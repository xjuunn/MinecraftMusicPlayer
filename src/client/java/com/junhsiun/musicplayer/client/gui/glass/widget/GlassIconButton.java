package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import com.junhsiun.musicplayer.client.gui.icon.SvgIcon;
import net.minecraft.client.gui.GuiGraphics;
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
    /** 是否绘制液态玻璃底（默认关闭，保持原来的扁平幽灵/强调样式）。 */
    private boolean glass;

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

    /** 改成液态玻璃底：像 GlassButton 那样画一块折射玻璃，选中时再叠主题色。 */
    public GlassIconButton glass() {
        this.glass = true;
        return this;
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
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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

        if (glass) {
            // 液态玻璃底：和 GlassButton 同一套材质，圆角规则也一样（取 min(材质半径, 半宽, 半高)）。
            float glassRadius = Math.min(GlassTheme.BUTTON.radius(),
                    Math.min(width * 0.5F, height * 0.5F));
            if (!GlassWidgets.glassBackground(graphics, x0, y0, x1, y1,
                    GlassTheme.BUTTON, lift, down)) {
                GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, 0xFFFFFF, 0x000000,
                        glassRadius, 0.55F, 1.0F - 0.03F * down);
            }
            if (selected) {
                GlassWidgets.roundedFill(graphics, x0, y0, x1, y1,
                        FlatTheme.accentRgb(), FlatTheme.accentRgb(),
                        glassRadius, 0.30F + 0.08F * lift, 1.0F - 0.03F * down);
            }
        } else if (kind == Kind.ACCENT) {
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
        if (glass) {
            color = selected ? FlatTheme.TEXT
                    : (usable ? GlassTheme.TEXT : GlassWidgets.dim(GlassTheme.TEXT, 0.45F));
        } else if (kind == Kind.ACCENT) {
            color = usable ? FlatTheme.TEXT : FlatTheme.alpha(0xFFFFFF, 0.55F);
        } else if (selected) {
            color = usable ? FlatTheme.accent() : FlatTheme.alpha(FlatTheme.accent(), 0.55F);
        } else {
            color = usable ? FlatTheme.TEXT_SECONDARY : FlatTheme.TEXT_FAINT;
        }
        if (icon != null) {
            // 按墨迹在按钮方框里水平+垂直居中（drawCentered 会按图标墨迹的真实中心对齐，
            // 而不是按 viewBox 中心——SVG 每个图标四周留白不同，按 viewBox 会看着偏）。
            icon.drawCentered(graphics, x0, y0, Math.min(width, height), iconSize, color);
        }
    }
}
