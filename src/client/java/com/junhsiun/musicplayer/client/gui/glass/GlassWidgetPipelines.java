package com.junhsiun.musicplayer.client.gui.glass;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 玻璃控件与玻璃圆珠的管线。 */
public final class GlassWidgetPipelines {

    private static final String NAMESPACE = MusicPlayerMod.MOD_ID;
    private static final Identifier VERTEX_SHADER =
            Identifier.fromNamespaceAndPath(NAMESPACE, "core/glass_widget");
    private static final Identifier FRAGMENT_SHADER =
            Identifier.fromNamespaceAndPath(NAMESPACE, "core/glass_widget");

    private static final Map<String, RenderPipeline> CACHE = new ConcurrentHashMap<>();

    private GlassWidgetPipelines() {
    }

    /**
     * 圆角填充管线。
     *
     * @param fill        底色 RGB
     * @param border      描边 RGB
     * @param borderAlpha 描边不透明度
     * @param gloss       顶部柔光强度
     */
    public static RenderPipeline roundedFill(int fill, int border, float radius,
                                             float borderAlpha, float gloss) {
        String key = Integer.toHexString(fill) + "/" + Integer.toHexString(border) + "/" + radius
                + "/" + borderAlpha + "/" + gloss;
        return CACHE.computeIfAbsent(key, ignored -> RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE,
                        "pipeline/glass_widget_" + Integer.toHexString(key.hashCode())))
                .withVertexShader(VERTEX_SHADER)
                .withFragmentShader(FRAGMENT_SHADER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(Optional.<DepthStencilState>empty())
                .withCull(false)
                .withShaderDefine("GLASS_WIDGET_FILL_R", ((fill >> 16) & 0xFF) / 255.0F)
                .withShaderDefine("GLASS_WIDGET_FILL_G", ((fill >> 8) & 0xFF) / 255.0F)
                .withShaderDefine("GLASS_WIDGET_FILL_B", (fill & 0xFF) / 255.0F)
                .withShaderDefine("GLASS_WIDGET_BORDER_R", ((border >> 16) & 0xFF) / 255.0F)
                .withShaderDefine("GLASS_WIDGET_BORDER_G", ((border >> 8) & 0xFF) / 255.0F)
                .withShaderDefine("GLASS_WIDGET_BORDER_B", (border & 0xFF) / 255.0F)
                .withShaderDefine("GLASS_WIDGET_RADIUS", radius)
                .withShaderDefine("GLASS_WIDGET_BORDER_A", borderAlpha)
                .withShaderDefine("GLASS_WIDGET_GLOSS", gloss)
                .build());
    }

    /** 界面提取阶段预编译，避免绘制期首次编译失败带崩游戏。 */
    public static boolean prepare(RenderPipeline pipeline) {
        try {
            RenderSystem.getDevice().precompilePipeline(pipeline);
            return true;
        } catch (Throwable throwable) {
            MusicPlayerMod.LOGGER.warn("液态玻璃控件着色器编译失败，控件退化为矩形填充", throwable);
            return false;
        }
    }
}
