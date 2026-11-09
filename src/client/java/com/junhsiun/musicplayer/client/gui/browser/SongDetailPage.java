package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.client.ClientMusicUiState;
import com.junhsiun.musicplayer.client.media.MediaBrowserService;
import com.junhsiun.musicplayer.model.LyricLine;
import com.junhsiun.musicplayer.model.MediaDetail;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.junhsiun.musicplayer.platform.url.UrlUtil;
import com.junhsiun.musicplayer.util.Messages;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;
import java.util.List;

/**
 * 单曲详情页：居中显示封面、歌名、歌手、专辑与时长。
 *
 * <p>如果这首歌正在播放，下方会显示歌词，并随播放进度高亮当前一句。歌词只在
 * <b>本页这首 == 当前播放曲目</b>时出现；其他歌曲在播时这一页只有静态信息，
 * 不会拿别人的歌词去对时间轴。</p>
 *
 * <p>页面本身不持有播放状态：每次渲染都从 {@link ClientMusicUiState} 读当前进度，
 * 由外壳每帧调用 {@link #tickLyrics()} 决定要不要拉歌词。</p>
 */
public final class SongDetailPage extends BrowserPage {

    private final String id;
    private final String fallbackTitle;
    private final String fallbackArtist;
    private final String fallbackCover;

    private MediaDetail detail;
    private boolean loading = true;
    private Component status = Component.empty();
    private List<LyricLine> lyrics = List.of();
    private boolean lyricsLoading;
    private boolean lyricsRequested;

    public SongDetailPage(MediaItem item) {
        this.id = item == null ? "" : item.id();
        this.fallbackTitle = item == null ? "" : item.title();
        this.fallbackArtist = item == null || item.subtitle() == null ? "" : item.subtitle();
        this.fallbackCover = item == null ? "" : item.coverUrl();
        view().setHeader(null);
        view().clearPagination();
        view().showItems(List.of(), false, false);
    }

    /** 平台 ID。 */
    public String id() {
        return id;
    }

    public MediaDetail detail() {
        return detail;
    }

    public String titleText() {
        return detail != null && !detail.title().isBlank() ? detail.title() : fallbackTitle;
    }

    public String artistText() {
        if (detail != null && detail.subtitle() != null) {
            return detail.subtitle();
        }
        return fallbackArtist;
    }

    public String coverUrl() {
        if (detail != null && detail.hasCover()) {
            return detail.coverUrl();
        }
        return fallbackCover;
    }

    /** 专辑名（详情里的 description 字段）。 */
    public String albumText() {
        return detail == null ? "" : detail.description();
    }

    /** 时长文本；拿不到返回空串。 */
    public String durationText() {
        if (detail == null || detail.stats().isEmpty()) {
            return "";
        }
        return detail.stats().get(0).value().getString();
    }

    public boolean loading() {
        return loading;
    }

    public Component status() {
        return status;
    }

    public List<LyricLine> lyrics() {
        return lyrics;
    }

    public boolean lyricsLoading() {
        return lyricsLoading;
    }

    /** 本页这首是不是正在播放的那首。 */
    public boolean playingThis() {
        ClientMusicUiState state = ClientMusicUiState.getInstance();
        return !id.isBlank() && state.hasState() && id.equals(state.trackId());
    }

    /** 当前歌词行下标；没在播放 / 还没到第一句时返回 {@code -1}。 */
    public int currentLyricIndex() {
        if (!playingThis()) {
            return -1;
        }
        long elapsed = ClientMusicUiState.getInstance().elapsedMillis();
        int index = -1;
        for (int line = 0; line < lyrics.size(); line++) {
            if (lyrics.get(line).timeMillis() <= elapsed) {
                index = line;
            } else {
                break;
            }
        }
        return index;
    }

    @Override
    public Component title() {
        return Component.literal(titleText());
    }

    @Override
    public EnumSet<PageChrome> chrome() {
        return EnumSet.of(PageChrome.BACK_BAR);
    }

    @Override
    public void apply() {
        view().setLoading(false);
        view().showItems(List.of(), false, false);
        view().clearPagination();
    }

    @Override
    public void onEnter(BrowserHost host) {
        loadDetail();
    }

    /** 外壳每帧调用：这首歌在播且还没拉过歌词时，补一次请求。 */
    public void tickLyrics() {
        if (!lyricsRequested && playingThis()) {
            loadLyrics();
        }
    }

    private void loadDetail() {
        if (id.isBlank()) {
            loading = false;
            status = Component.translatable("musicplayer.browser.detail_empty");
            apply();
            return;
        }
        MediaBrowserService.getInstance().detail(MediaKind.SONG, id)
                .whenComplete((loaded, throwable) ->
                        Minecraft.getInstance().execute(() -> {
                            loading = false;
                            if (throwable != null) {
                                status = Component.translatable("musicplayer.browser.failed",
                                        Messages.textOrTranslatable(UrlUtil.rootMessageKey(throwable)));
                            } else if (loaded == null || loaded.isEmpty()) {
                                status = Component.translatable("musicplayer.browser.detail_empty");
                            } else {
                                detail = loaded;
                            }
                            apply();
                        }));
    }

    private void loadLyrics() {
        lyricsRequested = true;
        lyricsLoading = true;
        MediaBrowserService.getInstance().lyrics(id)
                .whenComplete((lines, throwable) ->
                        Minecraft.getInstance().execute(() -> {
                            lyricsLoading = false;
                            lyrics = throwable != null || lines == null ? List.of() : lines;
                        }));
    }
}