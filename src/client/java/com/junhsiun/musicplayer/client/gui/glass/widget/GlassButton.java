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
        // 与玻璃的 SDF 用同一套圆角规则：着色器取 min(材质半径, 半宽, 半高)。
        // 叠加层若用固定的小半径，胶囊按钮外面会多出一圈圆角矩形，看着像"包了一层"。
        float radius = Math.min(GlassTheme.BUTTON.radius(), Math.min(width * 0.5F, height * 0.5F));

        if (!GlassWidgets.glassBackground(graphics, x0, y0, x1, y1, GlassTheme.BUTTON, lift, down)) {
            // 退化：半透明白片
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, 0xFFFFFF, 0x000000,
                    radius, 0.55F, 1.0F - 0.03F * down);
        }
        // 强调款在玻璃上叠一层<b>半透明</b>系统蓝。
        //
        // 不透明度必须低：玻璃的折射、色散和边缘高光都在材质本身，一旦用接近实色的
        // 叠加盖上去，这些光学响应就全被压掉，按钮会变成一块实心蓝色圆角块，
        // 也就是"强调按钮不是液态玻璃"的观感来源。这里保留三成左右的染色，
        // 背景仍能透过来并被折射。
        if (prominent) {
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, GlassTheme.ACCENT, GlassTheme.ACCENT,
                    radius, 0.32F + 0.08F * lift, 1.0F - 0.03F * down);
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
