package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.icon.SvgIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 侧边栏导航项：图标 + 文字的一行。
 *
 * <p>选中态是一块液态玻璃，再叠一层主题色；左侧保留一小枚强调色竖条作为定位锚点。
 * 未选中时完全退成排版，只有悬停 / 按下给一层很淡的亮度。</p>
 */
public class GlassNavButton extends AbstractButton {

    private static final int ICON_SIZE = 12;
    private static final int ICON_LEFT = 9;
    private static final int TEXT_LEFT = 27;
    private static final float TEXT_SCALE = 0.75F;

    private final SvgIcon icon;
    private final Runnable action;
    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private final GlassWidgets.Spring press = new GlassWidgets.Spring();
    private boolean selected;
    private boolean held;

    public GlassNavButton(int x, int y, int width, int height, SvgIcon icon, Component label,
                          Runnable action) {
        super(x, y, width, height, label);
        this.icon = icon;
        this.action = action;
    }

    public void setSelected(boolean value) {
        this.selected = value;
    }

    public boolean isSelected() {
        return selected;
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
        hover.tick((isHovered() || isFocused()) && usable, GlassTheme.Motion.HOVER);
        press.tickSpring(held && usable, held ? GlassTheme.Motion.PRESS : GlassTheme.Motion.RELEASE);
        float lift = hover.eased();
        float down = GlassWidgets.clamp01(press.value());

        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;
        float radius = Math.min(FlatTheme.RADIUS_ROW, height * 0.5F);

        if (selected) {
            float selectedLift = 0.55F + 0.45F * lift;
            if (!GlassWidgets.glassBackground(graphics, x0, y0, x1, y1,
                    GlassTheme.ROW, selectedLift, down)) {
                FlatTheme.fill(graphics, x0, y0, x1, y1, radius, FlatTheme.SURFACE_STRONG);
            }
            int accentRgb = FlatTheme.accentRgb();
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1,
                    accentRgb, accentRgb, radius, 0.12F + 0.05F * lift, 1.0F);
        } else if (lift > 0.01F || down > 0.01F) {
            FlatTheme.fill(graphics, x0, y0, x1, y1, radius,
                    FlatTheme.alpha(0xFFFFFF, 0.07F * lift + 0.05F * down));
        }

        if (selected) {
            int barHeight = Math.min(14, height - 10);
            int barTop = y0 + (height - barHeight) / 2;
            FlatTheme.fill(graphics, x0 + 5, barTop, x0 + 7, barTop + barHeight,
                    1.0F, FlatTheme.accent());
        }

        int iconColor = selected
                ? FlatTheme.accent()
                : (usable ? FlatTheme.TEXT_SECONDARY : FlatTheme.TEXT_FAINT);
        icon.drawCentered(graphics, x0 + ICON_LEFT, y0 + (height - ICON_SIZE) / 2,
                ICON_SIZE, ICON_SIZE, iconColor);

        Font font = Minecraft.getInstance().font;
        int textColor = selected
                ? FlatTheme.TEXT
                : (usable ? FlatTheme.TEXT_SECONDARY : FlatTheme.TEXT_FAINT);
        int textY = y0 + Math.round((height - 8.0F * TEXT_SCALE) * 0.5F);
        GlassWidgets.text(graphics, font, getMessage(), x0 + TEXT_LEFT, textY, textColor, TEXT_SCALE);
    }
}
