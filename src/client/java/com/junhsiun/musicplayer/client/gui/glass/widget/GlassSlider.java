package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** 扁平滑块：细滑轨 + 强调色已填充段 + 白色圆点。 */
public class GlassSlider extends AbstractSliderButton {

    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private final ValueText valueText;
    private final Consumer<Double> onValueChanged;
    private Runnable onCommit;
    private boolean alwaysShowValue;
    private boolean held;

    public GlassSlider(int x, int y, int width, int height, double initialValue,
                       Consumer<Double> onValueChanged, ValueText valueText) {
        super(x, y, width, height, Component.empty(), initialValue);
        this.valueText = valueText;
        this.onValueChanged = onValueChanged;
        updateMessage();
    }

    public static GlassSlider regular(int x, int y, int width, double initialValue,
                                      Consumer<Double> onValueChanged, ValueText valueText) {
        return new GlassSlider(x, y, width, GlassTheme.Sizes.CONTROL, initialValue, onValueChanged, valueText);
    }

    /** 值文本；返回 null 表示不显示。 */
    public interface ValueText {
        Component textAt(double value);
    }

    /** 拖动结束（松开鼠标）时回调，适合把连续变化的值合并成一次网络请求。 */
    public GlassSlider onCommit(Runnable action) {
        this.onCommit = action;
        return this;
    }

    /** 值文本是否始终显示；默认只在悬停/聚焦时显示。 */
    public GlassSlider alwaysShowValue() {
        this.alwaysShowValue = true;
        return this;
    }

    /**
     * 外部设置归一化后的值（0..1），不会触发 {@link #applyValue()}。
     *
     * <p>不要把它命名成 {@code setValue}：{@link AbstractSliderButton} 内部拖动、键盘调节用的
     * 正是这个签名，覆盖它会让拖动绕过 {@code applyValue()}，表现为滑块能动但设置值永远不变。</p>
     */
    public void setNormalizedValue(double value) {
        this.value = Math.max(0.0D, Math.min(1.0D, value));
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        if (valueText != null) {
            setMessage(valueText.textAt(value));
        }
    }

    @Override
    protected void applyValue() {
        if (onValueChanged != null) {
            onValueChanged.accept(value);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        super.onClick(event, doubled);
        held = true;
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        boolean wasHeld = held;
        super.onRelease(event);
        held = false;
        if (wasHeld && onCommit != null) {
            onCommit.run();
        }
    }

    /** 滑轨中心相对控件顶部的偏移，子类可覆写以留出标签行。 */
    protected int trackCenterY() {
        return height / 2;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean usable = isActive();
        hover.tick((isHovered() || isFocused()) && usable, GlassTheme.Motion.HOVER);
        float lift = hover.eased();

        int x0 = getX();
        int centerY = getY() + trackCenterY();
        int baseRadius = Math.max(4, GlassTheme.Sizes.SLIDER_KNOB / 2 - 1);
        // 圆点尺寸只跟悬停走：悬停弹簧是单调收敛的，放大/缩小一定平滑。
        // 按下不再参与半径（欠阻尼弹簧释放时会来回过冲，表现为圆点反复胀缩的"闪烁"）。
        int radius = Math.max(3, Math.round(baseRadius * (1.0F + 0.18F * lift)));
        int trackLeft = x0 + baseRadius;
        int trackRight = x0 + width - baseRadius;
        int trackTop = centerY - GlassTheme.Sizes.SLIDER_TRACK / 2;
        int trackBottom = trackTop + GlassTheme.Sizes.SLIDER_TRACK;
        float barRadius = GlassTheme.Sizes.SLIDER_TRACK / 2.0F;

        FlatTheme.fill(graphics, trackLeft, trackTop, trackRight, trackBottom, barRadius,
                FlatTheme.alpha(0xFFFFFF, usable ? 0.14F : 0.07F));

        int knobX = trackLeft + Math.round((trackRight - trackLeft) * (float) value);
        if (knobX > trackLeft + 1) {
            FlatTheme.fill(graphics, trackLeft, trackTop, knobX, trackBottom, barRadius,
                    FlatTheme.alpha(FlatTheme.accent(), usable ? 0.95F : 0.35F));
        }

        FlatTheme.fill(graphics, knobX - radius, centerY - radius, knobX + radius, centerY + radius,
                radius, FlatTheme.alpha(0xFFFFFF, usable ? 0.98F : 0.55F));

        if (valueText != null && (alwaysShowValue || isHovered() || isFocused())) {
            Component label = valueText.textAt(value);
            if (label != null) {
                GlassWidgets.rightText(graphics, font(), label, getX() + width,
                        centerY - Math.round(4 * GlassTheme.Text.VALUE),
                        FlatTheme.TEXT_SECONDARY, GlassTheme.Text.VALUE);
            }
        }
    }

    private static Font font() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }
}
