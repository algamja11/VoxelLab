package com.mamiyaotaru.voxelmap.util;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

public class VoxelMapRenderer {
    private static final Tesselator TESSELLATOR = new Tesselator(4096);
    private static final ArrayList<DrawBatch> DRAW_BATCHES = new ArrayList<>();
    private static final GPUBufferPool VERTEX_BUFFER_POOL = new GPUBufferPool(() ->  "VoxelMap Cached Vertex Buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST);
    private static final GPUBufferPool INDEX_BUFFER_POOL = new GPUBufferPool(() ->  "VoxelMap Cached Inex Buffer", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST);

    private static boolean batching = false;
    private static DrawBatch drawBatch;
    private static BufferBuilder bufferBuilder;

    private static GpuTexture immediateColorTexture;
    private static GpuTexture immediateDepthTexture;
    private static GpuTextureView immediateColorTextureView;
    private static GpuTextureView immediateDepthTextureView;

    public static VertexConsumer addVertex(Matrix3x2fStack matrixStack, float x, float y, float z) {
        Vector3f v3f = new Vector3f();
        matrixStack.transform(x, y, 1, v3f);
        return addVertex(v3f.x, v3f.y, z);
    }

    public static VertexConsumer addVertex(float x, float y, float z) {
        return bufferBuilder.addVertex(x, y, z);
    }

    public static void bindTexture(Identifier texture) {
        bindTexture(VoxelConstants.getMinecraft().getTextureManager().getTexture(texture));
    }

    public static void bindTexture(AbstractTexture texture) {
        bindTexture(texture.getTextureView(), texture.getSampler());
    }

    public static void bindTexture(GpuTextureView textureView, GpuSampler sampler) {
        bindTexture(TextureSetup.singleTexture(textureView, sampler));
    }

    public static void bindTexture(TextureSetup textureSetup) {
        drawBatch.setTexture(textureSetup);
    }

    public static void beginBatch(VertexFormat.Mode mode, RenderPipeline pipeline) {
        RenderSystem.assertOnRenderThread();

        if (batching) {
            throw new IllegalStateException("Batch already active! Call endBatch() before beginBatch().");
        }
        batching = true;

        drawBatch = new DrawBatch(pipeline);
        bufferBuilder = TESSELLATOR.begin(mode, pipeline.getVertexFormat());
    }

    public static void endBatch() {
        RenderSystem.assertOnRenderThread();

        if (!batching) {
            throw new IllegalStateException("No active batch! Call beginBatch() before submitting geometry.");
        }

        try (MeshData meshData = bufferBuilder.build()) {
            if (meshData == null) {
                return;
            }

            GpuBuffer vertexBuffer = VERTEX_BUFFER_POOL.upload(meshData.vertexBuffer());
            GpuBuffer indexBuffer;
            VertexFormat.IndexType indexType;
            if (meshData.indexBuffer() != null) {
                indexBuffer = INDEX_BUFFER_POOL.upload(meshData.indexBuffer());
                indexType = meshData.drawState().indexType();
            } else {
                RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(meshData.drawState().mode());
                indexBuffer = autoStorageIndexBuffer.getBuffer(meshData.drawState().indexCount());
                indexType = autoStorageIndexBuffer.type();
            }

            drawBatch.setRenderData(meshData, vertexBuffer, indexBuffer, indexType);
            DRAW_BATCHES.add(drawBatch);
        } finally {
            batching = false;
        }
    }

    public static GpuTextureView flushImmediate(Supplier<String> name, int width, int height) {
        RenderSystem.assertOnRenderThread();

        if (immediateColorTexture == null || immediateColorTexture.getWidth(0) != width || immediateColorTexture.getHeight(0) != height) {
            if (immediateColorTexture != null) {
                immediateColorTexture.close();
                immediateDepthTexture.close();
            }

            immediateColorTexture = RenderSystem.getDevice().createTexture("voxelmap-immediate-color-fbo", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.RGBA8, width, height, 1, 1);
            immediateDepthTexture = RenderSystem.getDevice().createTexture("voxelmap-immediate-depth-fbo", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.DEPTH32, width, height, 1, 1);
            immediateColorTextureView = RenderSystem.getDevice().createTextureView(immediateColorTexture);
            immediateDepthTextureView = RenderSystem.getDevice().createTextureView(immediateDepthTexture);
        }

        flush(name, immediateColorTextureView, immediateDepthTextureView);

        return immediateColorTextureView;
    }

    public static void flush(Supplier<String> name, GpuTextureView colorTexture, GpuTextureView depthTexture) {
        RenderSystem.assertOnRenderThread();

        if (batching) {
            throw new IllegalStateException("Cannot flush while a batch is active. Call endBatch() first.");
        }

        if (DRAW_BATCHES.isEmpty()) {
            return;
        }

        // 1: model view matrix, 2: color modulator, 3: model offset, 4: texture matrix
        GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
                .writeTransform(
                        RenderSystem.getModelViewMatrix(),
                        new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                        new Vector3f(),
                        new Matrix4f());

        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(name, colorTexture, OptionalInt.of(0x00000000), depthTexture, OptionalDouble.of(1.0))) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", gpuBufferSlice);

            for (DrawBatch drawBatch : DRAW_BATCHES) {
                if (!drawBatch.isReady()) {
                    continue;
                }

                renderPass.setVertexBuffer(0, drawBatch.getVertexBuffer());
                renderPass.setIndexBuffer(drawBatch.getIndexBuffer(), drawBatch.getIndexType());

                renderPass.setPipeline(drawBatch.getPipeline());
                TextureSetup textureSetup = drawBatch.getTextureSetup();
                if (textureSetup != null) {
                    renderPass.bindTexture("Sampler0", textureSetup.texure0(), textureSetup.sampler0());
                    renderPass.bindTexture("Sampler1", textureSetup.texure1(), textureSetup.sampler1());
                    renderPass.bindTexture("Sampler2", textureSetup.texure2(), textureSetup.sampler2());
                }
                renderPass.drawIndexed(0, 0, drawBatch.getMeshData().drawState().indexCount(), 1);
            }
        }

        DRAW_BATCHES.clear();
        VERTEX_BUFFER_POOL.rewind();
        INDEX_BUFFER_POOL.rewind();
    }

    private static class DrawBatch {
        private final RenderPipeline pipeline;
        private TextureSetup textureSetup;
        private MeshData meshData;
        private GpuBuffer vertexBuffer;
        private GpuBuffer indexBuffer;
        private VertexFormat.IndexType indexType;

        public DrawBatch(RenderPipeline pipeline) {
            this.pipeline = pipeline;
        }

        public void setTexture(TextureSetup textureSetup) {
            this.textureSetup = textureSetup;
        }

        public void setRenderData(MeshData meshData, GpuBuffer vertexBuffer, GpuBuffer indexBuffer, VertexFormat.IndexType indexType) {
            this.meshData = meshData;
            this.vertexBuffer = vertexBuffer;
            this.indexBuffer = indexBuffer;
            this.indexType = indexType;
        }

        public boolean isReady() {
            return pipeline != null && meshData != null && vertexBuffer != null && indexBuffer != null && indexType != null;
        }

        public RenderPipeline getPipeline() {
            return pipeline;
        }

        public TextureSetup getTextureSetup() {
            return textureSetup;
        }

        public MeshData getMeshData() {
            return meshData;
        }

        public GpuBuffer getVertexBuffer() {
            return vertexBuffer;
        }

        public GpuBuffer getIndexBuffer() {
            return indexBuffer;
        }

        public VertexFormat.IndexType getIndexType() {
            return indexType;
        }
    }

    private static class GPUBufferPool {
        private final Supplier<String> name;
        private final int usage;
        private final ArrayList<GpuBuffer> buffers = new ArrayList<>();

        private int index = 0;

        public GPUBufferPool(Supplier<String> name, int usage) {
            this.name = name;
            this.usage = usage;
        }

        public GpuBuffer upload(ByteBuffer byteBuffer) {
            int remaining = byteBuffer.remaining();

            if (buffers.size() <= index) {
                int initialBufferSize = Mth.smallestEncompassingPowerOfTwo(remaining);
                buffers.add(RenderSystem.getDevice().createBuffer(name, usage, initialBufferSize));

                VoxelConstants.getLogger().info("New buffer allocated in '{}' (Total: {}). Size: {} Bytes", name.get(), buffers.size(), initialBufferSize);
            }
            GpuBuffer buffer = buffers.get(index);

            if (buffer.size() < remaining) {
                int newBufferSize = Mth.smallestEncompassingPowerOfTwo(remaining);
                buffer.close();
                buffer = RenderSystem.getDevice().createBuffer(name, usage, newBufferSize);
                buffers.set(index, buffer);

                VoxelConstants.getLogger().info("Buffer #{} in '{}' resized. New Size: {} Bytes", index + 1, name.get(), newBufferSize);
            } else {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), byteBuffer);
            }

            index++;
            return buffer;
        }

        public void rewind() {
            index = 0;
        }

        public void dispose() {
            rewind();
            for (GpuBuffer buffer : buffers) {
                buffer.close();
            }
            buffers.clear();
        }
    }
}
