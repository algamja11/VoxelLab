package com.mamiyaotaru.voxelmap.util;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.function.Supplier;

public class VoxelMapRenderer {
    private static final Tesselator TESSELLATOR = new Tesselator(4096);
    private static final ArrayList<DrawBatch> DRAW_BATCHES = new ArrayList<>();
    private static final GPUBufferPool VERTEX_BUFFER_POOL = new GPUBufferPool();
    private static final GPUBufferPool INDEX_BUFFER_POOL = new GPUBufferPool();

    private static boolean batching = false;
    private static DrawBatch drawBatch;
    private static BufferBuilder bufferBuilder;

    public static VertexConsumer getVertexConsumer() {
        ensureBatching();
        return bufferBuilder;
    }

    public static VertexConsumer addVertex(Matrix3x2fStack matrixStack, float x, float y, float z) {
        Vector3f v3f = new Vector3f();
        matrixStack.transform(x, y, 1, v3f);
        return addVertex(v3f.x, v3f.y, z);
    }

    public static VertexConsumer addVertex(float x, float y, float z) {
        return getVertexConsumer().addVertex(x, y, z);
    }

    public static void bindTexture(Identifier texture) {
        bindTexture(VoxelConstants.getMinecraft().getTextureManager().getTexture(texture));
    }

    public static void bindTexture(AbstractTexture texture) {
        bindTexture(texture.getTextureView(), texture.getSampler());
    }

    public static void bindTexture(GpuTextureView textureView, GpuSampler sampler) {
        ensureBatching();
        drawBatch.setTexture(textureView, sampler);
    }

    private static void ensureBatching() {
        if (!batching) {
            throw new IllegalStateException("No active batch! Call beginBatch() before submitting geometry.");
        }
    }

    public static void beginBatch(VertexFormat.Mode mode, RenderPipeline pipeline) {
        if (batching) {
            throw new IllegalStateException("Batch already active! Call endBatch() before beginBatch().");
        }
        batching = true;

        drawBatch = new DrawBatch(pipeline);
        bufferBuilder = TESSELLATOR.begin(mode, pipeline.getVertexFormat());
    }

    public static void endBatch() {
        ensureBatching();

        try (MeshData meshData = bufferBuilder.build()) {
            if (meshData == null) {
                return;
            }

            GpuBuffer vertexBuffer = VERTEX_BUFFER_POOL.upload(() -> "VoxelMap Cached Vertex Buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, meshData.vertexBuffer());
            GpuBuffer indexBuffer;
            VertexFormat.IndexType indexType;
            if (meshData.indexBuffer() != null) {
                indexBuffer = INDEX_BUFFER_POOL.upload(() -> "VoxelMap Cached Index Buffer", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, meshData.indexBuffer());
                indexType = meshData.drawState().indexType();
            } else {
                RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(meshData.drawState().mode());
                indexBuffer = autoStorageIndexBuffer.getBuffer(meshData.drawState().indexCount());
                indexType = autoStorageIndexBuffer.type();
            }

            drawBatch.setDataForRender(meshData, vertexBuffer, indexBuffer, indexType);
            DRAW_BATCHES.add(drawBatch);
        } finally {
            batching = false;
        }
    }

    public static void flush(Supplier<String> name, GpuTextureView textureView) {
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

        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(name, textureView, OptionalInt.of(0x00000000))) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", gpuBufferSlice);

            for (DrawBatch drawBatch : DRAW_BATCHES) {
                renderPass.setVertexBuffer(0, drawBatch.vertexBuffer);
                renderPass.setIndexBuffer(drawBatch.indexBuffer, drawBatch.indexType);

                renderPass.setPipeline(drawBatch.pipeline);
                renderPass.bindTexture("Sampler0", drawBatch.textureView, drawBatch.sampler);
                renderPass.drawIndexed(0, 0, drawBatch.meshData.drawState().indexCount(), 1);
            }
        }

        DRAW_BATCHES.clear();
        VERTEX_BUFFER_POOL.reset();
        INDEX_BUFFER_POOL.reset();
    }

    private static class DrawBatch {
        public final RenderPipeline pipeline;

        // texture data
        public GpuTextureView textureView;
        public GpuSampler sampler;

        // for rendering
        public MeshData meshData;
        public GpuBuffer vertexBuffer;
        public GpuBuffer indexBuffer;
        public VertexFormat.IndexType indexType;

        public DrawBatch(RenderPipeline pipeline) {
            this.pipeline = pipeline;
        }

        public void setTexture(GpuTextureView textureView, GpuSampler sampler) {
            this.textureView = textureView;
            this.sampler = sampler;
        }

        public void setDataForRender(MeshData meshData, GpuBuffer vertexBuffer, GpuBuffer indexBuffer, VertexFormat.IndexType indexType) {
            this.meshData = meshData;
            this.vertexBuffer = vertexBuffer;
            this.indexBuffer = indexBuffer;
            this.indexType = indexType;
        }
    }

    private static class GPUBufferPool {
        private final ArrayList<GpuBuffer> buffers = new ArrayList<>();
        private int index = 0;

        public GpuBuffer upload(Supplier<String> name, int usage, ByteBuffer byteBuffer) {
            if (buffers.size() <= index) {
                buffers.add(RenderSystem.getDevice().createBuffer(name, usage, byteBuffer));

                VoxelConstants.getLogger().info("New buffer '{}' allocated. Total Count: {}", name.get(), buffers.size());
            }
            GpuBuffer buffer = buffers.get(index);

            if (buffer.size() < byteBuffer.remaining()) {
                buffer.close();
                buffer = RenderSystem.getDevice().createBuffer(name, usage, byteBuffer);
                buffers.set(index, buffer);

                VoxelConstants.getLogger().info("Buffer '{}' resized. Index: {}, Size: {}", name.get(), index, byteBuffer.remaining());
            } else {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), byteBuffer);
            }

            index++;
            return buffer;
        }

        public void reset() {
            index = 0;
        }

        public void dispose() {
            reset();
            for (GpuBuffer buffer : buffers) {
                buffer.close();
            }
            buffers.clear();
        }
    }
}
