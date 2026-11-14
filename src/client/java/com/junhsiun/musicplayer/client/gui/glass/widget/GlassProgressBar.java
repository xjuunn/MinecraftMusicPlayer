package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 只读的扁平进度条：一条极淡的轨道 + 强调色已播放段。
 *
 * <p>底部播放栏把它放在面板最上沿，它同时充当页面与播放栏之间的分隔线。</p>
 */
public class GlassProgressBar extends AbstractWidget {

    private double progress;

    public GlassProgressBar(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.active = false;
    }

    public void setProgress(double value) {
        this.progress = GlassWidgets.clamp01((float) value);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;
        float radius = height * 0.5F;

        FlatTheme.fill(graphics, x0, y0, x1, y1, radius, FlatTheme.alpha(0xFFFFFF, 0.10F));
        int filled = x0 + Math.round(width * (float) progress);
        if (filled > x0 + 1) {
            FlatTheme.fill(graphics, x0, y0, filled, y1, radius, FlatTheme.accent());
        }
    }
}
