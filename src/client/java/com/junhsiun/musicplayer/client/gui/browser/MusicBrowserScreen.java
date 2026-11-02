package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.client.gui.glass.GlassPanel;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassButton;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassLabel;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassSearchField;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassTabs;
import com.junhsiun.musicplayer.client.gui.media.MediaListView;
import com.junhsiun.musicplayer.client.gui.window.GlassWindow;
import com.junhsiun.musicplayer.client.gui.window.GlassWindowScreen;
import com.junhsiun.musicplayer.client.gui.window.WindowAnchor;
import com.junhsiun.musicplayer.client.gui.window.WindowAnimations;
import com.junhsiun.musicplayer.client.gui.window.WindowBounds;
import com.junhsiun.musicplayer.client.gui.window.WindowSize;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;

/**
 * 音乐浏览窗口：一个统一的"页面宿主"。
 *
 * <p><b>结构：</b>窗口只做三件事——维护返回栈、按栈顶页面需要的外壳摆控件、把页面切换的
 * 动画与交互转给页面。页面本身（{@link SearchPage} / {@link DetailPage} / 以后新增的）
 * 只管自己的数据和一个 {@link MediaListView}。</p>
 *
 * <p><b>为什么外壳要跟着页面走：</b>搜索框、标签栏、翻页栏只在"搜索页"有意义。详情页里
 * 留着它们，既占地方又容易让人误以为还能就地搜索。每个页面用 {@link BrowserPage#chrome()}
 * 声明自己要什么，这里统一摆——新增页面不用改布局代码。</p>
 *
 * <p><b>状态为什么放在页面字段里：</b>{@link #buildContent} 会在窗口缩放、页面切换时重跑，
 * 控件每次都是新对象。数据挂在页面上，重建后按同一份状态重新摆一遍即可，返回上一页时也
 * 能原样复用（不重新请求、不丢滚动位置）。</p>
 */
public final class MusicBrowserScreen extends GlassWindowScreen implements BrowserHost {

    /** 内容区尺寸。宽度按"列表行里序号 + 封面 + 标题 + 时长 + 播放按钮 + 箭头"排版反推。 */
    private static final int WIDTH = 400;
    private static final int HEIGHT = 372;
    private static final int PADDING = 14;
    private static final int SEARCH_HEIGHT = 22;
    private static final int GAP = 8;
    private static final int SEARCH_BUTTON_WIDTH = 56;
    private static final int TABS_HEIGHT = 22;
    /** 搜索框与标签栏之间的间距。标签栏紧贴在搜索框下面。 */
    private static final int TABS_GAP = 6;
    /** 翻页栏高度（与 GlassTheme.Sizes.CONTROL 一致，玻璃按钮就是这个高度）。 */
    private static final int PAGER_HEIGHT = GlassTheme.Sizes.CONTROL;
    private static final int PAGER_GAP = 6;
    /** 翻页按钮宽度。 */
    private static final int PAGE_BUTTON_WIDTH = 26;
    /** 详情页顶栏：返回按钮宽度与顶栏到列表的间距。 */
    private static final int BACK_WIDTH = 54;
    private static final int BAR_GAP = 6;

    private final Deque<BrowserPage> stack = new ArrayDeque<>();
    private final SearchPage searchPage = new SearchPage();

    private GlassSearchField searchField;
    private GlassButton searchButton;
    private GlassTabs tabs;
    private GlassButton prevPageButton;
    private GlassButton nextPageButton;
    private GlassLabel pageLabelWidget;
    private GlassLabel titleLabel;
    private boolean refocusOnTick;

    public MusicBrowserScreen() {
        super(new GlassWindow()
                .style(GlassTheme.windowStyle())
                .size(WindowSize.preferred(WIDTH + PADDING * 2, HEIGHT + PADDING * 2, 34))
                .anchor(WindowAnchor.CENTER)
                .contentPadding(PADDING, PADDING)
                .opening(WindowAnimations.riseFromBottom()));
        stack.push(searchPage);
        searchPage.attach(this);
    }

    // ── 页面宿主 ────────────────────────────────────────────────────────

    @Override
    public void push(BrowserPage page) {
        if (page == null) {
            return;
        }
        page.attach(this);
        stack.push(page);
        // 先让新页面进入"从右侧滑入"的状态，再重建控件，动画从这一帧就开始走
        page.view().startTransition(true);
        relayoutContent();
        page.onEnter(this);
    }

    @Override
    public void pop() {
        if (stack.size() <= 1) {
            return;
        }
        BrowserPage leaving = stack.pop();
        leaving.onExit(this);
        BrowserPage next = top();
        next.view().startTransition(false);
        relayoutContent();
        next.onResume(this);
    }

    @Override
    public boolean canGoBack() {
        return stack.size() > 1;
    }

    /**
     * 点播：等价于在聊天栏里敲 {@code /music play ...}。
     *
     * <p>走客户端命令而不是自己发网络包：权限校验、加载提示、队列规则全在服务端那一套里，
     * 界面这边重复实现只会和命令行为分叉。</p>
     */
    @Override
    public void play(MediaItem item) {
        if (item == null || item.id().isBlank()) {
            return;
        }
        String command = switch (item.kind()) {
            case SONG -> "music play song " + item.id();
            case PLAYLIST -> "music play playlist " + item.id();
            default -> null;
        };
        if (command == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        try {
            minecraft.player.connection.sendCommand(command);
        } catch (RuntimeException exception) {
            // 命令不存在（服务端没装/没同步）时别把界面崩掉
            MusicPlayerMod.LOGGER.warn("点播命令发送失败: {}", command, exception);
        }
    }

    @Override
    public void refreshChrome() {
        updatePager();
        updateTitle();
    }

    /**
     * 顶栏标题：只在头部区块滚出可见区之后才显示。
     *
     * <p>详情页的标题在头部里已经有一份了，刚打开就上下各一个会显得重复；滚过去之后
     * 头部看不见了，这时才需要顶栏补上，否则整页只剩封面和简介，看不出自己在哪一页。</p>
     */
    private void updateTitle() {
        if (titleLabel == null) {
            return;
        }
        BrowserPage page = top();
        titleLabel.setMessage(page.view().headerScrolledAway()
                ? page.title()
                : Component.empty());
    }

    private BrowserPage top() {
        BrowserPage page = stack.peek();
        return page == null ? searchPage : page;
    }

    // ── 布局 ────────────────────────────────────────────────────────────

    @Override
    protected void buildContent(WindowBounds content) {
        window().style(GlassTheme.windowStyle());
        // 管线编译与离屏贴图创建放在渲染循环之外，避免首帧黑屏
        GlassPanel.prewarm(window().style());

        BrowserPage page = top();
        EnumSet<PageChrome> chrome = page.chrome();

        // 控件每次重建都是新对象：先把上一批的引用清掉，否则这些字段会指向已经被丢弃的
        // 控件（焦点、可见性都会作用到不在界面上的对象上）
        searchField = null;
        searchButton = null;
        tabs = null;
        titleLabel = null;
        prevPageButton = null;
        nextPageButton = null;
        pageLabelWidget = null;

        int x = content.x0();
        int width = content.width();
        int top = content.y0();

        // ① 固定顶栏：返回 + 标题（只有需要返回的页面才有）
        if (chrome.contains(PageChrome.BACK_BAR)) {
            addRenderableWidget(GlassButton.regular(x, top, BACK_WIDTH,
                    Component.translatable("musicplayer.browser.back"), this::pop));
            titleLabel = addRenderableWidget(new GlassLabel(x + BACK_WIDTH + GAP, top,
                    Math.max(1, width - BACK_WIDTH - GAP), PAGER_HEIGHT, page.title(),
                    GlassTheme.Text.LABEL, GlassTheme.TEXT));
            top += PAGER_HEIGHT + BAR_GAP;
        }

        // ② 搜索行 + 标签栏（只有搜索页有）
        if (chrome.contains(PageChrome.SEARCH_BAR)) {
            int fieldWidth = Math.max(60, width - GAP - SEARCH_BUTTON_WIDTH);
            searchField = addRenderableWidget(GlassSearchField.create(x, top, fieldWidth, SEARCH_HEIGHT,
                    Component.translatable("musicplayer.browser.search_hint"),
                    value -> searchPage.setKeyword(value)));
            searchField.setValue(searchPage.keyword());
            searchButton = addRenderableWidget(GlassButton.large(x + width - SEARCH_BUTTON_WIDTH, top,
                    SEARCH_BUTTON_WIDTH, Component.translatable("musicplayer.browser.search"),
                    searchPage::submit, GlassButton.Kind.PROMINENT));
            top += SEARCH_HEIGHT + TABS_GAP;
        }
        if (chrome.contains(PageChrome.TABS)) {
            List<Component> labels = new ArrayList<>(SearchPage.TABS.size());
            for (MediaKind kind : SearchPage.TABS) {
                labels.add(Component.translatable(kind.labelKey()));
            }
            tabs = addRenderableWidget(new GlassTabs(x, top, width, TABS_HEIGHT, labels,
                    SearchPage.TABS.indexOf(searchPage.tab())));
            tabs.onSelect(searchPage::selectTab);
            top += TABS_HEIGHT + GAP;
        }

        // ③ 列表：剩下的高度全给它（翻页栏要给底部留出位置）
        int footer = chrome.contains(PageChrome.PAGER) ? PAGER_HEIGHT + PAGER_GAP : 0;
        int listHeight = Math.max(MediaListView.ROW_HEIGHT * 3, content.y1() - top - footer);
        MediaListView view = page.view();
        // 参数顺序是 (width, height, x, y)：AbstractWidget.setRectangle 内部做的是
        // setSize(w, h) + setPosition(x, y)，按 (x, y, w, h) 传会把控件摆到右下角
        view.setRectangle(width, listHeight, x, top);
        addRenderableWidget(view);
        page.apply();

        // ④ 翻页栏
        if (chrome.contains(PageChrome.PAGER)) {
            int pagerTop = top + listHeight + PAGER_GAP;
            prevPageButton = addRenderableWidget(GlassButton.regular(x, pagerTop, PAGE_BUTTON_WIDTH,
                    Component.literal("‹"), () -> searchPage.goToPage(-1)));
            nextPageButton = addRenderableWidget(GlassButton.regular(x + width - PAGE_BUTTON_WIDTH,
                    pagerTop, PAGE_BUTTON_WIDTH, Component.literal("›"), () -> searchPage.goToPage(1)));
            pageLabelWidget = addRenderableWidget(new GlassLabel(x + PAGE_BUTTON_WIDTH + 4, pagerTop,
                    Math.max(1, width - (PAGE_BUTTON_WIDTH + 4) * 2), PAGER_HEIGHT,
                    Component.empty(), GlassTheme.Text.VALUE, GlassTheme.TEXT_SECONDARY).centered());
            updatePager();
        }

        // 打开搜索页时直接把焦点交给输入框：这个窗口最主要的动作就是输入
        if (searchField != null) {
            setInitialFocus(searchField);
            refocusOnTick = true;
        }
    }

    /** 同步翻页栏：页码文案、两个按钮的可用状态与可见性。 */
    private void updatePager() {
        boolean show = top() == searchPage && searchPage.hasResults();
        boolean busy = searchPage.loading();
        if (prevPageButton != null) {
            prevPageButton.setVisible(show);
            prevPageButton.active = show && searchPage.page() > 1 && !busy;
        }
        if (nextPageButton != null) {
            nextPageButton.setVisible(show);
            nextPageButton.active = show && searchPage.canGoNext() && !busy;
        }
        if (pageLabelWidget != null) {
            pageLabelWidget.setVisible(show);
            pageLabelWidget.setMessage(show ? pageLabel() : Component.empty());
        }
    }

    private Component pageLabel() {
        if (!searchPage.hasResults()) {
            return Component.empty();
        }
        if (searchPage.total() > 0) {
            return Component.translatable("musicplayer.browser.page_total",
                    searchPage.page(), searchPage.total());
        }
        return Component.translatable("musicplayer.browser.page", searchPage.page());
    }

    // ── 交互 ────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        if (refocusOnTick) {
            refocusOnTick = false;
            // 原版 init() 收尾可能按 Tab 顺序把焦点挪走，下一 tick 再确认一次
            if (searchField != null) {
                setFocused(searchField);
            }
        }
        if (closing()) {
            return;
        }
        // 标题的显隐跟着滚动位置走，每 tick 同步一次就够了（20Hz，肉眼看不出来）
        updateTitle();
        super.tick();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Esc 交给原版：直接关掉整个窗口。返回上一页只走顶栏的返回按钮，
        // 免得想退出界面时被"一层一层退"卡住。
        boolean typing = searchField != null && searchField.isFocused();
        boolean enter = event.key() == InputConstants.KEY_RETURN
                || event.key() == InputConstants.KEY_NUMPADENTER
                || event.keycode() == InputConstants.KEYCODE_RETURN
                || event.keycode() == InputConstants.KEYCODE_NUMPADENTER;
        if (enter && typing) {
            searchPage.submit();
            return true;
        }
        // 不在输入框里时，方向键 / PgUp / PgDn / Home / End 直接滚列表：
        // 列表是窗口里唯一可滚的东西，不必先点一下才能用键盘
        if (!typing && top().view().keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }
}
