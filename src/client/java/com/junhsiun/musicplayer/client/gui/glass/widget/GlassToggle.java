package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** 扁平开关：浅色轨道 + 白色圆点，开启时轨道变成强调色。 */
public class GlassToggle extends AbstractButton {

    private final Consumer<Boolean> onChanged;
    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private final GlassWidgets.Spring press = new GlassWidgets.Spring();
    private final GlassWidgets.Spring slide = new GlassWidgets.Spring();
    private final GlassWidgets.Spring tint = new GlassWidgets.Spring();
    private boolean on;
    private boolean held;

    public GlassToggle(int x, int y, boolean initial, Consumer<Boolean> onChanged) {
        super(x, y, GlassTheme.Sizes.TOGGLE_WIDTH, GlassTheme.Sizes.TOGGLE_HEIGHT, Component.empty());
        this.on = initial;
        this.onChanged = onChanged;
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
            onChanged.accept(on);
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
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean usable = isActive();
        hover.tick(isHoveredOrFocused() && usable, GlassTheme.Motion.HOVER);
        press.tickSpring(held && usable, held ? GlassTheme.Motion.PRESS : GlassTheme.Motion.RELEASE);
        slide.tickSpring(on, GlassTheme.Motion.TOGGLE);
        tint.tick(on, GlassTheme.Motion.TOGGLE);

        float lift = hover.eased();
        float down = GlassWidgets.clamp01(press.value());
        float position = GlassWidgets.clamp01(slide.value());
        float blue = GlassWidgets.clamp01(tint.value());

        int x0 = getX();
        int y0 = getY();
        int w = GlassTheme.Sizes.TOGGLE_WIDTH;
        int h = GlassTheme.Sizes.TOGGLE_HEIGHT;
        int knob = GlassTheme.Sizes.TOGGLE_KNOB;
        int inset = GlassTheme.Sizes.TOGGLE_INSET;
        float radius = h / 2.0F;

        // 关闭：浅色轨道；开启：强调色轨道
        FlatTheme.fill(graphics, x0, y0, x0 + w, y0 + h, radius,
                FlatTheme.alpha(0xFFFFFF, (usable ? 0.14F : 0.08F) + 0.05F * lift));
        if (blue > 0.01F) {
            FlatTheme.fill(graphics, x0, y0, x0 + w, y0 + h, radius,
                    FlatTheme.alpha(FlatTheme.accent(), (usable ? 0.92F : 0.45F) * blue));
        }

        // 白色圆点，滑动时沿运动方向轻微拉长
        int travel = w - inset * 2 - knob;
        int knobX = x0 + inset + Math.round(travel * position);
        int knobY = y0 + (h - knob) / 2;
        float speed = GlassWidgets.clamp01(Math.abs(slide.velocity()) * 0.05F);
        int stretch = Math.round(speed * 3.0F);
        int left = knobX - stretch;
        int right = knobX + knob + stretch;
        FlatTheme.fill(graphics, left, knobY, right, knobY + knob, knob / 2.0F,
                FlatTheme.alpha(0xFFFFFF, usable ? 0.98F : 0.55F));
    }
}
