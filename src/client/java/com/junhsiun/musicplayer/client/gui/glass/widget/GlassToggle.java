package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** iOS 风格液态玻璃开关。 */
public class GlassToggle extends AbstractButton {

    private final Runnable onChanged;
    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private final GlassWidgets.Spring press = new GlassWidgets.Spring();
    private final GlassWidgets.Spring slide = new GlassWidgets.Spring();
    private final GlassWidgets.Spring tint = new GlassWidgets.Spring();
    private boolean on;
    private boolean held;

    public GlassToggle(int x, int y, boolean initial, Runnable onChanged) {
        super(x, y, GlassTheme.Sizes.TOGGLE_WIDTH, GlassTheme.Sizes.TOGGLE_HEIGHT, Component.empty());
        this.on = initial;
        this.onChanged = onChanged;
        // 初值直接就位，不播开启动画
        slide.snap(initial ? 1.0F : 0.0F);
        tint.snap(initial ? 1.0F : 0.0F);
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(boolean value) {
        on = value;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        on = !on;
        if (onChanged != null) {
            onChanged.run();
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

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics) {
        boolean usable = isActive();
        hover.tick((isHovered() || isFocused()) && usable, GlassTheme.Motion.HOVER);
        press.tickSpring(held && usable, held ? GlassTheme.Motion.PRESS : GlassTheme.Motion.RELEASE);
        // 圆点带回弹滑过去，染色用平滑曲线
        slide.tickSpring(on, GlassTheme.Motion.TOGGLE);
        tint.tick(on, GlassTheme.Motion.TOGGLE);

        float lift = hover.eased();
        float down = GlassWidgets.clamp01(press.value());
        float position = GlassWidgets.clamp01(slide.value());

        int x0 = getX();
        int y0 = getY();
        int w = GlassTheme.Sizes.TOGGLE_WIDTH;
        int h = GlassTheme.Sizes.TOGGLE_HEIGHT;
        int knob = GlassTheme.Sizes.TOGGLE_KNOB;
        int inset = GlassTheme.Sizes.TOGGLE_INSET;

        // 底槽是玻璃，开启时叠系统蓝
        if (!GlassWidgets.glassBackground(graphics, x0, y0, x0 + w, y0 + h,
                GlassTheme.TOGGLE, lift, down)) {
            GlassWidgets.roundedFill(graphics, x0, y0, x0 + w, y0 + h,
                    0xFFFFFF, 0x000000, h / 2.0F, 0.45F, 1.0F);
        }
        float blue = GlassWidgets.clamp01(tint.value());
        if (blue > 0.01F) {
            GlassWidgets.roundedFill(graphics, x0, y0, x0 + w, y0 + h,
                    GlassTheme.ACCENT, GlassTheme.ACCENT, h / 2.0F, 0.90F * blue, 1.0F);
        }

        // 圆点是玻璃珠，随开关滑动
        int travel = w - inset * 2 - knob;
        int knobX = x0 + inset + Math.round(travel * position);
        int knobY = y0 + (h - knob) / 2;

        // macOS 的手感：滑动时圆点沿运动方向被拉长，停下再回圆。
        // 用它自身的速度驱动，所以点击瞬间拉伸、到位后自动复原，不需要额外计时器。
        float speed = GlassWidgets.clamp01(Math.abs(slide.velocity()) * 0.05F);
        int stretch = Math.round(speed * 3.0F);
        int left = knobX - stretch;
        int right = knobX + knob + stretch;
        if (!GlassWidgets.glassBackground(graphics, left, knobY, right, knobY + knob,
                GlassTheme.KNOB, lift, down)) {
            GlassWidgets.roundedFill(graphics, left, knobY, right, knobY + knob,
                    0xFFFFFF, 0x808080, knob / 2.0F, 0.92F, 1.0F);
        }
    }
}
