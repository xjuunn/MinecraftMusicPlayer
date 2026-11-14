package com.junhsiun.musicplayer.client.gui.glass;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

/**
 * 一个静态全屏三角形对（POSITION_TEX），供玻璃背景模糊与纯享特效共用。
 *
 * <p>顶点直接是 NDC 坐标，着色器不做投影矩阵变换。</p>
 */
public final class GlassBlurQuads {

    private static volatile VertexBuffer buffer;
    private static boolean closed;

    private GlassBlurQuads() {
    }

    /** 懒创建的共享全屏 quad；退世界时 {@link #reset()} 释放，下次绘制重建。 */
    public static VertexBuffer get() {
        if (buffer == null) {
            synchronized (GlassBlurQuads.class) {
                if (buffer == null && !closed) {
                    buffer = create();
                }
            }
        }
        return closed ? null : buffer;
    }

    private static VertexBuffer create() {
        RenderSystem.assertOnRenderThreadOrInit();
        BufferBuilder builder = Tesselator.getInstance().begin(Mode.TRIANGLES,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        // 两个三角形覆盖整个屏幕；位置是 NDC，uv 全幅
        builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
        builder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
        builder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
        builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
        builder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
        builder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);
        MeshData mesh = builder.build();
        if (mesh == null) {
            return null;
        }
        VertexBuffer result = new VertexBuffer(VertexBuffer.Usage.STATIC);
        result.bind();
        result.upload(mesh);
        VertexBuffer.unbind();
        return result;
    }

    /** 释放（退世界 / 分辨率体系变化时调用）；下次 get() 重建。 */
    public static void reset() {
        synchronized (GlassBlurQuads.class) {
            closed = false;
            if (buffer != null) {
                buffer.close();
                buffer = null;
            }
        }
    }
}
