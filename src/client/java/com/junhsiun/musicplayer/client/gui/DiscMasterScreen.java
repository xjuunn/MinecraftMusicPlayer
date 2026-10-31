package com.junhsiun.musicplayer.client.gui;

import com.junhsiun.musicplayer.client.gui.glass.GlassDebug;
import com.junhsiun.musicplayer.client.gui.glass.GlassStyle;
import com.junhsiun.musicplayer.client.gui.window.GlassWindow;
import com.junhsiun.musicplayer.client.gui.window.GlassWindowScreen;
import com.junhsiun.musicplayer.client.gui.window.WindowAnchor;
import com.junhsiun.musicplayer.client.gui.window.WindowAnimations;
import com.junhsiun.musicplayer.client.gui.window.WindowBounds;
import com.junhsiun.musicplayer.client.gui.window.WindowSize;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 手持唱片母版右键打开的界面。
 *
 * <p>窗口本身由 {@link GlassWindowScreen} 托管，这里只声明窗口配置和内容控件。
 * 目前内容是四个实时调参滑条加一个重置按钮，用来打磨玻璃材质；后续的搜索、刻录
 * 功能直接往 {@link #buildContent(WindowBounds)} 里加即可。</p>
 *
 * <p>窗口不暂停游戏，背后的世界继续渲染，玻璃采样的就是实时画面。</p>
 */
public final class DiscMasterScreen extends GlassWindowScreen {

    private static final int SLIDER_WIDTH = 320;
    private static final int SLIDER_HEIGHT = 20;
    private static final int SLIDER_GAP = 6;

    public DiscMasterScreen() {
        super(new GlassWindow()
                .style(GlassStyle.WINDOW)
                .size(WindowSize.preferred(880, 560, 32))
                .anchor(WindowAnchor.CENTER)
                .contentPadding(28, 28)
                .opening(WindowAnimations.riseFromBottom()));
    }

    @Override
    protected void buildContent(WindowBounds content) {
        Tuning[] tunings = Tuning.values();
        int sliderX = content.x0() + Math.max(0, (content.width() - SLIDER_WIDTH) / 2);
        int blockHeight = tunings.length * (SLIDER_HEIGHT + SLIDER_GAP) + SLIDER_HEIGHT + SLIDER_GAP;
        int y = content.y0() + Math.max(0, (content.height() - blockHeight) / 2);

        for (Tuning tuning : tunings) {
            addRenderableWidget(new TuningSlider(sliderX, y, SLIDER_WIDTH, SLIDER_HEIGHT, tuning));
            y += SLIDER_HEIGHT + SLIDER_GAP;
        }

        y += SLIDER_GAP;
        addRenderableWidget(Button.builder(Component.literal("重置参数并重播动画"), button -> {
            GlassDebug.reset();
            // 重建滑条（清掉覆盖值后重新读材质默认值），再从头播一次开场动画；
            // init 本身不再重播动画，否则窗口缩放时也会弹一下
            rebuildWidgets();
            replayOpening();
        }).bounds(sliderX, y, SLIDER_WIDTH, SLIDER_HEIGHT).build());
    }

    /** 可调的四个光学参数，取值范围与 {@link GlassDebug} 的编码上限一致。 */
    private enum Tuning {
        REFRACTION("折射强度", GlassDebug.MAX_REFRACTION),
        DISPERSION("边缘色散", 1.0F),
        CENTER_DISPERSION("中心色散", GlassDebug.MAX_CENTER_DISPERSION),
        BEVEL("斜面宽度", GlassDebug.MAX_BEVEL);

        private final String label;
        private final float max;

        Tuning(String label, float max) {
            this.label = label;
            this.max = max;
        }

        float read() {
            return switch (this) {
                case REFRACTION -> GlassDebug.refraction(GlassStyle.WINDOW);
                case DISPERSION -> GlassDebug.dispersion(GlassStyle.WINDOW);
                case CENTER_DISPERSION -> GlassDebug.centerDispersion(GlassStyle.WINDOW);
                case BEVEL -> GlassDebug.bevel(GlassStyle.WINDOW);
            };
        }

        void write(float value) {
            switch (this) {
                case REFRACTION -> GlassDebug.refraction(value);
                case DISPERSION -> GlassDebug.dispersion(value);
                case CENTER_DISPERSION -> GlassDebug.centerDispersion(value);
                case BEVEL -> GlassDebug.bevel(value);
            }
        }
    }

    private static final class TuningSlider extends AbstractSliderButton {

        private final Tuning tuning;

        TuningSlider(int x, int y, int width, int height, Tuning tuning) {
            super(x, y, width, height, Component.empty(), tuning.read() / tuning.max);
            this.tuning = tuning;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format("%s: %.2f", tuning.label, current())));
        }

        @Override
        protected void applyValue() {
            tuning.write(current());
        }

        private float current() {
            return (float) (this.value * tuning.max);
        }
    }
}
