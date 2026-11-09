package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 一块只有纯色的圆角面板，用作扁平界面里的背景容器。
 *
 * <p>和 {@link com.junhsiun.musicplayer.client.gui.glass.widget.GlassCard} 的区别是
 * <b>没有玻璃层</b>：整个窗口只有最外层是液态玻璃，内部卡片只需要一层亮度层次，
 * 再叠玻璃会把背景糊成一团。控件不接收点击，交互由面板上的子控件负责。</p>
 *
 * <p>子控件要画在它上面，注册顺序必须晚于本控件；这是一个"背景片"，
 * 不参与事件处理。</p>
 */
public class FlatPanel extends AbstractWidget {

    private int fill = FlatTheme.SURFACE;
    private int border;
    private float radius = 10.0F;

    private FlatPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.active = false;
    }

    public static FlatPanel of(int x, int y, int width, int height) {
        return new FlatPanel(x, y, width, height);
    }

    /** 面板底色（ARGB）。 */
    public FlatPanel fill(int argb) {
        this.fill = argb;
        return this;
    }

    /** 1 像素描边（ARGB）；{@code 0} 表示不画描边。 */
    public FlatPanel border(int argb) {
        this.border = argb;
        return this;
    }

    public FlatPanel radius(float value) {
        this.radius = Math.max(0.0F, value);
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + getWidth();
        int y1 = y0 + getHeight();
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        if (((border >>> 24) & 0xFF) != 0) {
            FlatTheme.fill(graphics, x0, y0, x1, y1, radius, border);
            int inset = 1;
            FlatTheme.fill(graphics, x0 + inset, y0 + inset, x1 - inset, y1 - inset,
                    Math.max(1.0F, radius - inset), fill);
            return;
        }
        FlatTheme.fill(graphics, x0, y0, x1, y1, radius, fill);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}