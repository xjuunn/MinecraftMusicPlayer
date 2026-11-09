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

import java.util.EnumSet;
import java.util.List;

/**
 * 详情页：歌单 / 歌手 / 用户 / 播客 / 单曲。
 *
 * <p>外壳只有顶栏（返回按钮 + 标题）：<b>没有搜索框、没有标签栏、没有独立翻页栏</b>。
 * 封面、标题、简介、数据标签都在列表的头部区块里，跟着列表一起滚动——固定在顶上的话，
 * 能滚的区域只剩中间一小条，翻起来很难受。</p>
 *
 * <p>条目和搜索页一样按页加载：最后一条 item 后面直接跟着上一页 / 下一页 / 页码，
 * 不出现独立的"加载更多"。每次翻页都会回到列表顶部。</p>
 */
public final class DetailPage extends BrowserPage {

    private final MediaKind kind;
    private final String id;

    private MediaDetail detail;
    private List<MediaItem> entries = List.of();
    private int page = 1;
    /** 下级条目总数；{@code <= 0} 表示数据来源没给。 */
    private int totalEntries;
    private boolean loading = true;
    private boolean loadingEntries;
    private Component status = Component.empty();

    public DetailPage(MediaKind kind, String id) {
        this.kind = kind == null ? MediaKind.SONG : kind;
        this.id = id == null ? "" : id;
    }

    /** 条目类型：界面据此决定要不要显示"收藏歌单"按钮。 */
    public MediaKind kind() {
        return kind;
    }

    /** 平台 ID。 */
    public String id() {
        return id;
    }

    /** 已经加载到的元数据；还没回来时为 {@code null}。 */
    public MediaDetail detail() {
        return detail;
    }

    @Override
    public Component title() {
        return detail == null ? Component.empty() : Component.literal(detail.title());
    }

    @Override
    public EnumSet<PageChrome> chrome() {
        // 详情页是压栈打开的，必须能退回上一页；这里以前返回空集合，于是顶栏（返回按钮 + 标题）
        // 一直不出现，用户只能按 Esc 把整个窗口关掉。
        return EnumSet.of(PageChrome.BACK_BAR);
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
        view().showItems(entries, false, false);
        view().setPagination(page, totalPages(), canGoNext());
    }

    /** 还有没有下一页；条目总数未知时按"本页是否满页"判断。 */
    private boolean canGoNext() {
        if (totalEntries > 0) {
            return page < totalPages();
        }
        return entries.size() >= SearchPage.PAGE_SIZE;
    }

    /** 总页数；条目总数未知时返回 {@code -1}，页脚只显示当前页。 */
    private int totalPages() {
        if (totalEntries > 0) {
            return (totalEntries + SearchPage.PAGE_SIZE - 1) / SearchPage.PAGE_SIZE;
        }
        return entries.isEmpty() ? 1 : -1;
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

    /** 列表末尾的分页页脚。 */
    @Override
    public void onPageChange(int delta) {
        goToPage(delta);
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
                            totalEntries = loaded.totalEntries();
                            if (kind == MediaKind.SONG) {
                                // 单曲没有下级列表，拉到元数据就算完
                                loading = false;
                                apply();
                                view().resetAndReveal();
                                return;
                            }
                            // 元数据到了，条目还在路上：继续显示加载动画
                            loadEntries(1);
                        }));
    }

    private void goToPage(int delta) {
        if (loading || loadingEntries) {
            return;
        }
        int target = page + delta;
        if (target < 1) {
            return;
        }
        if (totalEntries > 0 && target > totalPages()) {
            return;
        }
        if (delta > 0 && totalEntries <= 0 && entries.size() < SearchPage.PAGE_SIZE) {
            // 总数未知时，本页没满就说明到底了
            return;
        }
        loadEntries(target);
    }

    private void loadEntries(int targetPage) {
        if (loadingEntries) {
            return;
        }
        loadingEntries = true;
        page = Math.max(1, targetPage);
        loading = true;
        status = Component.empty();
        apply();
        int offset = (page - 1) * SearchPage.PAGE_SIZE;
        MediaBrowserService.getInstance().entries(kind, id, offset, SearchPage.PAGE_SIZE)
                .whenComplete((items, throwable) ->
                        Minecraft.getInstance().execute(() -> {
                            loadingEntries = false;
                            loading = false;
                            if (throwable != null) {
                                status = failed(throwable);
                                apply();
                                return;
                            }
                            List<MediaItem> received = items == null ? List.of() : items;
                            entries = received;
                            if (received.size() < SearchPage.PAGE_SIZE) {
                                // 这一页没满 = 最后一页，顺便把总数定下来
                                totalEntries = offset + received.size();
                            }
                            if (entries.isEmpty() && status.getString().isBlank()) {
                                status = Component.translatable("musicplayer.browser.no_result");
                            }
                            apply();
                            // 翻页后回到顶部，用户不会停在上一页的滚动位置
                            view().resetAndReveal();
                        }));
    }

    private static Component failed(Throwable throwable) {
        return Component.translatable("musicplayer.browser.failed",
                Messages.textOrTranslatable(UrlUtil.rootMessageKey(throwable)));
    }
}