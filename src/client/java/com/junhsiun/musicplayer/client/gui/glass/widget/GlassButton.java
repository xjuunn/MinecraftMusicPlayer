package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** macOS 液态玻璃按钮。 */
public class GlassButton extends AbstractButton {

    public enum Kind {
        /** 普通玻璃片 */
        PLAIN,
        /** 系统蓝强调 */
        PROMINENT
    }

    private final Kind kind;
    private final Runnable action;
    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private final GlassWidgets.Spring press = new GlassWidgets.Spring();
    private boolean held;

    public GlassButton(int x, int y, int width, int height, Component message, Runnable action, Kind kind) {
        super(x, y, width, height, message);
        this.kind = kind;
        this.action = action;
    }

    public static GlassButton regular(int x, int y, int width, Component message, Runnable action) {
        return new GlassButton(x, y, width, GlassTheme.Sizes.CONTROL, message, action, Kind.PLAIN);
    }

    public static GlassButton large(int x, int y, int width, Component message, Runnable action, Kind kind) {
        return new GlassButton(x, y, width, GlassTheme.Sizes.CONTROL_LARGE, message, action, kind);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (action != null) {
            action.run();
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
        hover.tick(isHoveredOrFocused() && usable, GlassTheme.Motion.HOVER);
        // 按下快、松开带回弹
        press.tickSpring(held && usable, held ? GlassTheme.Motion.PRESS : GlassTheme.Motion.RELEASE);

        float lift = hover.eased();
        float down = GlassWidgets.clamp01(press.value());

        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;
        boolean prominent = kind == Kind.PROMINENT;

        if (!GlassWidgets.glassBackground(graphics, x0, y0, x1, y1, GlassTheme.BUTTON, lift, down)) {
            // 退化：半透明白片
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, 0xFFFFFF, 0x000000,
                    GlassTheme.Sizes.RADIUS, 0.55F, 1.0F - 0.03F * down);
        }
        // 强调款在玻璃上再叠系统蓝
        if (prominent) {
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, GlassTheme.ACCENT, GlassTheme.ACCENT,
                    GlassTheme.Sizes.RADIUS, 0.92F, 1.0F - 0.03F * down);
        }

        // 文字永远是白色，不随背景变化
        int textColor = GlassTheme.TEXT;
        if (!usable) {
            textColor = GlassWidgets.dim(textColor, 0.45F);
        }
        int textY = y0 + Math.round((height - 8 * GlassTheme.Text.CONTROL) / 2.0F);
        GlassWidgets.centeredText(graphics, Minecraft.getInstance().font, getMessage(),
                x0 + width / 2, textY,
                GlassWidgets.dim(textColor, 0.10F * down), GlassTheme.Text.CONTROL);
    }
}
