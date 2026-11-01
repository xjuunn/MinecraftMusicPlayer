package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** macOS 风格玻璃滑块：细滑轨 + 已填充段 + 液态玻璃圆珠。 */
public class GlassSlider extends AbstractSliderButton {

    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private final GlassWidgets.Spring grab = new GlassWidgets.Spring();
    private final ValueText valueText;
    private final Consumer<Double> onValueChanged;
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
        super.onRelease(event);
        held = false;
    }

    /** 滑轨中心相对控件顶部的偏移，子类可覆写以留出标签行。 */
    protected int trackCenterY() {
        return height / 2;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
        boolean usable = isActive();
        hover.tick(isHoveredOrFocused() && usable, GlassTheme.Motion.HOVER);
        grab.tickSpring(held && usable, held ? GlassTheme.Motion.PRESS : GlassTheme.Motion.RELEASE);
        float lift = hover.eased();
        float grip = GlassWidgets.clamp01(grab.value());

        int x0 = getX();
        int centerY = getY() + trackCenterY();
        int baseRadius = GlassTheme.Sizes.SLIDER_KNOB / 2;
        // 圆珠连续放大；材质圆角上限足够大，任何半径都是正圆，无需重编译
        int radius = Math.max(2, Math.round(baseRadius * (1.0F + 0.16F * grip + 0.08F * lift)));
        int trackLeft = x0 + baseRadius;
        int trackRight = x0 + width - baseRadius;
        int trackTop = centerY - GlassTheme.Sizes.SLIDER_TRACK / 2;
        int trackBottom = trackTop + GlassTheme.Sizes.SLIDER_TRACK;

        float barRadius = GlassTheme.Sizes.SLIDER_TRACK / 2.0F;
        // 浅灰底轨 + 强调蓝已填充段
        GlassWidgets.roundedFill(graphics, trackLeft, trackTop, trackRight, trackBottom,
                0x8E8E93, 0x8E8E93, barRadius, usable ? 0.35F : 0.18F, 1.0F);

        int knobX = trackLeft + Math.round((trackRight - trackLeft) * (float) value);
        if (knobX > trackLeft + 1) {
            GlassWidgets.roundedFill(graphics, trackLeft, trackTop, knobX, trackBottom,
                    GlassTheme.ACCENT, GlassTheme.ACCENT, barRadius, usable ? 1.0F : 0.35F, 1.0F);
        }

        // 圆珠是真正的液态玻璃
        if (!GlassWidgets.glassBackground(graphics, knobX - radius, centerY - radius,
                knobX + radius, centerY + radius, GlassTheme.KNOB, lift, grip)) {
            GlassWidgets.roundedFill(graphics, knobX - radius, centerY - radius,
                    knobX + radius, centerY + radius, 0xFFFFFF, 0x808080, radius, 0.92F, 1.0F);
        }

        if (valueText != null && isHoveredOrFocused()) {
            Component label = valueText.textAt(value);
            if (label != null) {
                GlassWidgets.rightText(graphics, font(), label, getX() + width,
                        centerY - Math.round(4 * GlassTheme.Text.VALUE),
                        GlassTheme.TEXT_SECONDARY, GlassTheme.Text.VALUE);
            }
        }
    }

    private static Font font() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }
}
