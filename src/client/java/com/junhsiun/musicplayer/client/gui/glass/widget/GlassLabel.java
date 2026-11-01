package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** 纯文本标签，默认白色。 */
public class GlassLabel extends AbstractWidget {

    private final float scale;
    private final int color;

    public GlassLabel(int x, int y, int width, int height, Component text, float scale) {
        this(x, y, width, height, text, scale, GlassTheme.TEXT);
    }

    /** @param color 文字颜色；状态行这类次要信息用 {@link GlassTheme#TEXT_SECONDARY}。 */
    public GlassLabel(int x, int y, int width, int height, Component text, float scale, int color) {
        super(x, y, width, height, text);
        this.scale = scale;
        this.color = color;
        this.active = false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
        GlassWidgets.text(graphics, Minecraft.getInstance().font, getMessage(),
                getX(), getY() + Math.round((height - 8 * scale) / 2.0F),
                color, scale);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
