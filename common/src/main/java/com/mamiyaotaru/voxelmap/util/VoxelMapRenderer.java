package com.mamiyaotaru.voxelmap.util;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.function.Supplier;

public class VoxelMapRenderer {
    private static final Tesselator tessellator = new Tesselator(4096);
    private static final ArrayList<RenderBuffer> renderBuffers = new ArrayList<>();

    private static boolean batching = false;
    private static RenderBuffer renderBuffer;
    private static BufferBuilder bufferBuilder;

    private static final GPUBufferPool vertexBufferPool = new GPUBufferPool();
    private static final GPUBufferPool indexBufferPool = new GPUBufferPool();

    private static void ensureBatching() {
        if (!batching) {
            throw new IllegalStateException("No active batch! Call beginBatch() before submitting geometry.");
        }
    }

    public static void beginBatch(VertexFormat.Mode mode, RenderPipeline pipeline, AbstractTexture texture) {
        beginBatch(mode, pipeline, texture.getTextureView(), texture.getSampler());
    }

    public static void beginBatch(VertexFormat.Mode mode, RenderPipeline pipeline, GpuTextureView textureView, GpuSampler sampler) {
        if (batching) {
            throw new IllegalStateException("Batch already active! Call endBatch() before beginBatch().");
        }
        batching = true;

        renderBuffer = new RenderBuffer(pipeline, textureView, sampler);
        bufferBuilder = tessellator.begin(mode, pipeline.getVertexFormat());
    }

    public static BufferBuilder getBufferBuilder() {
        ensureBatching();
        return bufferBuilder;
    }

    public static VertexConsumer addVertex(float x, float y, float z) {
        return getBufferBuilder().addVertex(x, y, z);
    }

    public static void endBatch() {
        ensureBatching();

        try (MeshData meshData = bufferBuilder.build()) {
            if (meshData == null) {
                return;
            }

            GpuBuffer vertexBuffer = vertexBufferPool.upload(() -> "VoxelMap Cached Vertex Buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, meshData.vertexBuffer());
            GpuBuffer indexBuffer;
            VertexFormat.IndexType indexType;
            if (meshData.indexBuffer() != null) {
                indexBuffer = indexBufferPool.upload(() -> "VoxelMap Cached Index Buffer", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, meshData.indexBuffer());
                indexType = meshData.drawState().indexType();
            } else {
                RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(meshData.drawState().mode());
                indexBuffer = autoStorageIndexBuffer.getBuffer(meshData.drawState().indexCount());
                indexType = autoStorageIndexBuffer.type();
            }

            renderBuffer.setDataForRender(meshData, vertexBuffer, indexBuffer, indexType);
            renderBuffers.add(renderBuffer);
        } finally {
            batching = false;
        }
    }

    public static void flush(String passName, GpuTextureView textureView) {
        if (renderBuffers.isEmpty()) {
            return;
        }

        // 1: model view matrix, 2: color modulator, 3: model offset, 4: texture matrix
        GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
                .writeTransform(
                        RenderSystem.getModelViewMatrix(),
                        new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                        new Vector3f(),
                        new Matrix4f());

        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "VoxelMap: " + passName, textureView, OptionalInt.of(0x00000000))) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", gpuBufferSlice);

            for (RenderBuffer renderBuffer : renderBuffers) {
                renderPass.setVertexBuffer(0, renderBuffer.vertexBuffer);
                renderPass.setIndexBuffer(renderBuffer.indexBuffer, renderBuffer.indexType);

                renderPass.setPipeline(renderBuffer.pipeline);
                renderPass.bindTexture("Sampler0", renderBuffer.textureView, renderBuffer.sampler);
                renderPass.drawIndexed(0, 0, renderBuffer.meshData.drawState().indexCount(), 1);
            }
        }

        renderBuffers.clear();
        vertexBufferPool.reset();
        indexBufferPool.reset();
    }

    private static class RenderBuffer {
        public final RenderPipeline pipeline;
        public final GpuTextureView textureView;
        public final GpuSampler sampler;

        public MeshData meshData;
        public GpuBuffer vertexBuffer;
        public GpuBuffer indexBuffer;
        public VertexFormat.IndexType indexType;

        public RenderBuffer(RenderPipeline pipeline, GpuTextureView textureView, GpuSampler sampler) {
            this.pipeline = pipeline;
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

                System.out.println("Adding new buffer: " + index);
            }
            GpuBuffer buffer = buffers.get(index);

            CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
            if (buffer.size() < byteBuffer.remaining()) {
                buffer.close();
                buffer = RenderSystem.getDevice().createBuffer(name, usage, byteBuffer);
                buffers.set(index, buffer);

                System.out.println("Resizing buffer: " + index);
            } else {
                commandEncoder.writeToBuffer(buffer.slice(), byteBuffer);
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
