package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** 纯文本标签，默认白色。超出控件宽度时截断并补省略号。 */
public class GlassLabel extends AbstractWidget {

    private final float scale;
    private final int color;
    private boolean centered;

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

    /**
     * 改成在控件宽度内居中（默认左对齐）。
     *
     * <p>给"两侧是按钮、中间一行说明文字"的排版用（例如翻页栏的页码）。文字宽度随内容变化，
     * 靠调用方每次重算 x 会把控件坐标和动画变换搅在一起，交给控件自己居中更省事。</p>
     */
    public GlassLabel centered() {
        this.centered = true;
        return this;
    }

    /**
     * 超宽就截断。
     *
     * <p>标签本身不做裁剪，文字画到控件外面也照画——状态行右侧紧挨着按钮，
     * 长文案（例如带失败原因的提示）会直接压在按钮上。这里按像素宽度截断，
     * 保证标签永远不会越界。</p>
     */
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
        Font font = Minecraft.getInstance().font;
        Component text = getMessage();
        // 控件宽度是屏幕像素，文字按 scale 缩小后画，可用宽度要换算回字体像素
        int available = Math.round(width / scale);
        if (available > 0 && font.width(text) > available) {
            String ellipsis = "…";
            String plain = font.plainSubstrByWidth(text.getString(),
                    Math.max(0, available - font.width(ellipsis))) + ellipsis;
            text = Component.literal(plain);
        }
        GlassWidgets.text(graphics, font, text,
                centered ? getX() + Math.round((width - font.width(text) * scale) * 0.5F) : getX(),
                getY() + Math.round((height - 8 * scale) / 2.0F),
                color, scale);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
