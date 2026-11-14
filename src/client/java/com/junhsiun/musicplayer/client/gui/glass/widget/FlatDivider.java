package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** 一条 1 像素的扁平分隔线，横向或纵向。 */
public final class FlatDivider extends AbstractWidget {

    private FlatDivider(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.active = false;
    }

    public static FlatDivider vertical(int x, int y, int height) {
        return new FlatDivider(x, y, 1, height);
    }

    public static FlatDivider horizontal(int x, int y, int width) {
        return new FlatDivider(x, y, width, 1);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        FlatTheme.divider(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight());
    }
}
