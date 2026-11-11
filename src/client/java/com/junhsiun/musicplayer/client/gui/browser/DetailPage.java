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

    /** 从列表点进来时带的条目快照：详情接口回来前先用它搭头部，共享元素动画不必等网络。 */
    private final String fallbackTitle;
    private final String fallbackSubtitle;
    private final String fallbackCover;

    private MediaDetail detail;
    private List<MediaItem> entries = List.of();
    private int page = 1;
    /** 下级条目总数；{@code <= 0} 表示数据来源没给。 */
    private int totalEntries;
    private boolean loading = true;
    private boolean loadingEntries;
    private Component status = Component.empty();

    public DetailPage(MediaKind kind, String id) {
        this(kind, id, null);
    }

    /** 带列表条目快照的构造：点歌单/歌手等行进入详情时用，可直接开始共享元素过渡。 */
    public DetailPage(MediaItem item) {
        this(item == null ? MediaKind.SONG : item.kind(),
                item == null ? "" : item.id(), item);
    }

    private DetailPage(MediaKind kind, String id, MediaItem fallback) {
        this.kind = kind == null ? MediaKind.SONG : kind;
        this.id = id == null ? "" : id;
        this.fallbackTitle = fallback == null || fallback.title() == null ? "" : fallback.title();
        String subtitle = fallback == null || fallback.subtitle() == null ? "" : fallback.subtitle();
        // 列表行的副标题比详情头部多带了一段统计（歌单是「创建者 · N 首」，用户是签名）。
        // 头部只显示真正的副标题，这里先按同一语义裁掉多余部分，避免详情回来时文字突然变化；
        // 用户详情本身没有副标题，直接留空，也就不会做一段并不存在的过渡。
        if (this.kind == MediaKind.PLAYLIST) {
            int separator = subtitle.indexOf(" · ");
            if (separator > 0) {
                subtitle = subtitle.substring(0, separator);
            }
        } else if (this.kind == MediaKind.USER) {
            subtitle = "";
        }
        this.fallbackSubtitle = subtitle;
        this.fallbackCover = fallback == null || fallback.coverUrl() == null ? "" : fallback.coverUrl();
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
        if (detail != null) {
            return Component.literal(detail.title());
        }
        return Component.literal(fallbackTitle);
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
        String title = current == null ? fallbackTitle : current.title();
        String cover = current == null ? fallbackCover : current.coverUrl();
        String subtitle = current == null ? fallbackSubtitle : current.subtitle();
        // 有详情就用详情；详情还没回来但列表带了标题/封面时，也先搭一个头部，
        // 这样共享元素动画可以直接开始，网络结果回来后再原地补全其余字段。
        boolean hasHeader = current != null || !fallbackTitle.isBlank() || !fallbackCover.isBlank();
        view().setHeader(hasHeader ? new MediaListView.Header(
                cover,
                Component.literal(title == null ? "" : title),
                subtitle == null || subtitle.isBlank() ? null : Component.literal(subtitle),
                current == null ? List.of() : current.stats(),
                current == null ? null : current.description(),
                // 只有歌单能"整个播"：/music play playlist <id>。详情没回来时按钮先不出现，
                // 避免出现一个点了没反应的"播放全部"。
                current != null && kind == MediaKind.PLAYLIST) : null);
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
        // 详情元数据与下级条目互不依赖：并行发起。歌单尤其明显——条目还要再拉一次
        // 全量 trackIds，串行等于让列表白等一次网络往返。
        if (kind != MediaKind.SONG) {
            loadEntries(1);
        }
        loadDetail();
    }

    /** 列表末尾的分页页脚。 */
    @Override
    public void onPageChange(int delta) {
        goToPage(delta);
    }

    /**
     * 返回本页时补齐缺失的数据。
     *
     * <p>本页还在栈里时，压栈打开下级页面会取消它的在途请求；如果那时详情或条目还没回来，
     * 返回后需要重新发起，否则会一直停在加载/空壳状态。</p>
     */
    @Override
    public void onResume(BrowserHost host) {
        if (detail == null) {
            loadDetail();
        }
        if (kind != MediaKind.SONG && entries.isEmpty() && !loadingEntries) {
            loadEntries(page);
        }
        super.onResume(host);
    }

    // ── 数据 ────────────────────────────────────────────────────────────

    private void loadDetail() {
        MediaBrowserService.getInstance().detail(kind, id)
                .whenComplete((loaded, throwable) ->
                        Minecraft.getInstance().execute(() -> {
                            // 页面已经切走：结果直接丢弃，不覆盖任何状态。
                            if (MediaBrowserService.isCancelled(throwable)) {
                                // 条目可能还在跑：保持加载态；本页若仍在栈里，返回时 onResume 会补拉。
                                loading = loadingEntries;
                                return;
                            }
                            if (throwable != null) {
                                // 条目请求还在跑时先别用元数据失败覆盖列表；等条目结果说话。
                                if (loadingEntries) {
                                    return;
                                }
                                loading = false;
                                status = failed(throwable);
                                apply();
                                return;
                            }
                            if (loaded == null || loaded.isEmpty()) {
                                // 接口回包认不出来（被风控、换了字段）：别留一个空壳页面
                                if (loadingEntries) {
                                    return;
                                }
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
                            // 元数据到了：只补头部；loading 由并行中的条目请求负责收尾。
                            apply();
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
                            if (MediaBrowserService.isCancelled(throwable)) {
                                loadingEntries = false;
                                loading = false;
                                return;
                            }
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