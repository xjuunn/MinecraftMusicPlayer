package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import com.junhsiun.musicplayer.client.gui.media.MediaThumbnails;
import com.junhsiun.musicplayer.model.LyricLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 歌曲详情页的居中视图：封面、歌名、歌手、专辑、时长，正在播放时再接一块歌词。
 *
 * <p>不挂任何子控件，全部自绘：底部歌词每帧都可能换行，用控件逐行同步反而更绕。</p>
 */
public final class SongDetailView extends AbstractWidget {

    private static final int COVER_SIZE = 84;
    private static final int CONTENT_MAX_WIDTH = 360;
    private static final int LYRIC_CONTEXT_LINES = 2;
    /** 静态信息块的大致高度：封面 + 标题 + 歌手 + 专辑 + 时长。用于垂直居中。 */
    private static final int INFO_BLOCK_HEIGHT = 148;

    private final SongDetailPage page;

    public SongDetailView(SongDetailPage page, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.page = page;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
        Font font = Minecraft.getInstance().font;
        int centerX = getX() + width / 2;
        int contentWidth = Math.max(80, Math.min(width - 32, CONTENT_MAX_WIDTH));
        int textLeft = centerX - contentWidth / 2;

        // 不在播放时整块信息垂直居中；在播放时顶部对齐，给下方歌词留空间。
        int top = page.playingThis()
                ? getY() + 8
                : getY() + Math.max(8, (height - INFO_BLOCK_HEIGHT) / 2);
        String coverUrl = page.coverUrl();
        if (coverUrl != null && !coverUrl.isBlank()) {
            MediaThumbnails.getInstance().request(coverUrl);
        }
        drawCover(graphics, centerX - COVER_SIZE / 2, top, coverUrl, page.titleText());
        top += COVER_SIZE + 12;

        Component title = Component.literal(fit(font, page.titleText(), contentWidth, 1.0F));
        int titleY = top;
        top += Math.round(font.lineHeight * 1.0F) + 4;

        String artist = page.artistText();
        if (!artist.isBlank()) {
            GlassWidgets.centeredText(graphics, font,
                    Component.literal(fit(font, artist, contentWidth, 0.72F)),
                    centerX, top, FlatTheme.TEXT_SECONDARY, 0.72F);
            top += Math.round(font.lineHeight * 0.72F) + 2;
        }

        String album = page.albumText();
        if (!album.isBlank()) {
            GlassWidgets.centeredText(graphics, font,
                    Component.literal(fit(font, album, contentWidth, 0.60F)),
                    centerX, top, FlatTheme.TEXT_TERTIARY, 0.60F);
            top += Math.round(font.lineHeight * 0.60F) + 2;
        }

        String duration = page.durationText();
        if (!duration.isBlank()) {
            GlassWidgets.centeredText(graphics, font,
                    Component.literal(duration), centerX, top, FlatTheme.TEXT_FAINT, 0.60F);
            top += Math.round(font.lineHeight * 0.60F) + 2;
        }

        if (page.loading()) {
            GlassWidgets.centeredText(graphics, font,
                    Component.translatable("musicplayer.browser.loading"),
                    centerX, top + 10, FlatTheme.TEXT_TERTIARY, 0.62F);
            return;
        }
        if (!page.status().getString().isBlank()) {
            GlassWidgets.centeredText(graphics, font, page.status(),
                    centerX, top + 10, FlatTheme.TEXT_TERTIARY, 0.62F);
            return;
        }

        int dividerY = top + 4;
        FlatTheme.fill(graphics, textLeft, dividerY, textLeft + contentWidth, dividerY + 1,
                0.5F, FlatTheme.alpha(0xFFFFFF, 0.10F));

        // 只给刚才的文字上色，不再额外画标题阴影
        GlassWidgets.centeredText(graphics, font, title, centerX, titleY,
                FlatTheme.TEXT, 1.0F);

        drawLyrics(graphics, font, centerX, dividerY + 12);
    }

    private void drawLyrics(GuiGraphicsExtractor graphics, Font font, int centerX, int top) {
        if (!page.playingThis()) {
            return;
        }
        GlassWidgets.centeredText(graphics, font,
                Component.translatable("musicplayer.song.now_playing"),
                centerX, top, FlatTheme.accent(), 0.58F);
        top += 12;

        if (page.lyricsLoading()) {
            GlassWidgets.centeredText(graphics, font,
                    Component.translatable("musicplayer.song.lyrics_loading"),
                    centerX, top, FlatTheme.TEXT_TERTIARY, 0.62F);
            return;
        }
        if (page.lyrics().isEmpty()) {
            GlassWidgets.centeredText(graphics, font,
                    Component.translatable("musicplayer.song.lyrics_empty"),
                    centerX, top, FlatTheme.TEXT_TERTIARY, 0.62F);
            return;
        }

        int current = page.currentLyricIndex();
        if (current < 0) {
            // 还没到第一句：显示第一句，提示即将开始
            current = 0;
        }
        int first = Math.max(0, current - LYRIC_CONTEXT_LINES);
        int last = Math.min(page.lyrics().size() - 1, current + LYRIC_CONTEXT_LINES);
        int lyricWidth = Math.max(80, Math.min(width - 40, CONTENT_MAX_WIDTH));
        for (int index = first; index <= last; index++) {
            LyricLine line = page.lyrics().get(index);
            if (index == current) {
                float scale = 0.82F;
                GlassWidgets.centeredText(graphics, font,
                        Component.literal(fit(font, line.text(), lyricWidth, scale)),
                        centerX, top, FlatTheme.TEXT, scale);
                top += Math.round(font.lineHeight * scale) + 7;
            } else {
                float scale = 0.62F;
                int color = index < current ? FlatTheme.TEXT_FAINT : FlatTheme.TEXT_TERTIARY;
                GlassWidgets.centeredText(graphics, font,
                        Component.literal(fit(font, line.text(), lyricWidth, scale)),
                        centerX, top, color, scale);
                top += Math.round(font.lineHeight * scale) + 5;
            }
        }
    }

    private void drawCover(GuiGraphicsExtractor graphics, int x, int y, String url, String title) {
        MediaThumbnails.Thumb thumb = url == null || url.isBlank()
                ? null : MediaThumbnails.getInstance().get(url);
        if (thumb != null) {
            MediaThumbnails.draw(graphics, thumb, x, y, COVER_SIZE);
            return;
        }
        float radius = COVER_SIZE * 0.18F;
        GlassWidgets.roundedFill(graphics, x, y, x + COVER_SIZE, y + COVER_SIZE,
                0xFFFFFF, 0xFFFFFF, radius, 0.12F, 1.0F);
        int inset = COVER_SIZE / 4;
        GlassWidgets.roundedFill(graphics, x + inset, y + inset,
                x + COVER_SIZE - inset, y + COVER_SIZE - inset,
                0xFFFFFF, 0xFFFFFF, Math.max(1.0F, radius - 2.0F), 0.09F, 1.0F);
    }

    /** 按可用宽度收窄文字，必要时截断并补省略号。 */
    private static String fit(Font font, String text, int maxWidth, float scale) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int available = Math.max(1, Math.round(maxWidth / Math.max(0.05F, scale)));
        if (font.width(text) <= available) {
            return text;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, available - font.width(ellipsis))) + ellipsis;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}