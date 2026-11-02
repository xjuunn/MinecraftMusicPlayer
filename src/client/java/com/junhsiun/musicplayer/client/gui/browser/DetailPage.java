package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.client.gui.media.MediaListView;
import com.junhsiun.musicplayer.client.media.MediaBrowserService;
import com.junhsiun.musicplayer.model.MediaDetail;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.junhsiun.musicplayer.platform.url.UrlUtil;
import com.junhsiun.musicplayer.util.Messages;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 详情页：歌单 / 歌手 / 用户 / 播客 / 单曲。
 *
 * <p>外壳只有顶栏（返回按钮 + 标题）：<b>没有搜索框、没有标签栏、没有翻页栏</b>。封面、标题、
 * 简介、数据标签都在列表的头部区块里，跟着列表一起滚动——固定在顶上的话，能滚的区域只剩
 * 中间一小条，翻起来很难受。</p>
 *
 * <p>条目是"加载更多"，不是分页：详情页的条目可能上千条，用页码翻会让用户失去位置感。</p>
 */
public final class DetailPage extends BrowserPage {

    private final MediaKind kind;
    private final String id;

    private MediaDetail detail;
    private List<MediaItem> entries = List.of();
    private boolean hasMore;
    private boolean loading = true;
    private boolean loadingEntries;
    private boolean loadingMore;
    private Component status = Component.empty();

    public DetailPage(MediaKind kind, String id) {
        this.kind = kind == null ? MediaKind.SONG : kind;
        this.id = id == null ? "" : id;
    }

    @Override
    public Component title() {
        return detail == null ? Component.empty() : Component.literal(detail.title());
    }

    @Override
    public EnumSet<PageChrome> chrome() {
        return EnumSet.noneOf(PageChrome.class);
    }

    @Override
    public void apply() {
        MediaDetail current = detail;
        view().setHeader(current == null ? null : new MediaListView.Header(
                current.coverUrl(),
                Component.literal(current.title()),
                current.subtitle() == null ? null : Component.literal(current.subtitle()),
                current.stats(),
                current.description(),
                // 只有歌单能"整个播"：/music play playlist <id>
                kind == MediaKind.PLAYLIST));
        view().setLoading(loading);
        view().setStatus(status);
        view().showItems(entries, hasMore, loadingMore);
    }

    @Override
    public void onPlayAll() {
        if (host == null || detail == null || id.isBlank() || kind != MediaKind.PLAYLIST) {
            return;
        }
        // 和行内播放按钮走同一条命令，效果等同于在聊天栏敲 /music play playlist <id>
        host.play(new MediaItem(id, kind, detail.title(), detail.subtitle(), detail.coverUrl()));
    }

    @Override
    public void onEnter(BrowserHost host) {
        loadDetail();
    }

    @Override
    public void onLoadMore() {
        if (!hasMore || loadingEntries) {
            return;
        }
        loadEntries(entries.size(), true);
    }

    // ── 数据 ────────────────────────────────────────────────────────────

    private void loadDetail() {
        MediaBrowserService.getInstance().detail(kind, id)
                .whenComplete((loaded, throwable) ->
                        Minecraft.getInstance().execute(() -> {
                            if (throwable != null) {
                                loading = false;
                                status = failed(throwable);
                                apply();
                                return;
                            }
                            if (loaded == null || loaded.isEmpty()) {
                                // 接口回包认不出来（被风控、换了字段）：别留一个空壳页面
                                loading = false;
                                status = Component.translatable("musicplayer.browser.detail_empty");
                                apply();
                                return;
                            }
                            detail = loaded;
                            if (kind == MediaKind.SONG) {
                                // 单曲没有下级列表，拉到元数据就算完
                                loading = false;
                                apply();
                                view().resetAndReveal();
                                return;
                            }
                            // 元数据到了，条目还在路上：继续显示加载动画
                            apply();
                            loadEntries(0, false);
                        }));
    }

    private void loadEntries(int offset, boolean append) {
        if (loadingEntries) {
            return;
        }
        loadingEntries = true;
        if (append) {
            loadingMore = true;
            apply();
        }
        MediaBrowserService.getInstance().entries(kind, id, offset, SearchPage.PAGE_SIZE)
                .whenComplete((items, throwable) ->
                        Minecraft.getInstance().execute(() -> {
                            loadingEntries = false;
                            loadingMore = false;
                            if (throwable != null) {
                                loading = false;
                                status = failed(throwable);
                                apply();
                                return;
                            }
                            List<MediaItem> received = items == null ? List.of() : items;
                            if (append) {
                                List<MediaItem> merged = new ArrayList<>(entries.size() + received.size());
                                merged.addAll(entries);
                                merged.addAll(received);
                                entries = merged;
                            } else {
                                entries = received;
                            }
                            hasMore = received.size() >= SearchPage.PAGE_SIZE;
                            loading = false;
                            if (entries.isEmpty() && status.getString().isBlank()) {
                                status = Component.translatable("musicplayer.browser.no_result");
                            }
                            apply();
                            if (!append) {
                                view().resetAndReveal();
                            }
                        }));
    }

    private static Component failed(Throwable throwable) {
        return Component.translatable("musicplayer.browser.failed",
                Messages.textOrTranslatable(UrlUtil.rootMessageKey(throwable)));
    }
}
