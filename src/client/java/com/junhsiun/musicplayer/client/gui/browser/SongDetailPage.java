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
 * <p>下方区域有两种形态：本页这首<b>不是</b>当前播放曲目时，显示一枚播放按钮，点一下就
 * 点播这首（等同于聊天栏的 {@code /music play song <id>}）；当播放真的切到这一首时，
 * 播放按钮淡出、歌词淡入，并随播放进度滚动/高亮当前一句。</p>
 *
 * <p>歌词仍然在页面打开时就预取，这样切歌的瞬间歌词已经就绪，不会先出现「加载中」跳一下。</p>
 *
 * <p>页面本身不持有播放状态：每次渲染都从 {@link ClientMusicUiState} 读当前进度，
 * 由外壳每帧调用 {@link #tickLyrics()} 决定要不要拉歌词。</p>
 */
public final class SongDetailPage extends BrowserPage {

    private final String id;
    private final String fallbackTitle;
    private final String fallbackArtist;
    private final String fallbackCover;
    /** 列表带来的时长快照：详情接口回来前，时长那一行也能参与共享元素过渡。 */
    private final String fallbackMeta;

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
        this.fallbackMeta = item == null || item.meta() == null ? "" : item.meta();
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
        // 列表行的副标题是「歌手 · 专辑」；详情页只显示歌手。这里提前把专辑后缀去掉，
        // 避免详情接口回来时歌手行突然变短，也让共享元素落地前后文字保持一致。
        return fallbackArtistText();
    }

    /** 列表副标题里" · "之前的歌手部分。 */
    private String fallbackArtistText() {
        int separator = fallbackArtist.indexOf(" · ");
        return separator > 0 ? fallbackArtist.substring(0, separator) : fallbackArtist;
    }

    /** 列表副标题里" · "之后的专辑部分；没有则空串。 */
    private String fallbackAlbumText() {
        int separator = fallbackArtist.indexOf(" · ");
        return separator > 0 ? fallbackArtist.substring(separator + 3) : "";
    }

    /** 专辑名（详情里的 description 字段）；详情没回来时用列表副标题里拆出的专辑。 */
    public String albumText() {
        if (detail == null) {
            return fallbackAlbumText();
        }
        return detail.description() == null ? "" : detail.description();
    }

    public String coverUrl() {
        if (detail != null && detail.hasCover()) {
            return detail.coverUrl();
        }
        return fallbackCover;
    }

    /** 时长文本；详情还没回来时用列表行里的时长快照，拿不到返回空串。 */
    public String durationText() {
        if (detail == null || detail.stats().isEmpty()) {
            return fallbackMeta;
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

    /**
     * 点播本页这首：走和聊天栏 {@code /music play song <id>} 完全相同的链路。
     *
     * <p>有别的歌在播时服务端会把它追加进队列，空闲时则直接开始播放；两种行为都由服务端
     * 决定，页面只负责把请求发出去。</p>
     */
    public void playThis() {
        if (host == null || id.isBlank()) {
            return;
        }
        host.play(new MediaItem(id, MediaKind.SONG, titleText(),
                artistText(), coverUrl(), durationText()));
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

    /** 返回本页时如果详情还没拉到（被后续压栈取消），补一次请求。 */
    @Override
    public void onResume(BrowserHost host) {
        if (detail == null && !id.isBlank()) {
            loadDetail();
        }
        super.onResume(host);
    }

    /**
     * 外壳每帧调用：只要还没拉过歌词就补一次请求。
     *
     * <p>打开任意一首歌的详情页都要能立刻看到歌词——不再要求"本页这首正在播放"才拉。
     * 是否随播放进度同步高亮由 {@link #currentLyricIndex()} 决定（只有本页在播时才推进），
     * 不在播的页面把歌词静态从头显示。</p>
     */
    public void tickLyrics() {
        if (!lyricsRequested && !id.isBlank()) {
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
                            if (MediaBrowserService.isCancelled(throwable)) {
                                loading = false;
                                return;
                            }
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