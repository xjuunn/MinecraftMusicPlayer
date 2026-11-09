package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.client.media.MediaBrowserService;
import com.junhsiun.musicplayer.client.media.MediaLinks;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.junhsiun.musicplayer.model.MediaPage;
import com.junhsiun.musicplayer.platform.url.UrlUtil;
import com.junhsiun.musicplayer.util.Messages;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 搜索页：搜索行 + 类型标签栏 + 结果列表 + 翻页栏。
 *
 * <p>数据（关键词、当前类型、页码、结果）都挂在本页字段上，所以从详情页返回时原样还在，
 * 既不会重新请求，也不会把滚动位置丢掉。</p>
 */
public final class SearchPage extends BrowserPage {

    /** 标签栏顺序：音乐在前，默认打开音乐。 */
    public static final List<MediaKind> TABS = List.of(
            MediaKind.SONG, MediaKind.PLAYLIST, MediaKind.ARTIST, MediaKind.USER, MediaKind.RADIO);
    /** 每页条数。 */
    public static final int PAGE_SIZE = 30;

    private String keyword = "";
    private MediaKind tab = MediaKind.SONG;
    private int page = 1;
    private int total = -1;
    private List<MediaItem> results = List.of();
    private boolean loading;
    private Component status = Component.empty();
    /** 搜索序号：连点搜索时只认最后一次的回包。 */
    private int token;

    @Override
    public Component title() {
        return Component.empty();
    }

    @Override
    public EnumSet<PageChrome> chrome() {
        return EnumSet.of(PageChrome.SEARCH_BAR, PageChrome.TABS);
    }

    @Override
    public void apply() {
        view().setHeader(null);
        view().setLoading(loading);
        view().setStatus(status);
        view().showItems(results, false, false);
        // 总数未知时用 canGoNext() 判断下一页，最后一页不会留下点了没反应的箭头
        view().setPagination(page, totalPages(), canGoNext());
    }

    /** 总页数；数据来源没给总数时返回 {@code -1}（页脚只显示当前页）。 */
    private int totalPages() {
        if (total > 0) {
            return (total + PAGE_SIZE - 1) / PAGE_SIZE;
        }
        return results.isEmpty() ? 1 : -1;
    }

    // ── 状态读取（界面摆外壳时要用） ────────────────────────────────────

    public String keyword() {
        return keyword;
    }

    public void setKeyword(String value) {
        this.keyword = value == null ? "" : value;
    }

    public MediaKind tab() {
        return tab;
    }

    public int page() {
        return page;
    }

    public int total() {
        return total;
    }

    public boolean loading() {
        return loading;
    }

    public boolean hasResults() {
        return !results.isEmpty();
    }

    /** 还有没有下一页。拿不到总数时按"本页是否满页"判断。 */
    public boolean canGoNext() {
        if (total > 0) {
            return (long) page * PAGE_SIZE < total;
        }
        return results.size() >= PAGE_SIZE;
    }

    // ── 交互 ────────────────────────────────────────────────────────────

    /** 搜索按钮 / 回车：先看是不是链接，不是再当关键词搜。 */
    public void submit() {
        String text = keyword == null ? "" : keyword.trim();
        Optional<MediaLinks.Target> link = MediaLinks.parse(text);
        if (link.isPresent()) {
            host.push(new DetailPage(link.get().kind(), link.get().id()));
            return;
        }
        if (text.isEmpty()) {
            results = List.of();
            total = -1;
            page = 1;
            loading = false;
            status = Component.empty();
            apply();
            refreshChrome();
            return;
        }
        if (looksLikeLink(text)) {
            // 是网易云链接却认不出类型：直接说清楚，别拿整条链接去搜出一片空白
            results = List.of();
            total = -1;
            page = 1;
            loading = false;
            status = Component.translatable("musicplayer.browser.link_unsupported");
            apply();
            refreshChrome();
            return;
        }
        search(1);
    }

    private static boolean looksLikeLink(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("music.163.com") || normalized.contains("163cn.tv");
    }

    /** 换标签页：已经有关键词就顺手按新类型再搜一次，省一次点击。 */
    public void selectTab(int index) {
        if (index < 0 || index >= TABS.size()) {
            return;
        }
        MediaKind next = TABS.get(index);
        if (next == tab) {
            return;
        }
        tab = next;
        page = 1;
        total = -1;
        results = List.of();
        status = Component.empty();
        if (!keyword.trim().isEmpty()) {
            search(1);
            return;
        }
        loading = false;
        apply();
        refreshChrome();
    }

    /** 翻页（-1 上一页，+1 下一页）。 */
    public void goToPage(int delta) {
        if (loading) {
            return;
        }
        int target = page + delta;
        if (target < 1 || (delta > 0 && !canGoNext())) {
            return;
        }
        search(target);
    }

    /** 列表末尾的分页页脚。 */
    @Override
    public void onPageChange(int delta) {
        goToPage(delta);
    }

    private void search(int targetPage) {
        int requestToken = ++token;
        page = Math.max(1, targetPage);
        loading = true;
        status = Component.empty();
        // 只把加载态打开，<b>不要</b>在这里播入场动画：等结果回来统一播一次，
        // 否则"开始搜索"和"结果到达"各播一次，看起来就是列表加载了两遍。
        apply();
        refreshChrome();
        MediaBrowserService.getInstance().search(tab, keyword.trim(), page, PAGE_SIZE)
                .whenComplete((result, throwable) ->
                        Minecraft.getInstance().execute(() -> applyResults(requestToken, result, throwable)));
    }

    private void applyResults(int requestToken, MediaPage result, Throwable throwable) {
        if (requestToken != token) {
            return;
        }
        loading = false;
        if (throwable != null) {
            results = List.of();
            total = -1;
            status = failed(throwable);
        } else {
            results = result == null ? List.of() : result.items();
            total = result == null ? -1 : result.total();
            status = results.isEmpty()
                    ? Component.translatable("musicplayer.browser.no_result")
                    : Component.empty();
        }
        apply();
        // 结果到齐才播一次错峰入场 + 回到顶部
        view().resetAndReveal();
        refreshChrome();
    }

    private static Component failed(Throwable throwable) {
        return Component.translatable("musicplayer.browser.failed",
                Messages.textOrTranslatable(UrlUtil.rootMessageKey(throwable)));
    }
}
