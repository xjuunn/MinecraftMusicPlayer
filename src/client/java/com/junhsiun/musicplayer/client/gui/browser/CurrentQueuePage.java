package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.client.ClientMusicUiState;
import com.junhsiun.musicplayer.client.gui.icon.SvgIcons;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.junhsiun.musicplayer.model.QueueEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 当前队列页：正在播放的歌曲 + 接下来要播放的歌曲。
 *
 * <p>数据由服务端按需推送（{@code MusicQueuePayload}），页面本身不发请求：
 * 队列会在打开页面时刷新一次，之后由外壳在页面可见期间低频轮询。这样长队列不会
 * 跟着两秒一次的播放状态广播反复占用带宽。</p>
 *
 * <p>页面顶部没有独立的播放控制卡片：正在播放的那首直接作为列表第一行，行尾放
 * 「快进」和「跳过」两个按钮；其余行只负责展示。点击任意一行都打开歌曲详情页，
 * 不会重新点播。</p>
 */
public final class CurrentQueuePage extends BrowserPage {

    public CurrentQueuePage() {
        view().setSongsClickable(true);
    }

    @Override
    public Component title() {
        return Component.translatable("musicplayer.app.queue");
    }

    @Override
    public EnumSet<PageChrome> chrome() {
        return EnumSet.of(PageChrome.TITLE_BAR);
    }

    @Override
    public void apply() {
        ClientMusicUiState state = ClientMusicUiState.getInstance();
        List<MediaItem> items = new ArrayList<>();
        String currentId = state.hasState() ? state.trackId() : "";
        if (!currentId.isBlank()) {
            items.add(new MediaItem(currentId, MediaKind.SONG, state.title(),
                    subtitleOf(state.artist(), state.requester()), state.coverUrl()));
        }
        for (QueueEntry entry : state.queue()) {
            items.add(new MediaItem(entry.id(), MediaKind.SONG, entry.title(),
                    subtitleOf(entry.artist(), entry.requester()), entry.coverUrl()));
        }
        view().showItems(items, false, false);

        if (currentId.isBlank()) {
            // 没有正在播放的歌曲时，队列里每一行都没有可执行的操作，整列收起。
            view().setRowActionsVisible(false);
            view().setActionRowId(null);
            view().setSecondaryActionIcon(null);
        } else {
            view().setRowActionsVisible(true);
            view().setActionIcon(SvgIcons.SKIP);
            view().setActionRowId(currentId);
            view().setSecondaryActionIcon(SvgIcons.FAST_FORWARD);
        }

        if (items.isEmpty()) {
            boolean preparing = state.queuePlaylistMode() && state.queuePlaylistRemaining() > 0;
            view().setStatus(Component.translatable(preparing
                    ? "musicplayer.app.queue_preparing"
                    : "musicplayer.app.queue_empty"));
        } else {
            view().setStatus(Component.empty());
        }
        refreshProgress();
        // 队列不分页
        view().clearPagination();
    }

    /** 把当前曲目的播放进度铺到那一行的背景上；由外壳每帧刷新。 */
    public void refreshProgress() {
        ClientMusicUiState state = ClientMusicUiState.getInstance();
        long duration = state.durationMillis();
        float progress = duration <= 0L ? 0.0F : (float) ((double) state.elapsedMillis() / duration);
        view().setRowProgress(state.trackId(), progress);
    }

    /** 歌手 + 点歌人合成一行副标题；两者都没有时返回空串（列表里就只显示标题）。 */
    private static String subtitleOf(String artist, String requester) {
        String name = artist == null ? "" : artist;
        if (requester == null || requester.isBlank()) {
            return name;
        }
        String requestedBy = Component.translatable(
                "musicplayer.app.queue_requester", requester).getString();
        return name.isBlank() ? requestedBy : name + " · " + requestedBy;
    }

    /** 只有正在播放的那一行会画出行尾按钮，点它就是跳过当前歌曲。 */
    @Override
    public void onPlay(MediaItem item) {
        if (host == null || item == null) {
            return;
        }
        if (item.id().equals(ClientMusicUiState.getInstance().trackId())) {
            host.skip();
        }
    }

    /** 正在播放那一行的第二个按钮：快进 10 秒。 */
    @Override
    public void onSecondaryPlay(MediaItem item) {
        if (host == null || item == null) {
            return;
        }
        if (item.id().equals(ClientMusicUiState.getInstance().trackId())) {
            host.seekForward();
        }
    }
}