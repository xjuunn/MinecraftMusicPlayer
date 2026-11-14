package com.junhsiun.musicplayer.client.gui.glass;

import net.minecraft.client.renderer.ShaderInstance;

/**
 * 玻璃控件与玻璃圆珠的着色器入口（1.21.1 老体系）。
 *
 * <p>26.x 按 fill/border/radius 组合编译 RenderPipeline；1.21.1 没有 shader define，
 * 这些参数全部改走 {@code glass_widget} 着色器的 uniforms，由
 * {@code GlassWidgets.roundedFill} 在每次绘制前写入。这里只负责暴露着色器实例
 * 与"是否可用"的判断。</p>
 */
public final class GlassWidgetPipelines {

    private GlassWidgetPipelines() {
    }

    /** 圆角填充着色器；资源重载后实例会被替换，必须每次经此获取。 */
    public static ShaderInstance roundedFill() {
        return GlassPipelines.glassWidget();
    }

    /** 界面打开阶段预校验，避免绘制期首次使用失败带崩游戏。 */
    public static boolean prepare(Object pipeline) {
        return GlassPipelines.shadersReady();
    }
}
