package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.client.ClientBgmController;
import com.junhsiun.musicplayer.client.ClientMusicUiState;
import com.junhsiun.musicplayer.client.ClientPlaylistStore;
import com.junhsiun.musicplayer.client.gui.animation.HeroElement;
import com.junhsiun.musicplayer.client.gui.animation.SharedElementTransition;
import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.FlatDivider;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassButton;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassColorPicker;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassIconButton;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassLabel;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassNavButton;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassSearchField;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassSlider;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassTabs;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassTextField;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassToggle;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import com.junhsiun.musicplayer.client.gui.glass.widget.ScrollPanel;
import com.junhsiun.musicplayer.client.gui.icon.SvgIcon;
import com.junhsiun.musicplayer.client.gui.icon.SvgIcons;
import com.junhsiun.musicplayer.client.gui.media.MediaListView;
import com.junhsiun.musicplayer.client.gui.window.GlassWindow;
import com.junhsiun.musicplayer.client.gui.window.GlassWindowScreen;
import com.junhsiun.musicplayer.client.gui.window.WindowAnchor;
import com.junhsiun.musicplayer.client.gui.window.WindowAnimations;
import com.junhsiun.musicplayer.client.gui.window.WindowBounds;
import com.junhsiun.musicplayer.client.gui.window.WindowSize;
import com.junhsiun.musicplayer.client.idle.IdleController;
import com.junhsiun.musicplayer.client.idle.IdleEffect;
import com.junhsiun.musicplayer.client.idle.IdleFilter;
import com.junhsiun.musicplayer.client.media.MediaBrowserService;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.model.MediaDetail;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.junhsiun.musicplayer.model.MediaStat;
import com.junhsiun.musicplayer.network.MusicConfigUpdatePayload;
import com.junhsiun.musicplayer.network.MusicUiRequestPayload;
import com.junhsiun.musicplayer.util.Messages;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * 音乐播放器的完整界面外壳。
 *
 * <p><b>结构：</b>左侧导航栏负责切换「搜索 / 当前队列 / 我的歌单 / 最近播放 / 设置」，
 * 右侧是页面卡片。页面本身仍然沿用 {@link BrowserPage} 的压栈模型：搜索与详情可以
 * 一层层进入，返回时数据与滚动位置都还在。</p>
 *
 * <p><b>播放控制放在哪：</b>底部播放栏已经移除，当前播放信息与上一首 / 播放暂停 / 下一首
 * 集中在「当前队列」页顶部的紧凑卡片里。按 {@code M} 打开窗口时默认就落在这个页面，
 * 打开即能看见当前状态并操作。</p>
 *
 * <p><b>为什么设置页单独处理：</b>设置页没有列表，而是一整页表单。它不参与
 * {@link BrowserPage} 的压栈，但仍占一个占位页，这样侧边栏切换、返回判断和标题逻辑
 * 都能保持同一套代码路径。</p>
 */
public final class MusicBrowserScreen extends GlassWindowScreen implements BrowserHost {

    // ── 整体尺寸 ────────────────────────────────────────────────────────
    // 窗口总尺寸保持 808×528 不变：侧边栏压到最简，内容区尽量大。
    private static final int WIDTH = 792;
    private static final int HEIGHT = 512;
    private static final int PADDING = 8;
    private static final int GAP = 6;
    private static final int SIDEBAR_WIDTH = 112;
    private static final int MAIN_PADDING = 14;
    private static final int PAGE_TOP = 14;

    // ── 页面外壳 ────────────────────────────────────────────────────────
    private static final int SEARCH_HEIGHT = 24;
    private static final int SEARCH_BUTTON_WIDTH = 56;
    private static final int TABS_HEIGHT = 24;
    private static final int TABS_GAP = 8;
    /** 顶栏（返回按钮 + 标题）的高度。 */
    private static final int BAR_HEIGHT = GlassTheme.Sizes.CONTROL;
    private static final int BACK_WIDTH = 46;
    private static final int BAR_GAP = 6;
    private static final int FAVORITE_SIZE = 20;
    private static final int NAV_HEIGHT = 28;

    // ── 设置页 ──────────────────────────────────────────────────────────
    private static final int SETTINGS_ROW_STEP = 30;
    private static final int SETTINGS_ROW_HEIGHT = 22;

    // ── 当前队列页 ──────────────────────────────────────────────────────
    private static final int QUEUE_TITLE_HEIGHT = 22;
    /** 标题与列表之间只留一点点呼吸空间：标题下面直接就是列表。 */
    private static final int QUEUE_SECTION_GAP = 6;
    /** 队列页可见时的刷新间隔（tick）。两秒一次足够，长队列不会频繁占带宽。 */
    private static final int QUEUE_POLL_INTERVAL = 40;

    private enum AppSection {
        SEARCH, QUEUE, PLAYLISTS, RECENT, SETTINGS
    }

    private final Deque<BrowserPage> stack = new ArrayDeque<>();
    private final SearchPage searchPage = new SearchPage();
    private final CurrentQueuePage currentQueuePage = new CurrentQueuePage();
    private final MyPlaylistsPage myPlaylistsPage = new MyPlaylistsPage();
    private final RecentlyPlayedPage recentlyPlayedPage = new RecentlyPlayedPage();
    private final SettingsPage settingsPage = new SettingsPage();

    /** 屏幕级的共享元素过渡：飞行中的封面/标题画在所有页面之上。 */
    private final SharedElementTransition hero = new SharedElementTransition();
    /** 下一次 {@link #buildPage} 要消费的源几何；只在一次 relayout 内有效。 */
    private HeroElement pendingHero;
    /** 当前正在显示的歌曲详情视图：返回时用它取反向动画的起点几何。 */
    private SongDetailView currentSongDetailView;
    /** 从哪一页的哪一条进入当前页 —— 返回时据此把共享元素送回原行。 */
    private final Map<BrowserPage, HeroReturn> heroReturns = new IdentityHashMap<>();

    /** 一次 Hero 跳转的返回信息。 */
    private record HeroReturn(BrowserPage originPage, String itemId) {
    }

    private AppSection section = AppSection.QUEUE;
    private int settingsTab;
    private int seenConfigRevision;
    private int seenRecentRevision;
    private int seenQueueRevision = -1;
    /** 上次刷新队列列表时的当前曲目 ID：切歌后需要把「正在播放」那一行换掉。 */
    private String seenQueueTrackId = "";
    private int queuePollTicks;
    private boolean settingsRelayoutPending;

    private GlassSearchField searchField;
    private GlassButton searchButton;
    private GlassTabs tabs;
    private GlassLabel titleLabel;
    private GlassIconButton favoriteButton;
    private boolean refocusOnTick;
    private boolean syncRequested;

    private GlassLabel queueRemainingLabel;

    /** 设置页滚动容器；只在构建设置页期间非空，作为表单控件的挂载目标。 */
    private ScrollPanel settingsScroll;
    /** 重建设置页时用来恢复滚动位置，避免切标签 / 配置刷新把用户弹回顶部。 */
    private double settingsScrollOffset;
    /** 下一次构建设置页时是否强制回到顶部（切换标签、重新进入设置页）。 */
    private boolean settingsScrollToTop;

    public MusicBrowserScreen() {
        super(new GlassWindow()
                .style(GlassTheme.windowStyle())
                .size(WindowSize.preferred(WIDTH + PADDING * 2, HEIGHT + PADDING * 2, 34))
                .anchor(WindowAnchor.CENTER)
                .contentPadding(PADDING, PADDING)
                .opening(WindowAnimations.riseFromBottom()));
        stack.push(currentQueuePage);
        currentQueuePage.attach(this);
    }

    // ── 页面宿主 ────────────────────────────────────────────────────────

    @Override
    public void push(BrowserPage page) {
        push(page, null);
    }

    /**
     * 压入一页；{@code origin} 非空且目标是单曲/通用详情页时，同时启动共享元素过渡。
     *
     * <p>顺序很重要：先记下 {@link #pendingHero}，再 {@link #relayoutContent()}，
     * 这样 {@link #buildPage} 里新建的详情控件能立刻拿到目标几何并开始动画。页面数据
     * （详情接口）是异步的，但详情页布局固定、歌单详情也先用列表带来的条目快照搭好头部，
     * 所以动画不必等网络。</p>
     */
    @Override
    public void push(BrowserPage page, HeroElement origin) {
        if (page == null) {
            return;
        }
        // 先作废上一页的在途请求，新页面 onEnter 发出的请求才会立刻拿到空闲线程。
        MediaBrowserService.getInstance().beginNavigation();
        cancelHero();
        BrowserPage originPage = top();
        page.attach(this);
        stack.push(page);
        boolean wantHero = origin != null
                && isOpeningFinished()
                && (page instanceof SongDetailPage || page instanceof DetailPage);
        if (wantHero) {
            // 记下"从哪页的哪一行进入"，返回时沿同一对元素反向飞回。
            heroReturns.put(page, new HeroReturn(originPage, origin.itemId()));
            pendingHero = origin;
            // 共享元素自己负责位移，列表再滑一下反而多一层运动，画面会显得拖沓。
            page.view().finishTransition();
        } else {
            page.view().startTransition(true);
        }
        relayoutContent();
        pendingHero = null;
        page.onEnter(this);
        // 上面的重建是同步的，可能吃掉开头几十毫秒；重建完再重新计时，动画起点才完整。
        hero.rebase();
    }

    @Override
    public void pop() {
        if (stack.size() <= 1) {
            return;
        }
        // 正在离开的详情页可能还有翻页/歌词请求在跑，返回时一并取消。
        MediaBrowserService.getInstance().beginNavigation();
        BrowserPage leaving = stack.peek();
        HeroReturn ret = heroReturns.remove(leaving);
        // 先抓走详情页当前封面/标题的几何，再拆控件——否则视图一销毁就取不到了。
        HeroElement reverseFrom = ret == null ? null : capturePageHero(leaving);
        cancelHero();
        stack.pop();
        leaving.onExit(this);
        BrowserPage next = top();
        if (reverseFrom != null) {
            // 反向过渡期间让上一页直接停在最终位置，只让封面/标题飞回去。
            next.view().finishTransition();
        } else {
            next.view().startTransition(false);
        }
        relayoutContent();
        next.onResume(this);
        if (reverseFrom != null) {
            HeroElement reverseTo = next.view().heroElementForId(ret.itemId());
            if (reverseTo != null) {
                hero.start(reverseFrom, reverseTo);
                next.view().setRowHero(ret.itemId(), hero);
            }
        }
    }

    /** 取某个详情页当前封面/标题的几何，作为反向过渡的起点。 */
    private HeroElement capturePageHero(BrowserPage page) {
        if (page instanceof SongDetailPage && currentSongDetailView != null) {
            return currentSongDetailView.heroTarget();
        }
        if (page instanceof DetailPage detailPage) {
            return detailPage.view().headerHeroElement(detailPage.id());
        }
        return null;
    }

    /** 中止共享元素动画并清掉所有页面上的隐藏状态。 */
    private void cancelHero() {
        hero.cancel();
        pendingHero = null;
        for (BrowserPage page : stack) {
            page.view().clearHero();
        }
    }

    @Override
    public boolean canGoBack() {
        return stack.size() > 1;
    }

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
        if (command != null) {
            sendCommand(command);
            queuePollTicks = QUEUE_POLL_INTERVAL - 1;
        }
    }

    @Override
    public void skip() {
        sendCommand("music skip");
        // 跳过后队列会很快变化，下一次 tick 立即把新队列拉回来。
        queuePollTicks = QUEUE_POLL_INTERVAL - 1;
    }

    /**
     * 把歌曲刻录到主手持有的唱片。
     *
     * <p>走聊天栏同一条命令链路，曲目解析、扣除唱片、写入 NBT 全部由服务端完成；
     * 界面只负责发 ID。点击瞬间再确认一次主手，避免列表画出来之后玩家已经换了物品。</p>
     */
    @Override
    public void burn(MediaItem item) {
        if (item == null || item.id().isBlank() || item.kind() != MediaKind.SONG) {
            return;
        }
        if (!holdsBurnableDisc()) {
            return;
        }
        sendCommand("music burn song " + item.id());
    }

    /** 主手是否拿着可刻录的唱片；刻录命令只认主手，界面据此决定是否显示刻录按钮。 */
    private static boolean holdsBurnableDisc() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && MusicDiscHelper.isBurnableDisc(minecraft.player.getMainHandItem());
    }

    /**
     * 每帧把「手上有没有唱片」同步给所有页面的列表。
     *
     * <p>按钮显示与否取决于玩家实时手持物，而列表控件在切页时会被复用，不能只在
     * {@code buildContent} 时配置一次。这里只改一个图标字段，不会重建控件、不影响滚动位置。</p>
     */
    private void syncDiscAction() {
        SvgIcon icon = holdsBurnableDisc() ? SvgIcons.DISC : null;
        for (BrowserPage page : stack) {
            page.view().setTrailingActionIcon(icon);
        }
    }

    @Override
    public void seekForward() {
        sendCommand("music seek 10");
    }

    @Override
    public void refreshChrome() {
        updateTitle();
        updateFavoriteButton();
    }

    private void openSection(AppSection next) {
        if (next == null) {
            return;
        }
        MediaBrowserService.getInstance().beginNavigation();
        cancelHero();
        heroReturns.clear();
        section = next;
        if (next != AppSection.SETTINGS) {
            settingsScrollOffset = 0.0D;
            settingsScrollToTop = true;
        }
        stack.clear();
        BrowserPage root = switch (next) {
            case SEARCH -> searchPage;
            case QUEUE -> currentQueuePage;
            case PLAYLISTS -> myPlaylistsPage;
            case RECENT -> recentlyPlayedPage;
            case SETTINGS -> settingsPage;
        };
        root.attach(this);
        stack.push(root);
        root.view().startTransition(true);
        relayoutContent();
        root.onEnter(this);
        if (next == AppSection.SETTINGS) {
            requestSync();
        } else if (next == AppSection.QUEUE) {
            queuePollTicks = 0;
            requestQueue();
        }
    }

    private BrowserPage top() {
        BrowserPage page = stack.peek();
        return page == null ? currentQueuePage : page;
    }

    // ── 布局 ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // 窗口重建/缩放后，旧动画里的坐标全部失效，必须丢弃。
        cancelHero();
        heroReturns.clear();
        currentSongDetailView = null;
        super.init();
        if (!syncRequested) {
            syncRequested = true;
            requestSync();
        }
    }

    /**
     * 先让基类把玻璃、页面控件全部提交完，再在最上层画飞行中的共享元素。
     *
     * <p>必须放在 {@code super} 之后：GUI 渲染是按提交顺序分层的，晚提交的元素才会
     * 盖住页面内容与行尾按钮；也不能在页面内部画，否则会被列表的 scissor 裁掉。</p>
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 先锁住本帧时间：页面控件与最上层 overlay 必须用同一个时间点判断「还在不在飞」，
        // 否则两者对动画结束的判断可能相差一毫秒，出现封面谁也不画的一帧（就是那下闪）。
        hero.beginFrame();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (hero.isActive()) {
            // 页面控件各自套 scissor；万一某一帧有控件在中途返回留下裁剪区，
            // 最上层的共享元素也会被裁掉。画之前先清一次，保证它总是完整可见。
            GlassWidgets.resetClip();
            hero.draw(graphics, Minecraft.getInstance().font);
        }
    }

    @Override
    protected void buildContent(WindowBounds content) {
        window().style(GlassTheme.windowStyle());
        // 不要在这里调用 GlassPanel.prewarm：它会 invalidate 背景贴图，而页面跳转、
        // 切换侧边栏同样会重跑 buildContent。贴图一旦失效，下一帧玻璃会整扇跳过，
        // 看起来就是窗口闪了一下。预热只在真正打开界面时由 GlassWindowScreen.open 做一次。

        // 控件马上整体重建：设置页如果正开着，先记住滚动位置，重建后再还原。
        captureSettingsScroll();
        settingsScroll = null;

        searchField = null;
        searchButton = null;
        tabs = null;
        titleLabel = null;
        favoriteButton = null;
        queueRemainingLabel = null;
        settingsScroll = null;
        currentSongDetailView = null;

        Shell shell = shell(content);
        buildSidebar(shell.sidebar());
        addRenderableWidget(FlatDivider.vertical(shell.sidebar().x1(),
                shell.sidebar().y0() + 18, shell.sidebar().height() - 36));

        BrowserPage page = top();
        if (page instanceof SettingsPage) {
            buildSettings(shell.main());
        } else if (page instanceof CurrentQueuePage) {
            buildQueuePage(page, shell.main());
        } else {
            buildPage(page, shell.main());
        }

        if (searchField != null) {
            setInitialFocus(searchField);
            refocusOnTick = true;
        }
    }

    private static Shell shell(WindowBounds content) {
        int sidebarRight = content.x0() + SIDEBAR_WIDTH;
        WindowBounds sidebar = new WindowBounds(content.x0(), content.y0(),
                sidebarRight, content.y1());
        int mainLeft = sidebarRight + 1 + MAIN_PADDING;
        int mainRight = content.x1() - MAIN_PADDING;
        // 底部播放栏移除后，主内容区一直铺到窗口内边距处，垂直空间全部留给页面。
        WindowBounds main = new WindowBounds(mainLeft, content.y0() + PAGE_TOP,
                mainRight, content.y1() - MAIN_PADDING);
        return new Shell(sidebar, main);
    }

    private void buildSidebar(WindowBounds sidebar) {
        int x = sidebar.x0() + 12;
        int y = sidebar.y0() + 16;
        int width = sidebar.width() - 24;

        addRenderableWidget(new GlassLabel(x, y, width, 16,
                Component.translatable("musicplayer.app.title"),
                0.92F, FlatTheme.TEXT));
        addRenderableWidget(new GlassLabel(x, y + 17, width, 10,
                Component.translatable("musicplayer.app.subtitle"),
                0.55F, FlatTheme.TEXT_TERTIARY));
        addRenderableWidget(FlatDivider.horizontal(x, y + 38, width));

        int navY = y + 52;
        GlassNavButton search = addRenderableWidget(new GlassNavButton(x, navY, width, NAV_HEIGHT,
                SvgIcons.SEARCH, Component.translatable("musicplayer.app.search"),
                () -> openSection(AppSection.SEARCH)));
        GlassNavButton queue = addRenderableWidget(new GlassNavButton(x, navY + NAV_HEIGHT + 2,
                width, NAV_HEIGHT, SvgIcons.QUEUE,
                Component.translatable("musicplayer.app.queue"),
                () -> openSection(AppSection.QUEUE)));
        GlassNavButton playlists = addRenderableWidget(new GlassNavButton(x, navY + (NAV_HEIGHT + 2) * 2,
                width, NAV_HEIGHT, SvgIcons.PLAYLIST,
                Component.translatable("musicplayer.app.my_playlists"),
                () -> openSection(AppSection.PLAYLISTS)));
        GlassNavButton recent = addRenderableWidget(new GlassNavButton(x, navY + (NAV_HEIGHT + 2) * 3,
                width, NAV_HEIGHT, SvgIcons.HISTORY,
                Component.translatable("musicplayer.app.recent"),
                () -> openSection(AppSection.RECENT)));
        GlassNavButton settings = addRenderableWidget(new GlassNavButton(x,
                sidebar.y1() - 20 - NAV_HEIGHT, width, NAV_HEIGHT, SvgIcons.SETTINGS,
                Component.translatable("musicplayer.settings.title"),
                () -> openSection(AppSection.SETTINGS)));

        search.setSelected(section == AppSection.SEARCH);
        queue.setSelected(section == AppSection.QUEUE);
        playlists.setSelected(section == AppSection.PLAYLISTS);
        recent.setSelected(section == AppSection.RECENT);
        settings.setSelected(section == AppSection.SETTINGS);
    }

    private void buildPage(BrowserPage page, WindowBounds inner) {
        EnumSet<PageChrome> chrome = page.chrome();
        int x = inner.x0();
        int top = inner.y0();
        int width = inner.width();

        if (chrome.contains(PageChrome.BACK_BAR)) {
            addRenderableWidget(GlassButton.regular(x, top, BACK_WIDTH,
                    Component.translatable("musicplayer.browser.back"), this::pop));
            boolean favorite = page instanceof DetailPage detailPage
                    && detailPage.kind() == MediaKind.PLAYLIST;
            int reserved = favorite ? FAVORITE_SIZE + GAP : 0;
            titleLabel = addRenderableWidget(new GlassLabel(x + BACK_WIDTH + 10, top,
                    Math.max(1, width - BACK_WIDTH - 10 - reserved), BAR_HEIGHT,
                    page.title(), 0.88F, FlatTheme.TEXT));
            if (favorite) {
                favoriteButton = addRenderableWidget(GlassIconButton.regular(
                        x + width - FAVORITE_SIZE, top - (FAVORITE_SIZE - BAR_HEIGHT) / 2,
                        FAVORITE_SIZE, SvgIcons.STAR,
                        Component.translatable("musicplayer.browser.favorite"), () -> {
                            if (top() instanceof DetailPage detailPage) {
                                toggleFavorite(detailPage);
                            }
                        }).glass());
            }
            top += BAR_HEIGHT + BAR_GAP;
        }

        if (chrome.contains(PageChrome.TITLE_BAR)) {
            titleLabel = addRenderableWidget(new GlassLabel(x, top, width, 22,
                    page.title(), 0.95F, FlatTheme.TEXT));
            top += 22 + BAR_GAP + 2;
        }

        if (chrome.contains(PageChrome.SEARCH_BAR)) {
            int fieldWidth = Math.max(60, width - GAP - SEARCH_BUTTON_WIDTH);
            searchField = addRenderableWidget(GlassSearchField.create(x, top, fieldWidth, SEARCH_HEIGHT,
                    Component.translatable("musicplayer.browser.search_hint"),
                    value -> searchPage.setKeyword(value)));
            searchField.setValue(searchPage.keyword());
            searchButton = addRenderableWidget(new GlassButton(
                    x + width - SEARCH_BUTTON_WIDTH, top, SEARCH_BUTTON_WIDTH, SEARCH_HEIGHT,
                    Component.translatable("musicplayer.browser.search"),
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

        int listHeight = Math.max(MediaListView.ROW_HEIGHT * 3, inner.y1() - top);
        if (page instanceof SongDetailPage songPage) {
            SongDetailView detailView = new SongDetailView(songPage, x, top, width, listHeight);
            currentSongDetailView = detailView;
            if (pendingHero != null) {
                HeroElement target = detailView.heroTarget();
                if (target != null) {
                    hero.start(pendingHero, target);
                    detailView.setHero(hero);
                }
            }
            addRenderableWidget(detailView);
            updateTitle();
            updateFavoriteButton();
            return;
        }
        MediaListView view = page.view();
        view.setRectangle(width, listHeight, x, top);
        // 主手拿着可刻录唱片时，行尾最右侧多一个「刻录」按钮；换手由 tick 每帧同步。
        view.setTrailingActionIcon(holdsBurnableDisc() ? SvgIcons.DISC : null);
        addRenderableWidget(view);
        page.apply();

        // 歌单/歌手等详情：目标在列表头部区块里，必须等 apply() 把头部数据放进去之后
        // 才能算出封面/标题的落点。
        if (pendingHero != null && page instanceof DetailPage detailPage) {
            HeroElement target = view.headerHeroElement(detailPage.id());
            if (target != null) {
                hero.start(pendingHero, target);
                view.setHeaderHero(hero);
            }
        }

        updateTitle();
        updateFavoriteButton();
    }

    private void updateTitle() {
        if (titleLabel == null) {
            return;
        }
        BrowserPage page = top();
        if (page.chrome().contains(PageChrome.TITLE_BAR) || page instanceof SongDetailPage) {
            titleLabel.setMessage(page.title());
            return;
        }
        titleLabel.setMessage(page.view().headerScrolledAway()
                ? page.title()
                : Component.empty());
    }

    // ── 收藏 ────────────────────────────────────────────────────────────

    private void toggleFavorite(DetailPage page) {
        MediaDetail detail = page.detail();
        if (detail == null || page.id().isBlank()) {
            return;
        }
        ClientPlaylistStore.getInstance().toggle(new ClientPlaylistStore.SavedPlaylist(
                page.id(), detail.title(), detail.subtitle(), detail.coverUrl(),
                trackCount(detail), System.currentTimeMillis()));
        updateFavoriteButton();
    }

    private void updateFavoriteButton() {
        if (favoriteButton == null) {
            return;
        }
        boolean show = top() instanceof DetailPage page && page.kind() == MediaKind.PLAYLIST;
        favoriteButton.visible = show;
        if (!show) {
            return;
        }
        DetailPage page = (DetailPage) top();
        favoriteButton.active = page.detail() != null;
        favoriteButton.setSelected(page.detail() != null
                && ClientPlaylistStore.getInstance().contains(page.id()));
    }

    private static int trackCount(MediaDetail detail) {
        String label = Component.translatable("musicplayer.browser.stat.tracks").getString();
        for (MediaStat stat : detail.stats()) {
            if (!label.equals(stat.label().getString())) {
                continue;
            }
            try {
                return Integer.parseInt(stat.value().getString().trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    // ── 设置页 ──────────────────────────────────────────────────────────

    private void buildSettings(WindowBounds inner) {
        int x = inner.x0();
        int y = inner.y0();
        int width = inner.width();

        boolean canEdit = ClientMusicUiState.getInstance().canEditConfig();
        List<Component> labels = new ArrayList<>(List.of(
                Component.translatable("musicplayer.settings.tab.client"),
                Component.translatable("musicplayer.settings.tab.appearance")));
        if (canEdit) {
            labels.add(Component.translatable("musicplayer.settings.tab.admin"));
        }
        // 权限可能在界面打开期间变化：把越界的标签号收回最后一个可见标签，
        // 否则会停留在已经不存在（或无权查看）的管理页上。
        settingsTab = Math.max(0, Math.min(settingsTab, labels.size() - 1));

        GlassTabs settingsTabs = addRenderableWidget(new GlassTabs(x, y, width, TABS_HEIGHT,
                labels, settingsTab));
        settingsTabs.onSelect(index -> {
            if (settingsTab == index) {
                return;
            }
            settingsTab = index;
            // 换标签是换一整页内容，滚动位置应该回到顶部。
            settingsScrollToTop = true;
            settingsRelayoutPending = true;
        });

        int top = y + TABS_HEIGHT + TABS_GAP;
        // 标签栏固定，下面的表单放进滚动容器：窗口在小屏上收缩、或以后继续加设置项时，
        // 内容只会滚动，不会画到玻璃窗口外面。
        ScrollPanel panel = new ScrollPanel(x, top, width, Math.max(1, inner.y1() - top));
        addRenderableWidget(panel);
        settingsScroll = panel;

        switch (settingsTab) {
            case 0 -> buildClientSettings(x, top, width);
            case 1 -> buildAppearanceSettings(x, top, width);
            case 2 -> {
                if (canEdit) {
                    buildAdminSettings(x, top, width, true);
                }
            }
            default -> {
            }
        }

        panel.finish();
        panel.scrollTo(settingsScrollToTop ? 0.0D : settingsScrollOffset);
        settingsScrollToTop = false;
    }

    /** 重建前把当前滚动位置记下来，下一次构建设置页时恢复。 */
    private void captureSettingsScroll() {
        if (settingsScroll != null && !settingsScrollToTop) {
            settingsScrollOffset = settingsScroll.position();
        }
    }

    /**
     * 表单控件挂载点：构建设置页时挂到滚动容器里，其余情况直接挂到界面。
     *
     * <p>设置页的每个 add*Row 都通过这里，这样表单排版代码完全不用关心自己是在
     * 滚动容器里还是在普通页面里。</p>
     */
    @SuppressWarnings("unchecked")
    private <T extends AbstractWidget> T addSettingWidget(T widget) {
        if (settingsScroll != null) {
            settingsScroll.add(widget);
            return widget;
        }
        return addRenderableWidget((T) widget);
    }

    private void buildClientSettings(int x, int y, int width) {
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        int rowY = y;
        addSliderRow(x, rowY, width, "musicplayer.settings.bgm_volume", 0.0D, 1.0D,
                config.client.bgmVolume, this::formatPercent,
                value -> {
                    config.client.bgmVolume = value;
                    MusicPlayerConfigManager.markDirty();
                },
                value -> MusicPlayerConfigManager.save());
        rowY += SETTINGS_ROW_STEP;
        addToggleRow(x, rowY, width, "musicplayer.settings.network_bgm",
                config.client.enableNetworkBgm, true, value -> {
                    config.client.enableNetworkBgm = value;
                    MusicPlayerConfigManager.save();
                    ClientBgmController.getInstance().onGlobalToggleChanged();
                });
        rowY += SETTINGS_ROW_STEP + 8;

        // 挂机纯享也属于纯客户端行为，直接并在这里，避免为它单开一个标签页。
        rowY = addSectionHeader(x, rowY, width, "musicplayer.settings.section.immersive",
                Component.translatable("musicplayer.settings.idle_preview"), () -> {
                    IdleController.getInstance().requestPreview();
                    onClose();
                });
        rowY = buildIdleSettings(x, rowY, width);

        rowY += SETTINGS_ROW_STEP + 8;
        addSettingWidget(GlassButton.regular(x, rowY, width,
                Component.translatable("musicplayer.settings.reset_defaults"), () -> {
                    MusicPlayerConfigManager.resetClient();
                    MusicPlayerConfigManager.resetIdle();
                    ClientBgmController.getInstance().onGlobalToggleChanged();
                    settingsRelayoutPending = true;
                }));
    }

    private void buildAppearanceSettings(int x, int y, int width) {
        MusicPlayerConfig.GlassSettings glass = MusicPlayerConfigManager.get().glass;
        int rowY = y;
        addThemeColorRow(x, rowY, width);
        rowY += SETTINGS_ROW_STEP;
        addSliderRow(x, rowY, width, "musicplayer.settings.glass_opacity", 0.0D, 1.0D,
                glass.opacity, this::formatPercent,
                value -> {
                    glass.opacity = value;
                    MusicPlayerConfigManager.markDirty();
                },
                value -> MusicPlayerConfigManager.save());
        rowY += SETTINGS_ROW_STEP;
        addSliderRow(x, rowY, width, "musicplayer.settings.glass_blur", 0.0D, 1.0D,
                glass.blur, this::formatPercent,
                value -> {
                    glass.blur = value;
                    MusicPlayerConfigManager.markDirty();
                },
                value -> MusicPlayerConfigManager.save());
        rowY += SETTINGS_ROW_STEP;
        addToggleRow(x, rowY, width, "musicplayer.settings.glass_shadow", glass.shadow, true, value -> {
            glass.shadow = value;
            MusicPlayerConfigManager.save();
        });
        rowY += SETTINGS_ROW_STEP;
        addSettingWidget(GlassButton.regular(x, rowY, width,
                Component.translatable("musicplayer.settings.reset_defaults"), () -> {
                    MusicPlayerConfigManager.resetGlass();
                    settingsRelayoutPending = true;
                }));
    }

    /** 主题色一行：左边名称，右边一排预设色块。 */
    private void addThemeColorRow(int x, int y, int width) {
        MusicPlayerConfig.GlassSettings glass = MusicPlayerConfigManager.get().glass;
        int controlWidth = controlWidth(width);
        int labelWidth = Math.max(1, width - controlWidth - 12);
        addSettingWidget(new GlassLabel(x, y, labelWidth, SETTINGS_ROW_HEIGHT,
                Component.translatable("musicplayer.settings.theme_color"),
                GlassTheme.Text.VALUE, FlatTheme.TEXT_SECONDARY));
        GlassColorPicker picker = new GlassColorPicker(x + width - controlWidth, y + 2,
                controlWidth, SETTINGS_ROW_HEIGHT, glass.themeColor, value -> {
                    glass.themeColor = value;
                    MusicPlayerConfigManager.save();
                });
        addSettingWidget(picker);
    }

    /**
     * 挂机纯享的表单内容。
     *
     * <p>这些设置属于纯客户端行为，直接嵌在客户端标签页里（见
     * {@link #buildClientSettings}），不再单独占一个标签页。返回下一行的起点。</p>
     */
    private int buildIdleSettings(int x, int y, int width) {
        MusicPlayerConfig.IdleSettings idle = MusicPlayerConfigManager.get().idle;
        int rowY = y;
        addToggleRow(x, rowY, width, "musicplayer.settings.idle_enabled",
                idle.enabled, true, value -> {
                    idle.enabled = value;
                    MusicPlayerConfigManager.save();
                    if (!value) {
                        IdleController.getInstance().reset();
                    }
                });
        rowY += SETTINGS_ROW_STEP;
        addIdleEffectRow(x, rowY, width, idle);
        rowY += SETTINGS_ROW_STEP;
        addIdleFilterRow(x, rowY, width, idle);
        rowY += SETTINGS_ROW_STEP;
        addSliderRow(x, rowY, width, "musicplayer.settings.idle_delay",
                MusicPlayerConfig.IdleSettings.MIN_DELAY_SECONDS,
                MusicPlayerConfig.IdleSettings.MAX_DELAY_SECONDS,
                idle.delaySeconds, value -> formatInteger(value) + "s",
                value -> {
                    idle.delaySeconds = (int) Math.round(value);
                    MusicPlayerConfigManager.markDirty();
                },
                value -> MusicPlayerConfigManager.save());
        rowY += SETTINGS_ROW_STEP;
        addSliderRow(x, rowY, width, "musicplayer.settings.idle_intensity", 0.2D, 1.6D,
                idle.intensity, value -> String.format(Locale.ROOT, "%.2fx", value),
                value -> {
                    idle.intensity = value;
                    MusicPlayerConfigManager.markDirty();
                },
                value -> MusicPlayerConfigManager.save());
        rowY += SETTINGS_ROW_STEP;
        addSliderRow(x, rowY, width, "musicplayer.settings.idle_lyric_scale", 0.6D, 1.6D,
                idle.lyricScale, value -> String.format(Locale.ROOT, "%.2fx", value),
                value -> {
                    idle.lyricScale = value;
                    MusicPlayerConfigManager.markDirty();
                },
                value -> MusicPlayerConfigManager.save());
        rowY += SETTINGS_ROW_STEP;
        addToggleRow(x, rowY, width, "musicplayer.settings.idle_show_lyrics",
                idle.showLyrics, true, value -> {
                    idle.showLyrics = value;
                    MusicPlayerConfigManager.save();
                });
        return rowY + SETTINGS_ROW_STEP;
    }

    /** 特效预设选择：与 {@link IdleEffect} 的枚举顺序一一对应。 */
    private void addIdleEffectRow(int x, int y, int width, MusicPlayerConfig.IdleSettings idle) {
        int controlWidth = controlWidth(width);
        int labelWidth = Math.max(1, width - controlWidth - 12);
        addSettingWidget(new GlassLabel(x, y, labelWidth, SETTINGS_ROW_HEIGHT,
                Component.translatable("musicplayer.settings.idle_effect"),
                GlassTheme.Text.VALUE, FlatTheme.TEXT_SECONDARY));

        IdleEffect[] effects = IdleEffect.values();
        List<Component> labels = new ArrayList<>(effects.length);
        for (IdleEffect effect : effects) {
            labels.add(Component.translatable(effect.translationKey()));
        }
        int selected = IdleEffect.indexOf(IdleEffect.of(idle.effect));
        GlassTabs tabs = new GlassTabs(x + width - controlWidth, y + 2,
                controlWidth, 18, labels, selected);
        tabs.onSelect(index -> {
            if (index < 0 || index >= effects.length) {
                return;
            }
            idle.effect = effects[index].key();
            MusicPlayerConfigManager.save();
        });
        addSettingWidget(tabs);
    }

    /** 背景滤镜选择：与 {@link IdleFilter} 的枚举顺序一一对应。 */
    private void addIdleFilterRow(int x, int y, int width, MusicPlayerConfig.IdleSettings idle) {
        int controlWidth = controlWidth(width);
        int labelWidth = Math.max(1, width - controlWidth - 12);
        addSettingWidget(new GlassLabel(x, y, labelWidth, SETTINGS_ROW_HEIGHT,
                Component.translatable("musicplayer.settings.idle_filter"),
                GlassTheme.Text.VALUE, FlatTheme.TEXT_SECONDARY));

        IdleFilter[] filters = IdleFilter.values();
        List<Component> labels = new ArrayList<>(filters.length);
        for (IdleFilter filter : filters) {
            labels.add(Component.translatable(filter.translationKey()));
        }
        int selected = IdleFilter.indexOf(IdleFilter.of(idle.filter));
        GlassTabs tabs = new GlassTabs(x + width - controlWidth, y + 2,
                controlWidth, 18, labels, selected);
        tabs.onSelect(index -> {
            if (index < 0 || index >= filters.length) {
                return;
            }
            idle.filter = filters[index].key();
            MusicPlayerConfigManager.save();
        });
        addSettingWidget(tabs);
    }

    /**
     * 管理设置：所有需要服务端权限的项集中在这里。
     *
     * <p>没有权限的玩家连这个标签都不会出现（见 {@link #buildSettings(WindowBounds)}），
     * 因此这里的控件始终是可编辑的。</p>
     */
    private void buildAdminSettings(int x, int y, int width, boolean canEdit) {
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        int rowY = addSectionHeader(x, y, width, "musicplayer.settings.section.playback");

        addServerToggle(x, rowY, width, "musicplayer.settings.auto_advance", "autoAdvance",
                serverBool("autoAdvance", config.autoAdvance), canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerToggle(x, rowY, width, "musicplayer.settings.show_loading_hints", "showLoadingHints",
                serverBool("showLoadingHints", config.showLoadingHints), canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerToggle(x, rowY, width, "musicplayer.settings.show_lyrics", "showLyrics",
                serverBool("showLyrics", config.showLyrics), canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerToggle(x, rowY, width, "musicplayer.settings.announce_queue", "announceQueueChanges",
                serverBool("announceQueueChanges", config.announceQueueChanges), canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerToggle(x, rowY, width, "musicplayer.settings.allow_song_request", "allowSongRequest",
                serverBool("allowSongRequest", config.allowSongRequest), canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerToggle(x, rowY, width, "musicplayer.settings.allow_playlist_request", "allowPlaylistRequest",
                serverBool("allowPlaylistRequest", config.allowPlaylistRequest), canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerSlider(x, rowY, width, "musicplayer.settings.search_limit", "searchLimit",
                3.0D, 20.0D, serverInt("searchLimit", config.searchLimit), true, canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerSlider(x, rowY, width, "musicplayer.settings.max_queue", "maxQueueSize",
                1.0D, 200.0D, serverInt("maxQueueSize", config.maxQueueSize), true, canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerSlider(x, rowY, width, "musicplayer.settings.max_songs_per_player", "maxSongsPerPlayer",
                1.0D, 50.0D, serverInt("maxSongsPerPlayer", config.maxSongsPerPlayer), true, canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerSlider(x, rowY, width, "musicplayer.settings.playlist_queue_limit", "playlistQueueLimit",
                1.0D, 100.0D, serverInt("playlistQueueLimit", config.playlistQueueLimit), true, canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerSlider(x, rowY, width, "musicplayer.settings.queue_cache", "queueCacheSize",
                0.0D, 20.0D, serverInt("queueCacheSize", config.queueCacheSize), true, canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerSlider(x, rowY, width, "musicplayer.settings.vote_skip", "voteSkipPercent",
                0.1D, 1.0D, serverDouble("voteSkipPercent", config.voteSkipPercent), false,
                canEdit, this::formatPercent);
        rowY += SETTINGS_ROW_STEP;
        addServerToggle(x, rowY, width, "musicplayer.settings.loot_discs", "enableLootMusicDiscs",
                serverBool("enableLootMusicDiscs", config.enableLootMusicDiscs), canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerSlider(x, rowY, width, "musicplayer.settings.loot_chance", "lootMusicDiscChance",
                0.0D, 1.0D, serverDouble("lootMusicDiscChance", config.lootMusicDiscChance), false,
                canEdit, this::formatPercent);
        rowY += SETTINGS_ROW_STEP;
        addServerSlider(x, rowY, width, "musicplayer.settings.loot_count", "lootMusicDiscCount",
                0.0D, 5.0D, serverInt("lootMusicDiscCount", config.lootMusicDiscCount), true, canEdit);

        rowY += SETTINGS_ROW_STEP + 8;
        rowY = addSectionHeader(x, rowY, width, "musicplayer.settings.section.network");

        addTextFieldRow(x, rowY, width, "musicplayer.settings.base_url",
                serverRaw("neteaseBaseUrl", config.neteaseBaseUrl), canEdit,
                "musicplayer.settings.hint.base_url", text -> sendConfig("neteaseBaseUrl", text));
        rowY += SETTINGS_ROW_STEP;
        addApiModeRow(x, rowY, width, serverRaw("apiMode", config.apiMode), canEdit);
        rowY += SETTINGS_ROW_STEP;
        addTextFieldRow(x, rowY, width, "musicplayer.settings.bgm_playlist",
                serverRaw("bgmPlaylistId", config.bgmPlaylistId), canEdit,
                "musicplayer.settings.hint.bgm_playlist", text -> sendConfig("bgmPlaylistId", text));
        rowY += SETTINGS_ROW_STEP;
        addTextFieldRow(x, rowY, width, "musicplayer.settings.proxy",
                serverRaw("proxy", config.proxy), canEdit,
                "musicplayer.settings.hint.proxy", text -> sendConfig("proxy", text));
        rowY += SETTINGS_ROW_STEP;
        addServerToggle(x, rowY, width, "musicplayer.settings.use_system_proxy", "useSystemProxy",
                serverBool("useSystemProxy", config.useSystemProxy), canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerToggle(x, rowY, width, "musicplayer.settings.prefer_ipv4", "preferIpv4",
                serverBool("preferIpv4", config.preferIpv4), canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerToggle(x, rowY, width, "musicplayer.settings.allow_custom_server", "allowCustomServer",
                serverBool("allowCustomServer", config.allowCustomServer), canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerSlider(x, rowY, width, "musicplayer.settings.connect_timeout", "connectTimeoutSeconds",
                3.0D, 60.0D, serverInt("connectTimeoutSeconds", config.connectTimeoutSeconds), true, canEdit);
        rowY += SETTINGS_ROW_STEP;
        addServerSlider(x, rowY, width, "musicplayer.settings.read_timeout", "readTimeoutSeconds",
                3.0D, 120.0D, serverInt("readTimeoutSeconds", config.readTimeoutSeconds), true, canEdit);

        rowY += SETTINGS_ROW_STEP + 8;
        addSettingWidget(GlassButton.regular(x, rowY, width,
                Component.translatable("musicplayer.settings.reset_defaults"),
                () -> sendConfig("resetServerDefaults", "true")));
    }

    /** 管理页的分组标题；返回下一行的起点。 */
    private int addSectionHeader(int x, int y, int width, String labelKey) {
        addSettingWidget(new GlassLabel(x, y + 3, width, 14,
                Component.translatable(labelKey), GlassTheme.Text.LABEL, FlatTheme.TEXT_TERTIARY));
        return y + SETTINGS_ROW_STEP;
    }

    /** 右侧带操作按钮的分组标题；按钮与标题同一行，返回下一行的起点。 */
    private int addSectionHeader(int x, int y, int width, String labelKey,
                                 Component actionLabel, Runnable action) {
        int buttonWidth = Math.min(64, Math.max(48, Math.round(width * 0.2F)));
        int labelWidth = Math.max(1, width - buttonWidth - 12);
        addSettingWidget(new GlassLabel(x, y + 3, labelWidth, 14,
                Component.translatable(labelKey), GlassTheme.Text.LABEL, FlatTheme.TEXT_TERTIARY));
        addSettingWidget(GlassButton.regular(x + width - buttonWidth, y, buttonWidth,
                actionLabel, action));
        return y + SETTINGS_ROW_STEP;
    }

    // ── 设置控件 ────────────────────────────────────────────────────────

    private void addServerToggle(int x, int y, int width, String labelKey, String key,
                                 boolean value, boolean enabled) {
        addToggleRow(x, y, width, labelKey, value, enabled,
                next -> sendConfig(key, Boolean.toString(next)));
    }

    private void addServerSlider(int x, int y, int width, String labelKey, String key,
                                 double min, double max, double value, boolean integer,
                                 boolean enabled) {
        addServerSlider(x, y, width, labelKey, key, min, max, value, integer, enabled,
                next -> integer ? formatInteger(next) : formatTwoDecimals(next));
    }

    private void addServerSlider(int x, int y, int width, String labelKey, String key,
                                 double min, double max, double value, boolean integer,
                                 boolean enabled, DoubleFunction<String> format) {
        addSliderRow(x, y, width, labelKey, min, max, value, format,
                enabled ? next -> ClientMusicUiState.getInstance()
                        .setConfigValue(key, rawValue(next, integer)) : null,
                enabled ? next -> sendConfig(key, rawValue(next, integer)) : null,
                enabled);
    }

    private void addApiModeRow(int x, int y, int width, String current, boolean enabled) {
        int controlWidth = controlWidth(width);
        int labelWidth = Math.max(1, width - controlWidth - 12);
        int controlX = x + width - controlWidth;
        addSettingWidget(new GlassLabel(x, y, labelWidth, SETTINGS_ROW_HEIGHT,
                Component.translatable("musicplayer.settings.api_mode"),
                GlassTheme.Text.VALUE, FlatTheme.TEXT_SECONDARY));

        List<Component> labels = List.of(
                Component.translatable("musicplayer.settings.api_mode.auto"),
                Component.translatable("musicplayer.settings.api_mode.direct"),
                Component.translatable("musicplayer.settings.api_mode.proxy"));
        String normalized = current == null ? "auto" : current.trim().toLowerCase(Locale.ROOT);
        int selected = switch (normalized) {
            case "direct" -> 1;
            case "proxy" -> 2;
            default -> 0;
        };
        GlassTabs modes = new GlassTabs(controlX, y + 2, controlWidth, 18, labels, selected);
        modes.active = enabled;
        modes.onSelect(index -> {
            if (!enabled) {
                return;
            }
            String value = switch (index) {
                case 1 -> "direct";
                case 2 -> "proxy";
                default -> "auto";
            };
            sendConfig("apiMode", value);
        });
        addSettingWidget(modes);
    }

    private void addSliderRow(int x, int y, int width, String labelKey, double min, double max,
                              double current, DoubleFunction<String> format,
                              DoubleConsumer onChange, DoubleConsumer onCommit) {
        addSliderRow(x, y, width, labelKey, min, max, current, format, onChange, onCommit, true);
    }

    private void addSliderRow(int x, int y, int width, String labelKey, double min, double max,
                              double current, DoubleFunction<String> format,
                              DoubleConsumer onChange, DoubleConsumer onCommit, boolean enabled) {
        int controlWidth = controlWidth(width);
        int valueWidth = 44;
        int sliderWidth = Math.max(60, controlWidth - valueWidth - 6);
        int labelWidth = Math.max(1, width - controlWidth - 12);
        int controlX = x + width - controlWidth;

        addSettingWidget(new GlassLabel(x, y, labelWidth, SETTINGS_ROW_HEIGHT,
                Component.translatable(labelKey), GlassTheme.Text.VALUE, FlatTheme.TEXT_SECONDARY));
        GlassLabel valueLabel = addSettingWidget(new GlassLabel(controlX + sliderWidth + 6, y,
                valueWidth, SETTINGS_ROW_HEIGHT, Component.literal(format.apply(current)),
                GlassTheme.Text.VALUE, FlatTheme.TEXT));
        valueLabel.centered();

        double[] latest = {current};
        GlassSlider slider = new GlassSlider(controlX, y + 3, sliderWidth, GlassTheme.Sizes.CONTROL,
                normalize(current, min, max), value -> {
                    double actual = denormalize(value, min, max);
                    latest[0] = actual;
                    valueLabel.setMessage(Component.literal(format.apply(actual)));
                    if (onChange != null) {
                        onChange.accept(actual);
                    }
                }, null);
        slider.onCommit(() -> {
            if (onCommit != null) {
                onCommit.accept(latest[0]);
            }
        });
        slider.active = enabled;
        addSettingWidget(slider);
    }

    private void addToggleRow(int x, int y, int width, String labelKey, boolean value,
                              boolean enabled, Consumer<Boolean> onChange) {
        int labelWidth = Math.max(1, width - GlassTheme.Sizes.TOGGLE_WIDTH - 12);
        addSettingWidget(new GlassLabel(x, y, labelWidth, SETTINGS_ROW_HEIGHT,
                Component.translatable(labelKey), GlassTheme.Text.VALUE, FlatTheme.TEXT_SECONDARY));
        GlassToggle toggle = new GlassToggle(x + width - GlassTheme.Sizes.TOGGLE_WIDTH,
                y + 2, value, onChange);
        toggle.active = enabled;
        addSettingWidget(toggle);
    }

    private void addTextFieldRow(int x, int y, int width, String labelKey, String value,
                                 boolean enabled, String hintKey, Consumer<String> onSubmit) {
        int controlWidth = controlWidth(width);
        int labelWidth = Math.max(1, width - controlWidth - 12);
        addSettingWidget(new GlassLabel(x, y, labelWidth, SETTINGS_ROW_HEIGHT,
                Component.translatable(labelKey), GlassTheme.Text.VALUE, FlatTheme.TEXT_SECONDARY));

        GlassTextField field = GlassTextField.create(x + width - controlWidth, y + 3,
                controlWidth, GlassTheme.Sizes.CONTROL, Component.translatable(hintKey), ignored -> {
                });
        field.setValue(value == null ? "" : value);
        field.setEditable(enabled);
        field.active = enabled;
        field.onSubmit(() -> onSubmit.accept(field.getValue()));
        addSettingWidget(field);
    }

    private static int controlWidth(int rowWidth) {
        return Math.min(230, Math.max(130, Math.round(rowWidth * 0.55F)));
    }

    private static double normalize(double value, double min, double max) {
        if (max <= min) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (value - min) / (max - min)));
    }

    private static double denormalize(double value, double min, double max) {
        return min + Math.max(0.0D, Math.min(1.0D, value)) * (max - min);
    }

    private static String rawValue(double value, boolean integer) {
        return integer ? Integer.toString((int) Math.round(value)) : Double.toString(value);
    }

    private String formatInteger(double value) {
        return Integer.toString((int) Math.round(value));
    }

    private String formatPercent(double value) {
        return Math.round(value * 100.0D) + "%";
    }

    private String formatTwoDecimals(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // ── 当前队列页 ──────────────────────────────────────────────────────

    private void buildQueuePage(BrowserPage page, WindowBounds inner) {
        int x = inner.x0();
        int y = inner.y0();
        int width = inner.width();

        titleLabel = addRenderableWidget(new GlassLabel(x, y, width, QUEUE_TITLE_HEIGHT,
                page.title(), 0.95F, FlatTheme.TEXT));
        // 歌单剩余数量贴在标题行右侧，不额外占一行高度。
        queueRemainingLabel = addRenderableWidget(new GlassLabel(x, y, width, QUEUE_TITLE_HEIGHT,
                Component.empty(), GlassTheme.Text.LABEL, FlatTheme.TEXT_FAINT).rightAligned());
        y += QUEUE_TITLE_HEIGHT + QUEUE_SECTION_GAP;

        MediaListView view = page.view();
        view.setRectangle(width, Math.max(MediaListView.ROW_HEIGHT * 3, inner.y1() - y), x, y);
        addRenderableWidget(view);
        page.apply();

        ClientMusicUiState state = ClientMusicUiState.getInstance();
        seenQueueRevision = state.queueRevision();
        seenQueueTrackId = state.trackId();
        updateQueueChrome();
    }

    /** 队列页除了列表只剩下标题行的「歌单剩余」提示，切歌 / 队列变化时刷新这一行。 */
    private void updateQueueChrome() {
        if (queueRemainingLabel != null) {
            int remaining = ClientMusicUiState.getInstance().queuePlaylistRemaining();
            queueRemainingLabel.setMessage(remaining > 0
                    ? Component.translatable("musicplayer.app.queue_remaining", remaining)
                    : Component.empty());
        }
    }

    // ── 网络 / 命令 ────────────────────────────────────────────────────

    private void requestSync() {
        if (ClientPlayNetworking.canSend(MusicUiRequestPayload.TYPE)) {
            ClientPlayNetworking.send(MusicUiRequestPayload.all());
        }
    }

    /** 只刷新队列快照：队列页可见时低频轮询用，不连带重发配置和最近播放。 */
    private void requestQueue() {
        if (ClientPlayNetworking.canSend(MusicUiRequestPayload.TYPE)) {
            ClientPlayNetworking.send(MusicUiRequestPayload.queue());
        }
    }

    private void sendConfig(String key, String value) {
        ClientMusicUiState.getInstance().setConfigValue(key, value);
        if (ClientPlayNetworking.canSend(MusicConfigUpdatePayload.TYPE)) {
            ClientPlayNetworking.send(new MusicConfigUpdatePayload(key, value));
        }
    }

    private void sendCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        try {
            minecraft.player.connection.sendCommand(command);
        } catch (RuntimeException exception) {
            MusicPlayerMod.LOGGER.warn("界面命令发送失败: {}", command, exception);
        }
    }

    private static String serverRaw(String key, String fallback) {
        return ClientMusicUiState.getInstance().configValue(key, fallback == null ? "" : fallback);
    }

    private static boolean serverBool(String key, boolean fallback) {
        return ClientMusicUiState.getInstance().configBoolean(key, fallback);
    }

    private static int serverInt(String key, int fallback) {
        return ClientMusicUiState.getInstance().configInt(key, fallback);
    }

    private static double serverDouble(String key, double fallback) {
        return ClientMusicUiState.getInstance().configDouble(key, fallback);
    }

    // ── 交互 ────────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        // 界面关掉后没有页面会消费这些结果：取消在途请求，别让它们继续占用线程。
        MediaBrowserService.getInstance().beginNavigation();
        cancelHero();
        heroReturns.clear();
        currentSongDetailView = null;
        super.onClose();
    }

    @Override
    public void tick() {
        // 关闭动画由基类在 tick 里推进；这里必须每帧交给它，否则界面永远退不出去
        if (closing()) {
            cancelHero();
            super.tick();
            return;
        }

        syncDiscAction();

        if (refocusOnTick) {
            refocusOnTick = false;
            if (searchField != null) {
                setFocused(searchField);
            }
        }

        if (settingsRelayoutPending) {
            settingsRelayoutPending = false;
            captureSettingsScroll();
            relayoutContent();
            return;
        }

        int revision = ClientMusicUiState.getInstance().configRevision();
        if (revision != seenConfigRevision) {
            seenConfigRevision = revision;
            if (top() instanceof SettingsPage && getFocused() == null) {
                captureSettingsScroll();
                relayoutContent();
                return;
            }
        }

        // 最近播放在服务端更新后是整份重发的；只更新数据，不重建控件，
        // 这样用户正在滚动 / 悬停时不会被打断。
        int recentRevision = ClientMusicUiState.getInstance().recentRevision();
        if (recentRevision != seenRecentRevision) {
            seenRecentRevision = recentRevision;
            if (top() == recentlyPlayedPage) {
                recentlyPlayedPage.apply();
            }
        }

        updateTitle();
        updateFavoriteButton();

        // 单曲详情页：如果这首正在播放，补拉歌词；歌词高亮由视图按当前进度自己算。
        if (top() instanceof SongDetailPage songDetailPage) {
            songDetailPage.tickLyrics();
        }
        if (top() == currentQueuePage) {
            ClientMusicUiState state = ClientMusicUiState.getInstance();
            // 进度每帧推进：当前曲目那一行的主题色玻璃背景跟着走。
            currentQueuePage.refreshProgress();
            if (++queuePollTicks >= QUEUE_POLL_INTERVAL) {
                queuePollTicks = 0;
                requestQueue();
            }
            int queueRevision = state.queueRevision();
            String trackId = state.trackId();
            if (queueRevision != seenQueueRevision || !trackId.equals(seenQueueTrackId)) {
                seenQueueRevision = queueRevision;
                seenQueueTrackId = trackId;
                currentQueuePage.apply();
            }
            updateQueueChrome();
        }
        super.tick();
    }

    /**
     * 切换侧边栏页面时，当前按钮会在自己的点击回调里被整体重建掉。原版容器随后仍会把
     * 这个已经不在控件列表里的按钮设为焦点，导致键盘事件一直发给一个不可见的旧控件。
     * 这里只接受仍然存在的控件，其余一律清空焦点。
     */
    @Override
    public void setFocused(GuiEventListener focused) {
        if (focused != null && !children().contains(focused)) {
            super.setFocused(null);
            return;
        }
        super.setFocused(focused);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean enter = event.key() == InputConstants.KEY_RETURN
                || event.key() == InputConstants.KEY_NUMPADENTER;
        if (enter && searchField != null && searchField.isFocused()) {
            searchPage.submit();
            return true;
        }
        boolean typing = getFocused() instanceof EditBox;
        if (!typing && !(top() instanceof SettingsPage) && top().view().keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    private record Shell(WindowBounds sidebar, WindowBounds main) {
    }

    /** 设置页占位页：不画列表，真正的表单由 {@link MusicBrowserScreen} 摆放。 */
    private static final class SettingsPage extends BrowserPage {

        @Override
        public Component title() {
            return Component.translatable("musicplayer.settings.title");
        }

        @Override
        public EnumSet<PageChrome> chrome() {
            return EnumSet.of(PageChrome.TITLE_BAR);
        }

        @Override
        public void apply() {
        }
    }
}
