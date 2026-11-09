package com.junhsiun.musicplayer.client.gui.media;

import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** 一张会自己下载、自己画占位块的方形封面。 */
public class MediaCover extends AbstractWidget {

    private String coverUrl = "";

    public MediaCover(int x, int y, int size) {
        super(x, y, size, size, Component.empty());
        this.active = false;
    }

    public void setCoverUrl(String value) {
        this.coverUrl = value == null ? "" : value;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
        if (width <= 0 || height <= 0) {
            return;
        }
        MediaThumbnails.Thumb thumb = coverUrl.isBlank()
                ? null : MediaThumbnails.getInstance().get(coverUrl);
        if (thumb != null) {
            MediaThumbnails.draw(graphics, thumb, getX(), getY(), Math.min(width, height));
            return;
        }
        float radius = Math.min(width, height) * 0.22F;
        GlassWidgets.roundedFill(graphics, getX(), getY(), getX() + width, getY() + height,
                0xFFFFFF, 0xFFFFFF, radius, 0.14F, 1.0F);
        int inset = Math.max(2, Math.min(width, height) / 4);
        GlassWidgets.roundedFill(graphics, getX() + inset, getY() + inset,
                getX() + width - inset, getY() + height - inset,
                0xFFFFFF, 0xFFFFFF, Math.max(1.0F, radius - 1.0F), 0.10F, 1.0F);
    }
}
