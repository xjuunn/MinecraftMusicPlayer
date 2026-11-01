package com.junhsiun.musicplayer.client.gui;

import com.junhsiun.musicplayer.client.gui.glass.GlassDebug;
import com.junhsiun.musicplayer.client.gui.glass.GlassPanel;
import com.junhsiun.musicplayer.client.gui.glass.GlassStyle;
import com.junhsiun.musicplayer.client.gui.glass.layout.GlassLayout;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassButton;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassLabel;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassSlider;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassToggle;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import com.junhsiun.musicplayer.client.gui.window.GlassWindow;
import com.junhsiun.musicplayer.client.gui.window.GlassWindowScreen;
import com.junhsiun.musicplayer.client.gui.window.WindowAnchor;
import com.junhsiun.musicplayer.client.gui.window.WindowAnimations;
import com.junhsiun.musicplayer.client.gui.window.WindowBounds;
import com.junhsiun.musicplayer.client.gui.window.WindowSize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 手持唱片母版打开的界面。 */
public final class DiscMasterScreen extends GlassWindowScreen {

    private static final int WIDTH = 300;
    private static final int ROW_HEIGHT = 20;
    private static final int GAP = 6;
    private static final int SECTION_GAP = 10;
    private static final int PADDING = 16;
    /** 6 个光学滑块 + 透明度 + 模糊度 + 阴影开关行 + 按钮。 */
    private static final int HEIGHT = Tuning.values().length * (ROW_HEIGHT + GAP)
            + (ROW_HEIGHT + GAP) * 2
            + GlassTheme.Sizes.TOGGLE_HEIGHT + GAP
            + SECTION_GAP
            + GlassTheme.Sizes.CONTROL
            + PADDING * 2;

    public DiscMasterScreen() {
        super(new GlassWindow()
                .style(GlassTheme.windowStyle())
                .size(WindowSize.preferred(WIDTH + PADDING * 2, HEIGHT, 32))
                .anchor(WindowAnchor.CENTER)
                .contentPadding(PADDING, PADDING)
                .opening(WindowAnimations.riseFromBottom()));
    }

    @Override
    protected void buildContent(WindowBounds content) {
        window().style(GlassTheme.windowStyle());
        GlassPanel.prewarm(window().style());

        GlassLayout.Column column = new GlassLayout.Column().gap(GAP);
        for (Tuning tuning : Tuning.values()) {
            column.fill(ROW_HEIGHT, slot -> addRenderableWidget(new TuningSlider(slot, tuning)));
        }
        // 背景不透明度：0 = 叠加层完全消失，窗口和所有控件一起变
        column.fill(ROW_HEIGHT, slot -> addRenderableWidget(new OpacitySlider(slot)));
        // 背景模糊强度：0 = 完全没有模糊，只剩折射畸变
        column.fill(ROW_HEIGHT, slot -> addRenderableWidget(new BlurSlider(slot)));
        column.fill(GlassTheme.Sizes.TOGGLE_HEIGHT, slot -> toggleRow(slot, "窗口阴影",
                GlassTheme.shadowEnabled(), () -> GlassTheme.setShadowEnabled(!GlassTheme.shadowEnabled())));
        column.fill(GlassTheme.Sizes.CONTROL, slot -> addRenderableWidget(
                GlassButton.regular(slot.x(), slot.y(), slot.width(),
                        Component.literal("恢复默认并重播动画"), () -> {
                            // 9 项玻璃外观一起回到出厂值并立即落盘
                            GlassDebug.reset();
                            replayOpening();
                        })));
        column.layout(content);
    }

    /** 左侧标签 + 右侧开关的一行。 */
    private void toggleRow(GlassLayout.Slot slot, String label, boolean initial, Runnable onToggle) {
        addRenderableWidget(new GlassLabel(slot.x(), slot.y(), slot.width(), slot.height(),
                Component.literal(label), GlassTheme.Text.LABEL));
        addRenderableWidget(new GlassToggle(slot.x() + slot.width() - GlassTheme.Sizes.TOGGLE_WIDTH,
                slot.y(), initial, onToggle));
    }

    /**
     * 可调的光学参数。顺序即界面上的顺序：先边缘区、后中心区，
     * 方便一边拖一边对着边框看。
     *
     * <p>标签按「区域 + 光学量 + 词尾」构造，同类参数共用词尾：
     * 像素位移用「强度 / 宽度」，无量纲量用「倍率」。不出现内部变量名。</p>
     */
    private enum Tuning {
        // ── 边缘区：边框 → a ──────────────────────────────────────────
        A_DISTANCE("边缘折射宽度", GlassDebug.MAX_A_DISTANCE),
        CONTRA("边缘折射倍率", GlassDebug.MAX_CONTRA),
        DISPERSION("边缘色散强度", 1.0F),
        // ── 中心区：a → 中心 ──────────────────────────────────────────
        REFRACTION("中心折射强度", GlassDebug.MAX_REFRACTION),
        SHRINK("中心收缩强度", GlassDebug.MAX_SHRINK),
        CENTER_DISPERSION("中心色散强度", GlassDebug.MAX_CENTER_DISPERSION);

        private final String label;
        private final float max;

        Tuning(String label, float max) {
            this.label = label;
            this.max = max;
        }

        float read() {
            return switch (this) {
                case A_DISTANCE -> GlassDebug.aDistance(GlassStyle.WINDOW);
                case CONTRA -> GlassDebug.contraRatio(GlassStyle.WINDOW);
                case REFRACTION -> GlassDebug.refraction(GlassStyle.WINDOW);
                case SHRINK -> GlassDebug.shrink(GlassStyle.WINDOW);
                case DISPERSION -> GlassDebug.dispersion(GlassStyle.WINDOW);
                case CENTER_DISPERSION -> GlassDebug.centerDispersion(GlassStyle.WINDOW);
            };
        }

        void write(float value) {
            switch (this) {
                case A_DISTANCE -> GlassDebug.aDistance(value);
                case CONTRA -> GlassDebug.contraRatio(value);
                case REFRACTION -> GlassDebug.refraction(value);
                case SHRINK -> GlassDebug.shrink(value);
                case DISPERSION -> GlassDebug.dispersion(value);
                case CENTER_DISPERSION -> GlassDebug.centerDispersion(value);
            }
        }
    }

    /** 左侧标签 + 右侧数值的滑块行。 */
    private abstract static class LabeledSlider extends GlassSlider {

        private static final int LABEL_H = 9;

        LabeledSlider(int x, int y, int width, int height, double initial,
                      java.util.function.Consumer<Double> apply) {
            super(x, y, width, height, initial, apply, null);
        }

        abstract String label();

        abstract String valueText();

        @Override
        protected int trackCenterY() {
            return LABEL_H + (height - LABEL_H) / 2;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
            super.extractWidgetRenderState(graphics);
            var font = Minecraft.getInstance().font;
            GlassWidgets.text(graphics, font, Component.literal(label()),
                    getX(), getY(), GlassTheme.TEXT, GlassTheme.Text.LABEL);
            GlassWidgets.rightText(graphics, font, Component.literal(valueText()),
                    getX() + width, getY(), GlassTheme.TEXT_SECONDARY, GlassTheme.Text.VALUE);
        }
    }

    /** 单个光学参数的滑块。 */
    private static final class TuningSlider extends LabeledSlider {

        private final Tuning tuning;

        TuningSlider(GlassLayout.Slot slot, Tuning tuning) {
            super(slot.x(), slot.y(), slot.width(), slot.height(),
                    tuning.read() / tuning.max, value -> tuning.write((float) (value * tuning.max)));
            this.tuning = tuning;
        }

        @Override
        String label() {
            return tuning.label;
        }

        @Override
        String valueText() {
            return String.format(java.util.Locale.ROOT, "%.2f", (float) this.value * tuning.max);
        }
    }

    /** 背景不透明度滑块，0 = 叠加层完全消失。 */
    private static final class OpacitySlider extends LabeledSlider {

        OpacitySlider(GlassLayout.Slot slot) {
            super(slot.x(), slot.y(), slot.width(), slot.height(), GlassTheme.opacity(),
                    value -> GlassTheme.setOpacity((float) (double) value));
        }

        @Override
        String label() {
            return "背景不透明度";
        }

        @Override
        String valueText() {
            return String.format(java.util.Locale.ROOT, "%d%%", Math.round((float) this.value * 100));
        }
    }

    /** 背景模糊强度滑块，0 = 完全无模糊。 */
    private static final class BlurSlider extends LabeledSlider {

        BlurSlider(GlassLayout.Slot slot) {
            super(slot.x(), slot.y(), slot.width(), slot.height(), GlassTheme.blur(),
                    value -> GlassTheme.setBlur((float) (double) value));
        }

        @Override
        String label() {
            return "背景模糊强度";
        }

        @Override
        String valueText() {
            return String.format(java.util.Locale.ROOT, "%d%%", Math.round((float) this.value * 100));
        }
    }
}
