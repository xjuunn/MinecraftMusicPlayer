package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.icon.SvgIcon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 只有图标的扁平按钮。
 *
 * <p>两种形态：{@link Kind#GHOST} 默认完全透明，只在悬停/按下时浮出一层极淡的圆形底；
 * {@link Kind#ACCENT} 是一枚实心强调色圆钮，用于底部播放栏的播放/暂停。</p>
 */
public class GlassIconButton extends AbstractButton {

    public enum Kind {
        /** 透明底，悬停时浮出圆形底。 */
        GHOST,
        /** 实心强调色圆钮。 */
        ACCENT
    }

    private final Kind kind;
    private final Runnable action;
    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private final GlassWidgets.Spring press = new GlassWidgets.Spring();
    private SvgIcon icon;
    private boolean selected;
    private boolean held;

    public GlassIconButton(int x, int y, int width, int height, SvgIcon icon,
                           Component narration, Runnable action) {
        this(x, y, width, height, icon, narration, action, Kind.GHOST);
    }

    private GlassIconButton(int x, int y, int width, int height, SvgIcon icon,
                            Component narration, Runnable action, Kind kind) {
        super(x, y, width, height, narration == null ? Component.empty() : narration);
        this.icon = icon;
        this.action = action;
        this.kind = kind;
    }

    public static GlassIconButton regular(int x, int y, int size, SvgIcon icon,
                                          Component narration, Runnable action) {
        return new GlassIconButton(x, y, size, size, icon, narration, action, Kind.GHOST);
    }

    public static GlassIconButton accent(int x, int y, int size, SvgIcon icon,
                                         Component narration, Runnable action) {
        return new GlassIconButton(x, y, size, size, icon, narration, action, Kind.ACCENT);
    }

    public void setIcon(SvgIcon value) {
        this.icon = value;
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
        float radius = Math.min(width, height) * 0.5F;

        if (kind == Kind.ACCENT) {
            FlatTheme.fill(graphics, x0, y0, x1, y1, radius,
                    FlatTheme.alpha(FlatTheme.accent(), usable ? 1.0F : 0.35F));
            if (lift > 0.01F) {
                FlatTheme.fill(graphics, x0, y0, x1, y1, radius,
                        FlatTheme.alpha(0xFFFFFF, 0.12F * lift));
            }
        } else if (selected) {
            FlatTheme.fill(graphics, x0, y0, x1, y1, radius,
                    FlatTheme.alpha(FlatTheme.accent(), 0.22F + 0.10F * lift));
        } else if (lift > 0.01F || down > 0.01F) {
            FlatTheme.fill(graphics, x0, y0, x1, y1, radius,
                    FlatTheme.alpha(0xFFFFFF, 0.08F * lift + 0.05F * down));
        }

        int iconSize = Math.max(6, Math.round(Math.min(width, height) * 0.56F));
        int color;
        if (kind == Kind.ACCENT) {
            color = usable ? FlatTheme.TEXT : FlatTheme.alpha(0xFFFFFF, 0.55F);
        } else if (selected) {
            color = usable ? FlatTheme.accent() : FlatTheme.alpha(FlatTheme.accent(), 0.55F);
        } else {
            color = usable ? FlatTheme.TEXT_SECONDARY : FlatTheme.TEXT_FAINT;
        }
        if (icon != null) {
            icon.drawCentered(graphics, x0, y0, Math.min(width, height), iconSize, color);
        }
    }
}
