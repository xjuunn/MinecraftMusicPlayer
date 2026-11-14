package com.junhsiun.musicplayer.client.gui.glass;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

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
        // 1.20.1 的 BufferBuilder 链路：Tesselator 拿 builder，vertex().uv()，end() 产出可上传的数据
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        // 两个三角形覆盖整个屏幕；位置是 NDC，uv 全幅
        builder.vertex(-1.0F, -1.0F, 0.0F).uv(0.0F, 0.0F).endVertex();
        builder.vertex(1.0F, -1.0F, 0.0F).uv(1.0F, 0.0F).endVertex();
        builder.vertex(1.0F, 1.0F, 0.0F).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0F, -1.0F, 0.0F).uv(0.0F, 0.0F).endVertex();
        builder.vertex(1.0F, 1.0F, 0.0F).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0F, 1.0F, 0.0F).uv(0.0F, 1.0F).endVertex();
        VertexBuffer result = new VertexBuffer(VertexBuffer.Usage.STATIC);
        result.bind();
        result.upload(builder.end());
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
