package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** 纯文本标签，永远是白色。 */
public class GlassLabel extends AbstractWidget {

    private final float scale;

    public GlassLabel(int x, int y, int width, int height, Component text, float scale) {
        super(x, y, width, height, text);
        this.scale = scale;
        this.active = false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
        GlassWidgets.text(graphics, Minecraft.getInstance().font, getMessage(),
                getX(), getY() + Math.round((height - 8 * scale) / 2.0F),
                GlassTheme.TEXT, scale);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
