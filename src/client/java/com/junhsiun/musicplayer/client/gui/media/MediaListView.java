package com.junhsiun.musicplayer.client.gui.media;

import com.junhsiun.musicplayer.client.gui.animation.HeroElement;
import com.junhsiun.musicplayer.client.gui.animation.SharedElementTransition;
import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassScroll;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import com.junhsiun.musicplayer.client.gui.icon.SvgIcon;
import com.junhsiun.musicplayer.client.gui.icon.SvgIcons;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.junhsiun.musicplayer.model.MediaStat;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用的"头部 + 行列表"滚动组件：搜索结果页和详情页都用它。
 *
 * <p><b>整块自绘而不是每行挂一个控件：</b>行会滚动、会错峰入场、还要跟着页面切换一起平移淡出。
 * 每行挂一个控件就得每帧同步 N 组坐标和状态（{@code GlassListView} 已经踩过这个坑，
 * 它的行按钮就是画出来的）。这里把行、封面、播放按钮、头部都画在同一个控件里，命中区现算。</p>
 *
 * <p><b>头部也在滚动区里：</b>详情页的封面、标题、简介、数据标签全部作为头部区块画在行前面，
 * 跟着同一份滚动偏移一起走。以前头部固定在顶上，能滚的只剩中间一小条，翻起来很难受。</p>
 *
 * <p><b>三层动画互不干扰：</b></p>
 * <ul>
 *   <li>页面切换：整块内容横向滑入（前进从右侧来，返回从左侧来）；</li>
 *   <li>列表入场：换一批数据时逐行错峰（每行延迟 26ms）上浮淡入；</li>
 *   <li>悬停/按压：单行高亮与收缩，只影响那一行。</li>
 * </ul>
 *
 * <p>所有动画都按 {@code Util.getMillis()} 推进，掉帧时不会"慢放"，也不会因为帧率不同而快慢不一。</p>
 */
public class MediaListView extends AbstractWidget {

    /** 交互回调。 */
    public interface Listener {
        /** 点了一行。 */
        void onItemClick(MediaItem item);

        /**
         * 点了一行，并带上这一行在屏幕上的封面/标题几何。
         *
         * <p>{@code origin} 用于共享元素过渡：界面可以拿它和详情页的目标位置做 FLIP 动画。
         * 默认实现直接退回 {@link #onItemClick(MediaItem)}，不认识这个信息的宿主无需改动。</p>
         */
        default void onItemClick(MediaItem item, HeroElement origin) {
            onItemClick(item);
        }

        /** 点了行尾的操作按钮（默认是"加入播放队列"，唱片母版界面是"刻录"）。 */
        void onPlay(MediaItem item);

        /**
         * 点了行尾的第二个操作按钮。
         *
         * <p>只有通过 {@link #setSecondaryActionIcon(SvgIcon)} 配置了图标才会出现。
         * 当前队列页用它给正在播放的那一行加「快进」按钮。</p>
         */
        default void onSecondaryPlay(MediaItem item) {
        }

        /**
         * 点了行尾最右侧的第三个操作按钮。
         *
         * <p>只有通过 {@link #setTrailingActionIcon(SvgIcon)} 配置了图标才会出现。
         * 浏览界面在手持唱片时用它显示「刻录」按钮。</p>
         */
        default void onTrailingPlay(MediaItem item) {
        }

        /** 点了列表末尾的"加载更多"。 */
        void onLoadMore();

        /**
         * 点了列表末尾的分页按钮。
         *
         * @param delta {@code -1} 上一页，{@code +1} 下一页
         */
        default void onPageChange(int delta) {
        }

        /** 点了头部区块里的"播放全部"。只有声明了 {@code playAll} 的头部才会有这个回调。 */
        default void onPlayAll() {
        }
    }

    /**
     * 列表上方的头部区块，随列表一起滚动。
     *
     * @param coverUrl    封面地址，可为 {@code null}
     * @param title       主标题
     * @param subtitle    副标题，可为 {@code null}
     * @param stats       数据小标签，可为空
     * @param description 简介原文，列表自己按宽度折行
     * @param playAll     头部右上角是否显示"播放全部"（歌单这类"一整个可以播"的条目才有）
     */
    public record Header(String coverUrl, Component title, Component subtitle,
                         List<MediaStat> stats, String description, boolean playAll) {

        /** 没有"播放全部"的头部。 */
        public Header(String coverUrl, Component title, Component subtitle,
                      List<MediaStat> stats, String description) {
            this(coverUrl, title, subtitle, stats, description, false);
        }
    }

    // ── 尺寸 ────────────────────────────────────────────────────────────
    /** 行高。两行文字 + 24 像素封面，行与行之间不会挤在一起。 */
    public static final int ROW_HEIGHT = 32;
    /** 缩略图边长。 */
    private static final int COVER_SIZE = 24;
    /** 缩略图到文字的间距。 */
    private static final int TEXT_GAP = 7;
    /** 行内右侧内边距：右箭头离行右边缘至少这么远，贴着边看起来像被切掉了。 */
    private static final int ROW_RIGHT_PADDING = 8;
    /** 行号（序号）列的宽度，从封面左边让出来。 */
    private static final int INDEX_WIDTH = 12;
    /** 行尾播放按钮的边长与它到箭头的间距。 */
    private static final int PLAY_SIZE = 16;
    /**
     * 按钮里图标的边长。
     *
     * <p>取 10：图标在 16 像素的按钮里不再顶满。更关键的是缩放后墨迹约占 4×8 像素——
     * 都是偶数，四边留白才能是整数像素；取 12 时墨迹是 5×9（奇数），留白只能是 5/6、3/4，
     * 看起来就是"差半个像素没居中"。</p>
     */
    private static final int PLAY_ICON_SIZE = 10;
    private static final int PLAY_GAP = 6;
    /** 滚动条宽度与它和内容之间留的间隙；行内容不占这块，免得和滚动条叠在一起。 */
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_GAP = 4;
    /** 列表底部留白：最后一行能完整露出来，而不是贴着边界被裁掉半个。 */
    private static final int BOTTOM_PADDING = 8;
    /** 分页页脚高度：和一行等高，里面是上一页 / 页码 / 下一页。 */
    private static final int PAGE_FOOTER_HEIGHT = ROW_HEIGHT;
    /** 分页箭头的可点边长。 */
    private static final int PAGE_ARROW_SIZE = 20;
    /** 行内文字缩放。 */
    private static final float TITLE_SCALE = 0.75F;
    private static final float SUBTITLE_SCALE = 0.62F;
    private static final float META_SCALE = 0.62F;
    private static final float INDEX_SCALE = 0.58F;
    /** 头部封面尺寸与简介最多行数。 */
    private static final int HEADER_COVER = 44;
    private static final int HEADER_MAX_LINES = 3;
    /** 头部"播放全部"按钮：尺寸，以及它离头部上沿的距离（和小标签那一行对齐）。 */
    private static final int HEADER_ACTION_WIDTH = 52;
    private static final int HEADER_ACTION_HEIGHT = 12;
    private static final int HEADER_ACTION_TOP = 24;
    /** 页面切换动画时长（毫秒）。 */
    private static final float TRANSITION_MILLIS = 180.0F;
    /** 页面切换的最大位移（GUI 像素）。 */
    private static final float TRANSITION_TRAVEL = 26.0F;
    /** 列表错峰入场：每行延迟与单行时长（毫秒）。 */
    private static final float REVEAL_STAGGER_MILLIS = 26.0F;
    private static final float REVEAL_MILLIS = 200.0F;
    /** 错峰行数上限：超过这个数量的行一起出现，免得长列表末尾等太久。 */
    private static final int REVEAL_MAX_ROWS = 12;
    /** 封面预取窗口：可见区上下各多提前这么多行开始下载。 */
    private static final int PREFETCH_ROWS = 6;
    /** 滚动条停手后淡出的时长。 */
    private static final float SCROLLBAR_FADE_MILLIS = 400.0F;
    /** 滚动条在最后一次滚动后保持完全可见的时长。 */
    private static final long SCROLLBAR_HOLD_MILLIS = 700L;
    /** 一次滚轮"格"折算的位移（GUI 像素）。 */
    private static final double SCROLL_STEP = ROW_HEIGHT * 0.85D;

    private final Listener listener;
    private final GlassScroll scroll = new GlassScroll();
    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private final GlassWidgets.Spring press = new GlassWidgets.Spring();

    /**
     * 行尾按钮的图标。
     *
     * <p>浏览界面用它表示"加入播放队列"；唱片母版界面复用同一套行样式，但那个按钮的动作是
     * "刻录到母版"，图标必须跟着换，否则会让人以为点下去是播放。</p>
     */
    private SvgIcon actionIcon = SvgIcons.QUEUE_ADD;
    /**
     * 行尾第二个操作按钮的图标；{@code null} 表示不显示。
     *
     * <p>排在主按钮左侧。当前队列页用它在「跳过」左边再放一个「快进」。</p>
     */
    private SvgIcon secondaryActionIcon;
    /**
     * 行尾最右侧的第三个操作按钮的图标；{@code null} 表示不显示。
     *
     * <p>排在整个操作区的最后面（最靠右）。浏览界面在手持可刻录唱片时用它显示「刻录」，
     * 与其他界面共用同一套行样式，不需要单独维护一份列表排版。</p>
     */
    private SvgIcon trailingActionIcon;
    /** 行尾按钮当前是否可点。刻录请求在途时置 {@code false}，避免连点重复刻录。 */
    private boolean actionEnabled = true;
    private boolean secondaryActionEnabled = true;
    private boolean trailingActionEnabled = true;
    /** 行尾按钮是否显示。队列页在没有正在播放的歌曲时会把它整列收起。 */
    private boolean rowActionsVisible = true;
    /**
     * 只在匹配这一条目 ID 的行上显示行尾按钮；{@code null} 表示所有可点行都显示。
     *
     * <p>当前队列页用它把「跳过」按钮只放在正在播放的那一行上，其余待播行不显示操作按钮。</p>
     */
    private String actionRowId;
    /** 歌曲行是否也可以点击。默认关闭（歌曲没有详情页）；最近播放页用它来直接重播。 */
    private boolean songsClickable;
    /** 扁平模式：不再给每行/徽标/按钮画玻璃，只保留悬停高亮与强调色。 */
    private boolean minimal;

    private Header header;
    private List<String> descriptionLines = List.of();
    private List<MediaItem> items = List.of();
    private boolean loading;
    private Component status = Component.empty();
    private int hoveredRow = -1;
    private int pressedRow = -1;
    /** 按下的是不是第二个操作按钮（快进），松开时据此决定回调。 */
    private boolean pressedSecondary;
    private boolean pressActive;

    /** 按下时锁定的目标：松手时据此决定回调，不受中途刷新/滚动影响。 */
    private PressTarget pressTarget = PressTarget.NONE;
    /**
     * 按下时锁定的条目。
     *
     * <p>队列页会在按下与松手之间因轮询刷新整份列表（{@link #showItems} 会把行号清掉），
     * 如果松手时再按几何重新命中，常常对不上同一条，于是出现"有按下音效却没反应"。
     * 锁定按下那一刻的条目，松手时直接用它触发回调就不会丢。</p>
     */
    private MediaItem pressedItem;
    private double pressX;
    private double pressY;
    /** 按下后累计的拖拽位移，超过 {@link #CLICK_TOLERANCE} 就当作拖拽而非点击。 */
    private double pressDragDistance;

    /** 按下锁定的目标类型。 */
    private enum PressTarget {
        NONE, ROW, PLAY, SECONDARY, TRAILING
    }

    /**
     * 点击与拖拽的分界（GUI 像素）。
     *
     * <p>原版 {@code MouseHandler.handleAccumulatedMovement} 只要鼠标在按住期间移动了一个
     * 像素就会派发 {@code mouseDragged}，而旧实现一收到 onDrag 就清掉按下状态——于是任何一点
     * 手抖都会把点击吞掉，只剩按下音效。给一个容差即可把"手抖"与"真的拖动"区分开。
     * 真正拖动时松手点也会离按下点很远，{@link #onRelease} 里还有一道位移判断兜底。</p>
     */
    private static final double CLICK_TOLERANCE = 4.0D;
    /** 分页页脚：当前页 / 总页数（{@code <= 0} 表示总数未知）。 */
    private boolean paginationVisible;
    private int pageNumber = 1;
    private int totalPages = 1;
    /** 还有没有下一页；总数未知时由调用方按"本页是否满页"给出。 */
    private boolean pageHasNext;
    private boolean pressedPrevPage;
    private boolean pressedNextPage;
    /** 以主题色液态玻璃背景显示播放进度的行；{@code null} 表示没有。 */
    private String progressRowId;
    private float progress;

    private long revealStartMillis = Util.getMillis();
    /** 滚动条显示到什么时候（毫秒时间戳）。滚动一下续期，停手后自己淡出。 */
    private long scrollbarUntilMillis;
    private long transitionStartMillis = -1L;
    private boolean transitionForward = true;

    /** 共享元素过渡：飞行期间隐藏本列表里对应的封面/标题，避免和目标端重影。 */
    private SharedElementTransition hero;
    /** 反向返回时要隐藏的那一行（按条目 ID 匹配）；{@code null} 表示不隐藏行。 */
    private String heroRowId;
    /** 是否隐藏头部区块的封面与标题（歌单详情 → 列表的反向/正向过渡）。 */
    private boolean heroHeader;
    /** 头部封面从小图切到高清大图时的平滑衔接（详情头部 44px 也吃高清图）。 */
    private final CoverFade headerCoverFade = new CoverFade();
    /** 上一帧头部是否处于共享元素飞行状态：用于落地那一帧交接封面状态。 */
    private boolean headerHeroWasActive;

    public MediaListView(int x, int y, int width, int height, Listener listener) {
        super(x, y, width, height, Component.empty());
        this.listener = listener;
    }

    // ── 共享元素配合 ────────────────────────────────────────────────────

    /** 隐藏某一行的封面/标题，交给最上层 overlay 绘制；过渡结束自动恢复。 */
    public void setRowHero(String itemId, SharedElementTransition hero) {
        this.hero = hero;
        this.heroRowId = itemId;
        this.heroHeader = false;
    }

    /** 隐藏头部区块的封面/标题（歌单详情页作为过渡目标时用）。 */
    public void setHeaderHero(SharedElementTransition hero) {
        this.hero = hero;
        this.heroRowId = null;
        this.heroHeader = true;
    }

    /** 清掉本列表上的共享元素隐藏状态。 */
    public void clearHero() {
        this.hero = null;
        this.heroRowId = null;
        this.heroHeader = false;
    }

    private boolean heroActive() {
        return hero != null && hero.isActive();
    }

    /** 立即结束本列表的滑入/滑出动画，让内容停在最终位置（共享元素动画期间用）。 */
    public void finishTransition() {
        this.transitionStartMillis = -1L;
    }

    // ── 外部喂数据 ──────────────────────────────────────────────────────

    /** 设置（或清空）头部区块。传 {@code null} 表示这一页没有头部。 */
    public void setHeader(Header header) {
        this.header = header;
        refreshHeaderLines();
    }

    /**
     * 换一批行。这个方法只更新数据，<b>不动滚动位置、不播动画</b>，可以随便重复调用。
     *
     * <p>需要"回到顶部 + 逐行入场"时，调用方在数据真正换了之后再调
     * {@link #resetAndReveal()}——把"更新数据"和"播动画"分开，追加（加载更多）才不会
     * 把列表弹回顶部。</p>
     */
    public void showItems(List<MediaItem> items, boolean hasMore, boolean loadingMore) {
        this.items = items == null ? List.of() : items;
        this.hoveredRow = -1;
        this.pressedRow = -1;
        this.pressedSecondary = false;
        this.pressedPrevPage = false;
        this.pressedNextPage = false;
    }

    /**
     * 设置列表末尾的分页页脚。
     *
     * <p>分页画在最后一条 item 后面，跟着列表一起滚动，而不是固定在列表外面。
     * 只有一页时自动隐藏（没有可翻的内容）。</p>
     *
     * @param page       当前页，从 1 开始
     * @param totalPages 总页数；{@code <= 0} 表示数据来源没给，页脚只显示当前页
     */
    public void setPagination(int page, int totalPages) {
        setPagination(page, totalPages, totalPages < 0);
    }

    /**
     * 带"还有没有下一页"提示的分页页脚。
     *
     * <p>数据来源没给总页数时，调用方仍知道本页有没有排满，据此决定下一页按钮是否可点，
     * 避免最后一页还挂着一个点了没反应的箭头。</p>
     */
    public void setPagination(int page, int totalPages, boolean hasNext) {
        this.pageNumber = Math.max(1, page);
        this.totalPages = totalPages;
        this.pageHasNext = hasNext;
        this.paginationVisible = this.pageNumber > 1 || hasNext;
    }

    /** 收起分页页脚（队列、收藏这类不分页的列表用）。 */
    public void clearPagination() {
        this.paginationVisible = false;
        this.pageNumber = 1;
        this.totalPages = 1;
        this.pageHasNext = false;
        this.pressedPrevPage = false;
        this.pressedNextPage = false;
    }

    /** 回到顶部并播一次逐行错峰入场（换了一批数据时用）。 */
    public void resetAndReveal() {
        scroll.reset();
        if (!items.isEmpty()) {
            startReveal();
        }
    }

    /** 只切换加载态（例如开始搜索时先转圈，结果到了再 {@link #showItems}）。 */
    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    /** 状态文案（空列表时居中显示）。 */
    public void setStatus(Component status) {
        this.status = status == null ? Component.empty() : status;
    }

    /**
     * 换行尾按钮的图标（默认 {@link SvgIcons#QUEUE_ADD}）。
     *
     * <p>列表行的排布、命中区、悬停高亮都不变，只换按钮里画的那个图标。</p>
     */
    public void setActionIcon(SvgIcon icon) {
        this.actionIcon = icon == null ? SvgIcons.QUEUE_ADD : icon;
    }

    /** 行尾按钮是否可点。关闭时按钮照常画出来，但点了不触发回调（图标会一起变暗）。 */
    public void setActionEnabled(boolean enabled) {
        this.actionEnabled = enabled;
    }

    /** 设置行尾第二个按钮的图标；传 {@code null} 隐藏它。 */
    public void setSecondaryActionIcon(SvgIcon icon) {
        this.secondaryActionIcon = icon;
    }

    /** 行尾第二个按钮是否可点。 */
    public void setSecondaryActionEnabled(boolean enabled) {
        this.secondaryActionEnabled = enabled;
    }

    /** 设置行尾最右侧第三个按钮的图标；传 {@code null} 隐藏它。 */
    public void setTrailingActionIcon(SvgIcon icon) {
        this.trailingActionIcon = icon;
    }

    /** 行尾第三个按钮是否可点。 */
    public void setTrailingActionEnabled(boolean enabled) {
        this.trailingActionEnabled = enabled;
    }

    /**
     * 行尾操作按钮是否显示。
     *
     * <p>当前队列页用它彻底收起按钮：队列里同时可能有单点队列与歌单预载队列，两者
     * 的可用操作不同，画一个"置顶"按钮会让一半的行点了没反应。队列页改为整行点击播放，
     * 管理操作仍走聊天栏的 {@code /music queue} 命令。</p>
     */
    public void setRowActionsVisible(boolean visible) {
        this.rowActionsVisible = visible;
    }

    /**
     * 只在这一条目对应的行上显示行尾按钮；传 {@code null} 表示恢复为所有可点行都显示。
     *
     * <p>和 {@link #setActionIcon(SvgIcon)} 配合使用：图标是全局的，但按钮是否出现按条目过滤。</p>
     */
    public void setActionRowId(String id) {
        this.actionRowId = id == null || id.isBlank() ? null : id;
    }

    /**
     * 给某一行铺一层以主题色液态玻璃绘制的播放进度背景。
     *
     * @param rowId   条目 ID；{@code null} / 空串表示清除
     * @param value   0..1 的播放进度，超出范围会被夹住
     */
    public void setRowProgress(String rowId, float value) {
        this.progressRowId = rowId == null || rowId.isBlank() ? null : rowId;
        this.progress = GlassWidgets.clamp01(value);
    }

    private boolean actionRowAllowed(MediaItem item) {
        return actionRowId == null || (item != null && actionRowId.equals(item.id()));
    }

    /** 歌曲行是否可点击。开启后点击歌曲会回调 {@link Listener#onItemClick(MediaItem)}。 */
    public void setSongsClickable(boolean value) {
        this.songsClickable = value;
    }

    /** 扁平模式：行、徽标、播放按钮不再画玻璃，改用纯色层次。默认关闭。 */
    public void setMinimal(boolean value) {
        this.minimal = value;
    }

    /** 播一次"从某侧滑入"的切换动画。 */
    public void startTransition(boolean forward) {
        this.transitionForward = forward;
        this.transitionStartMillis = Util.getMillis();
    }

    /** 播一次逐行入场动画。 */
    public void startReveal() {
        this.revealStartMillis = Util.getMillis();
    }

    /** 回到顶部（换页时用）。 */
    public void scrollToTop() {
        scroll.reset();
    }

    /**
     * 头部区块是否已经滚出可见区。
     *
     * <p>详情页的顶栏标题据此决定要不要显示：头部里的标题还看得见时不重复显示，
     * 滚过去之后再在顶栏补一个，免得整页只有封面和简介、看不出自己在哪一页。</p>
     */
    public boolean headerScrolledAway() {
        return header != null && scroll.position() > headerHeight() - 8;
    }

    /** 滚动条续期：任何滚动动作都调一下。 */
    private void markScrolling() {
        scrollbarUntilMillis = Util.getMillis() + (long) SCROLLBAR_FADE_MILLIS + SCROLLBAR_HOLD_MILLIS;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    // ── 几何 ────────────────────────────────────────────────────────────

    /** 头部区块的高度（没有头部就是 0）。 */
    private int headerHeight() {
        if (header == null) {
            return 0;
        }
        int textBlock = Math.max(HEADER_COVER, 14 + 10 + 12);
        int description = descriptionLines.isEmpty() ? 0 : descriptionLines.size() * 8 + 2;
        return 4 + textBlock + description + 8;
    }

    /** 行内容可用的右边界：给滚动条让出位置，行和滚动条不会叠在一起。 */
    private int contentRight() {
        return getX() + width - SCROLLBAR_WIDTH - SCROLLBAR_GAP;
    }

    /** 列表可见区的下边界。 */
    private int listBottom() {
        return getY() + height;
    }

    private int contentHeight() {
        return headerHeight() + items.size() * ROW_HEIGHT + paginationHeight() + BOTTOM_PADDING;
    }

    /** 分页页脚实际占的高度；没有内容或只有一页时为零。 */
    private int paginationHeight() {
        return paginationVisible && !items.isEmpty() ? PAGE_FOOTER_HEIGHT : 0;
    }

    private double maxScroll() {
        return Math.max(0.0D, contentHeight() - height);
    }

    /** 第 {@code index} 行的上沿（已含头部偏移与滚动偏移）。 */
    private int rowTop(int index) {
        return getY() + headerHeight() + index * ROW_HEIGHT - scroll.offset();
    }

    /**
     * 算出某一行封面与标题在屏幕上的位置，交给共享元素过渡做端点。
     *
     * <p>这里刻意重复一遍 {@link #drawRow} 的布局算式，而不是从绘制过程中收集：
     * 点击发生在事件阶段，那一帧还没有走绘制；按同一套常量重算才能保证动画端点与
     * 用户眼睛看到的行完全重合。两边任何一处改了间距，请一起改。</p>
     *
     * @param includeShift 是否叠加页面切换的横向平移。正向点击时列表正停在最终位置，
     *                     加上更精确；反向返回时目标列表的滑入动画会被跳过，取 0 更稳。
     * @return {@code null} 表示这一行当前没有可用的屏幕位置（越界、不可导航、滚出可见区）
     */
    private HeroElement heroElement(int index, MediaItem item, boolean includeShift) {
        if (index < 0 || index >= items.size() || !navigable(item) || width <= 0) {
            return null;
        }
        int top = rowTop(index);
        // 行完全滚出可见区时不做共享元素：起点/落点在屏幕外，动画会从画面边缘飞入。
        if (top + ROW_HEIGHT <= getY() || top >= listBottom()) {
            return null;
        }
        Font font = Minecraft.getInstance().font;
        // 行入场动画会把内容从下方 6px 处推上来；点击时通常已经落定，但仍按当前
        // reveal 偏移算一次，避免在入场动画里手快点击时起点偏下。
        float reveal = revealProgress(Util.getMillis(), index);
        top += Math.round((1.0F - reveal) * 6.0F);
        // 整个列表在页面切换时还会横向平移，起点必须跟着走。
        int shift = 0;
        if (includeShift) {
            float transition = transitionProgress();
            shift = Math.round(transitionForward
                    ? (1.0F - transition) * TRANSITION_TRAVEL
                    : -(1.0F - transition) * TRANSITION_TRAVEL);
        }

        int coverLeft = getX() + INDEX_WIDTH + 4 + shift;
        int coverTop = top + (ROW_HEIGHT - COVER_SIZE) / 2;
        int textLeft = coverLeft + COVER_SIZE + TEXT_GAP;
        int textRight = contentRight() + shift - ROW_RIGHT_PADDING - 9;

        int lineY = top + (ROW_HEIGHT - 8) / 2;

        // 与 drawRow 相同的可写宽度收窄顺序：行尾按钮 → 补充信息。
        if (queuable(item) && rowActionsVisible && actionRowAllowed(item)) {
            textRight = playButtonLeft(item) + shift - PLAY_GAP;
            if (secondaryActionVisible(item)) {
                textRight = secondaryButtonLeft(item) + shift - PLAY_GAP;
            }
        }
        // 行尾补充信息（时长、播放量）：右对齐在按钮左侧，终点是详情页对应的那一行。
        HeroElement.HeroText meta = null;
        if (item.meta() != null) {
            int metaWidth = Math.round(font.width(item.meta()) * META_SCALE);
            int metaLeft = textRight - metaWidth;
            if (metaLeft > textLeft + 12) {
                int metaColor = minimal
                        ? FlatTheme.TEXT_TERTIARY
                        : GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.22F);
                meta = new HeroElement.HeroText(
                        item.meta(), textRight - metaWidth * 0.5F, lineY, META_SCALE, metaColor);
                textRight = metaLeft - 6;
            }
        }

        int textWidth = Math.max(1, textRight - textLeft);
        String shown = ellipsize(font, item.title(), textWidth, TITLE_SCALE);
        float titleCenterX = textLeft + font.width(shown) * TITLE_SCALE * 0.5F;
        int titleTopY = item.subtitle() == null
                ? lineY
                : top + 8;
        int titleColor = minimal ? FlatTheme.TEXT : GlassTheme.TEXT;
        // 副标题（歌曲是「歌手 · 专辑」，歌单是「创建者 · N 首」）：详情页同样有这一段时一起飞。
        // 歌曲的专辑部分单独拆出来，终点是详情页的专辑行——这样列表里的一整段副标题
        // 会分成两行落进详情页，而不是只飞歌手、专辑凭空淡入。
        HeroElement.HeroText subtitle = null;
        HeroElement.HeroText album = null;
        if (item.subtitle() != null) {
            String subShown = ellipsize(font, item.subtitle(), textWidth, SUBTITLE_SCALE);
            int subColor = minimal
                    ? FlatTheme.TEXT_SECONDARY
                    : GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.35F);
            // 只有歌曲的副标题是「歌手 · 专辑」，才拆出专辑部分飞向详情页的专辑行；
            // 歌单的「创建者 · N 首」等其它类型保持整段，不硬拆成两段。
            int separator = item.kind() == MediaKind.SONG ? subShown.indexOf(" · ") : -1;
            if (separator > 0) {
                String artistPart = subShown.substring(0, separator);
                String albumPart = subShown.substring(separator + 3);
                float artistCenterX = textLeft + font.width(artistPart) * SUBTITLE_SCALE * 0.5F;
                float albumLeft = textLeft
                        + (font.width(artistPart) + font.width(" · ")) * SUBTITLE_SCALE;
                subtitle = new HeroElement.HeroText(
                        artistPart, artistCenterX, top + 19, SUBTITLE_SCALE, subColor);
                if (!albumPart.isBlank()) {
                    float albumCenterX = albumLeft + font.width(albumPart) * SUBTITLE_SCALE * 0.5F;
                    album = new HeroElement.HeroText(
                            albumPart, albumCenterX, top + 19, SUBTITLE_SCALE, subColor);
                }
            } else {
                float subCenterX = textLeft + font.width(subShown) * SUBTITLE_SCALE * 0.5F;
                subtitle = new HeroElement.HeroText(
                        subShown, subCenterX, top + 19, SUBTITLE_SCALE, subColor);
            }
        }
        return new HeroElement(
                coverLeft, coverTop, coverLeft + COVER_SIZE, coverTop + COVER_SIZE,
                titleCenterX, titleTopY, TITLE_SCALE, titleColor,
                shown, item.coverUrl(), item.id(), subtitle, album, meta);
    }

    /** 按条目 ID 找可见行并算出它的共享元素端点；找不到/不在可见区返回 {@code null}。 */
    public HeroElement heroElementForId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        for (int index = 0; index < items.size(); index++) {
            MediaItem item = items.get(index);
            if (itemId.equals(item.id())) {
                return heroElement(index, item, false);
            }
        }
        return null;
    }

    /**
     * 头部区块封面与标题的共享元素端点（歌单/歌手/专辑详情的列表头部）。
     *
     * <p>头部跟着列表滚动，完全滚出可见区时返回 {@code null}，避免从屏幕外飞入。</p>
     */
    public HeroElement headerHeroElement(String itemId) {
        if (header == null) {
            return null;
        }
        int top = contentTop() + 4;
        if (top + HEADER_COVER <= getY() || top >= listBottom()) {
            return null;
        }
        Font font = Minecraft.getInstance().font;
        int left = getX();
        int right = contentRight();
        int textLeft = left + HEADER_COVER + 8;
        int textWidth = Math.max(1, right - textLeft);
        String title = ellipsize(font, text(header.title()), textWidth, GlassTheme.Text.LABEL);
        float titleCenterX = textLeft + font.width(title) * GlassTheme.Text.LABEL * 0.5F;
        int titleColor = minimal ? FlatTheme.TEXT : GlassTheme.TEXT;
        // 头部副标题（歌单创建者 / 歌手别名 / 播客分类）：与列表行的副标题同槽位，一起飞。
        HeroElement.HeroText subtitle = null;
        if (header.subtitle() != null && !header.subtitle().getString().isBlank()) {
            String sub = ellipsize(font, header.subtitle().getString(), textWidth, SUBTITLE_SCALE);
            float subCenterX = textLeft + font.width(sub) * SUBTITLE_SCALE * 0.5F;
            int subColor = minimal
                    ? FlatTheme.TEXT_SECONDARY
                    : GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.30F);
            subtitle = new HeroElement.HeroText(sub, subCenterX, top + 14, SUBTITLE_SCALE, subColor);
        }
        return new HeroElement(
                left, top, left + HEADER_COVER, top + HEADER_COVER,
                titleCenterX, top + 2, GlassTheme.Text.LABEL, titleColor,
                title, header.coverUrl(), itemId, subtitle, null, null);
    }

    /** 列表内容的起点（头部上沿）。 */
    private int contentTop() {
        return getY() - scroll.offset();
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return -1;
        }
        int relative = (int) Math.floor(mouseY - getY() + scroll.position()) - headerHeight();
        if (relative < 0) {
            return -1;
        }
        int index = relative / ROW_HEIGHT;
        return index < items.size() ? index : -1;
    }

    /** 头部"播放全部"按钮的矩形。它跟着头部一起滚动，所以位置要算上滚动偏移。 */
    private int headerActionLeft() {
        return contentRight() - HEADER_ACTION_WIDTH;
    }

    private int headerActionTop() {
        return contentTop() + 4 + HEADER_ACTION_TOP;
    }

    private boolean overHeaderAction(double mouseX, double mouseY) {
        if (header == null || !header.playAll()) {
            return false;
        }
        int left = headerActionLeft();
        int top = headerActionTop();
        return mouseX >= left && mouseX <= left + HEADER_ACTION_WIDTH
                && mouseY >= top && mouseY <= top + HEADER_ACTION_HEIGHT;
    }

    /**
     * "加入歌单"按钮右边界。
     *
     * <p>贴着行内容右边界，和歌单行的右箭头占同一列——两类行右边的按钮/箭头在同一条竖线上，
     * 整列看下去不会一高一低。（歌曲行没有箭头，所以这里不用再给箭头让位置。）</p>
     *
     * <p>手持唱片时最右侧还会多出一个「刻录」按钮，主按钮随之向左让位。</p>
     */
    private int playButtonRight(MediaItem item) {
        return trailingActionVisible(item)
                ? trailingButtonLeft() - PLAY_GAP
                : contentRight() - ROW_RIGHT_PADDING;
    }

    /** 播放按钮左边界。 */
    private int playButtonLeft(MediaItem item) {
        return playButtonRight(item) - PLAY_SIZE;
    }

    /** 行尾第二个按钮的右边界：主按钮左侧隔一个间距。 */
    private int secondaryButtonRight(MediaItem item) {
        return playButtonLeft(item) - PLAY_GAP;
    }

    private int secondaryButtonLeft(MediaItem item) {
        return secondaryButtonRight(item) - PLAY_SIZE;
    }

    /** 行尾最右侧第三个按钮的右边界。 */
    private int trailingButtonRight() {
        return contentRight() - ROW_RIGHT_PADDING;
    }

    private int trailingButtonLeft() {
        return trailingButtonRight() - PLAY_SIZE;
    }

    /** 这一行是否显示第二个操作按钮。 */
    private boolean secondaryActionVisible(MediaItem item) {
        return secondaryActionIcon != null && queuable(item) && rowActionsVisible
                && actionRowAllowed(item);
    }

    /** 这一行是否显示最右侧的第三个操作按钮。 */
    private boolean trailingActionVisible(MediaItem item) {
        return trailingActionIcon != null && queuable(item) && rowActionsVisible
                && actionRowAllowed(item);
    }

    /** 鼠标是否落在某一行的第二个操作按钮上。 */
    private int secondaryRowAt(double mouseX, double mouseY) {
        if (!secondaryActionEnabled || secondaryActionIcon == null || !rowActionsVisible) {
            return -1;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0 || !secondaryActionVisible(items.get(index))) {
            return -1;
        }
        int right = secondaryButtonRight(items.get(index));
        int left = secondaryButtonLeft(items.get(index));
        int top = rowTop(index) + (ROW_HEIGHT - PLAY_SIZE) / 2;
        if (mouseX < left || mouseX > right || mouseY < top || mouseY > top + PLAY_SIZE) {
            return -1;
        }
        return index;
    }

    /** 鼠标是否落在某一行最右侧的第三个操作按钮上。 */
    private int trailingRowAt(double mouseX, double mouseY) {
        if (!trailingActionEnabled || trailingActionIcon == null || !rowActionsVisible) {
            return -1;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0 || !trailingActionVisible(items.get(index))) {
            return -1;
        }
        int right = trailingButtonRight();
        int left = trailingButtonLeft();
        int top = rowTop(index) + (ROW_HEIGHT - PLAY_SIZE) / 2;
        if (mouseX < left || mouseX > right || mouseY < top || mouseY > top + PLAY_SIZE) {
            return -1;
        }
        return index;
    }

    /** 鼠标是否落在某一行的操作按钮上。 */
    private int playRowAt(double mouseX, double mouseY) {
        if (!actionEnabled || !rowActionsVisible) {
            return -1;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0 || !queuable(items.get(index)) || !actionRowAllowed(items.get(index))) {
            return -1;
        }
        int right = playButtonRight(items.get(index));
        int left = playButtonLeft(items.get(index));
        int top = rowTop(index) + (ROW_HEIGHT - PLAY_SIZE) / 2;
        if (mouseX < left || mouseX > right || mouseY < top || mouseY > top + PLAY_SIZE) {
            return -1;
        }
        return index;
    }


    // ── 分页页脚 ────────────────────────────────────────────────────────

    private int footerTop() {
        return rowTop(items.size());
    }

    private int prevPageLeft() {
        return getX() + 2;
    }

    private int nextPageLeft() {
        return contentRight() - PAGE_ARROW_SIZE;
    }

    private int footerButtonTop() {
        return footerTop() + (PAGE_FOOTER_HEIGHT - PAGE_ARROW_SIZE) / 2;
    }

    private boolean canGoPrevPage() {
        return pageNumber > 1;
    }

    private boolean canGoNextPage() {
        return pageHasNext;
    }

    private boolean overPrevPage(double mouseX, double mouseY) {
        return paginationHeight() > 0
                && overSquare(mouseX, mouseY, prevPageLeft(), footerButtonTop());
    }

    private boolean overNextPage(double mouseX, double mouseY) {
        return paginationHeight() > 0
                && overSquare(mouseX, mouseY, nextPageLeft(), footerButtonTop());
    }

    private boolean overSquare(double mouseX, double mouseY, int left, int top) {
        return isMouseOver(mouseX, mouseY)
                && mouseX >= left && mouseX <= left + PAGE_ARROW_SIZE
                && mouseY >= top && mouseY <= top + PAGE_ARROW_SIZE;
    }

    /**
     * 这一行能不能"加入播放队列"。
     *
     * <p>只有单曲有这个按钮：歌单的按钮没法叫"加入歌单"——{@code /music play playlist} 是
     * 整单替换当前队列，不是追加。歌单的播放入口在详情页头部的"播放全部"。</p>
     */
    private static boolean queuable(MediaItem item) {
        return item != null && !item.id().isBlank() && item.kind() == MediaKind.SONG;
    }

    /**
     * 这一行点下去会不会打开详情（所有条目都有详情，单曲打开专属的歌曲页）。
     */
    private static boolean navigable(MediaItem item) {
        return item != null && !item.id().isBlank();
    }

    /** 这一行点下去有没有回调。 */
    private boolean rowClickable(MediaItem item) {
        return navigable(item);
    }

    // ── 交互 ────────────────────────────────────────────────────────────

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        // 每次按下先把上一次的按压状态清干净，避免残留的捕获在松手时误触发。
        clearPress();
        pressX = event.x();
        pressY = event.y();
        if (overHeaderAction(event.x(), event.y())) {
            pressActive = true;
            return;
        }
        if (overPrevPage(event.x(), event.y()) && canGoPrevPage()) {
            pressedPrevPage = true;
            pressActive = true;
            return;
        }
        if (overNextPage(event.x(), event.y()) && canGoNextPage()) {
            pressedNextPage = true;
            pressActive = true;
            return;
        }
        int trailingIndex = trailingRowAt(event.x(), event.y());
        if (trailingIndex >= 0) {
            pressedRow = trailingIndex;
            pressTarget = PressTarget.TRAILING;
            pressedItem = items.get(trailingIndex);
            pressActive = true;
            return;
        }
        int queueIndex = playRowAt(event.x(), event.y());
        if (queueIndex >= 0) {
            pressedRow = queueIndex;
            pressTarget = PressTarget.PLAY;
            pressedItem = items.get(queueIndex);
            pressActive = true;
            return;
        }
        int secondaryIndex = secondaryRowAt(event.x(), event.y());
        if (secondaryIndex >= 0) {
            pressedRow = secondaryIndex;
            pressedSecondary = true;
            pressTarget = PressTarget.SECONDARY;
            pressedItem = items.get(secondaryIndex);
            pressActive = true;
            return;
        }
        int index = rowAt(event.x(), event.y());
        // 歌曲没有详情页，点行本身不做事：连按下的回弹也不给，免得看起来"点了有反应"
        if (index < 0 || !rowClickable(items.get(index))) {
            pressedRow = -1;
            return;
        }
        pressedRow = index;
        pressTarget = PressTarget.ROW;
        pressedItem = items.get(index);
        pressActive = true;
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        // 用按下时"锁定"的目标来判断，而不是重新按几何命中：队列页会在按下与松手之间
        // 因轮询刷新整份列表（showItems 会把行号清掉），重新命中常常对不上同一条，
        // 于是出现"有按下音效却没反应"。锁定按下时的条目就能稳定触发。
        PressTarget target = pressTarget;
        MediaItem item = pressedItem;
        int clickedRow = pressedRow;
        boolean prevPage = pressedPrevPage;
        boolean nextPage = pressedNextPage;
        double dragDistance = pressDragDistance;
        double movedX = Math.abs(event.x() - pressX);
        double movedY = Math.abs(event.y() - pressY);
        clearPress();
        if (overHeaderAction(event.x(), event.y())) {
            listener.onPlayAll();
            return;
        }
        if (prevPage && overPrevPage(event.x(), event.y())) {
            listener.onPageChange(-1);
            return;
        }
        if (nextPage && overNextPage(event.x(), event.y())) {
            listener.onPageChange(1);
            return;
        }
        if (target == PressTarget.NONE || item == null) {
            return;
        }
        // 只有"在原地点击"才触发：指针移出控件、或明显拖动过，都按取消处理。
        if (!isMouseOver(event.x(), event.y())) {
            return;
        }
        if (dragDistance > CLICK_TOLERANCE || movedX > CLICK_TOLERANCE || movedY > CLICK_TOLERANCE) {
            return;
        }
        switch (target) {
            case PLAY -> listener.onPlay(item);
            case SECONDARY -> listener.onSecondaryPlay(item);
            case TRAILING -> listener.onTrailingPlay(item);
            // 源几何必须在 clearPress() 之前取：pressedRow 一清就不知道是哪一行了。
            // 取不到也照样跳转，只是不做共享元素动画。
            case ROW -> listener.onItemClick(item, heroElement(clickedRow, item, true));
            default -> {
            }
        }
    }

    /** 清掉按下相关的全部状态（视觉与逻辑）。 */
    private void clearPress() {
        pressedRow = -1;
        pressedSecondary = false;
        pressedPrevPage = false;
        pressedNextPage = false;
        pressActive = false;
        pressTarget = PressTarget.NONE;
        pressedItem = null;
        pressDragDistance = 0.0D;
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        // 只有真正拖动（累计位移超过容差）才取消这次点击。以前任何一点鼠标抖动都会走到
        // 这里把按下状态清掉，结果"按下有音效、松手却没反应"——大多数正常点击都带一两个
        // 像素的抖动，正好被误判成拖拽。
        if (pressTarget == PressTarget.NONE) {
            return;
        }
        pressDragDistance += Math.hypot(dragX, dragY);
        if (pressDragDistance > CLICK_TOLERANCE) {
            clearPress();
        }
    }

    /**
     * 滚轮：只给速度一个冲量，位置交给 {@link GlassScroll} 慢慢滑过去。
     *
     * <p>直接把滚动位置加减一个量是"瞬时跳变"——再高的帧率也是一格一格跳，看起来就是
     * "不丝滑"。冲量 + 惯性 + 平滑跟随的手感在 {@link GlassScroll} 里。</p>
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0.0D) {
            return false;
        }
        scroll.setMax(maxScroll());
        if (!scroll.canScroll()) {
            return false;
        }
        scroll.impulse(-scrollY * SCROLL_STEP);
        markScrolling();
        return true;
    }

    /**
     * 键盘滚动：↑↓ 一行，PgUp/PgDn 一屏，Home/End 到顶/到底。
     *
     * <p>和滚轮走同一套 {@link GlassScroll} 冲量，手感一致；Home/End 用
     * {@link GlassScroll#jumpTo(double)} 滑过去，不做瞬移——一下子跳到底会让人
     * 完全失去"自己在列表哪个位置"的感觉。</p>
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        scroll.setMax(maxScroll());
        if (!scroll.canScroll()) {
            return false;
        }
        int page = Math.max(ROW_HEIGHT, height - ROW_HEIGHT);
        switch (event.key()) {
            case InputConstants.KEY_UP -> scroll.impulse(-ROW_HEIGHT);
            case InputConstants.KEY_DOWN -> scroll.impulse(ROW_HEIGHT);
            case InputConstants.KEY_PAGEUP -> scroll.impulse(-page);
            case InputConstants.KEY_PAGEDOWN -> scroll.impulse(page);
            case InputConstants.KEY_HOME -> scroll.jumpTo(0.0D);
            case InputConstants.KEY_END -> scroll.jumpTo(maxScroll());
            default -> {
                return false;
            }
        }
        markScrolling();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    // ── 绘制 ────────────────────────────────────────────────────────────

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (width <= 0 || height <= 0) {
            return;
        }
        scroll.setMax(maxScroll());
        scroll.tick();
        Minecraft client = Minecraft.getInstance();
        press.tickSpring(pressActive, pressActive ? GlassTheme.Motion.PRESS : GlassTheme.Motion.RELEASE);
        Font font = client.font;

        // 页面切换：整块内容平移滑入
        float transition = transitionProgress();
        int shift = Math.round(transitionForward
                ? (1.0F - transition) * TRANSITION_TRAVEL
                : -(1.0F - transition) * TRANSITION_TRAVEL);

        // 一道裁剪管住全部内容：文字、图标走原版 scissor，玻璃方块走自带的 scissorArea。
        // 滚出可见区的行、徽标、播放按钮都由它遮挡，不需要各自判断可见性。
        GlassWidgets.beginClip(graphics, getX(), getY(), getX() + width, getY() + height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(shift, 0.0F);

        drawContent(graphics, font, mouseX, mouseY);

        graphics.pose().popMatrix();
        GlassWidgets.endClip(graphics);

        // 加载 / 空状态浮在最上层，不随切换动画平移
        if (loading) {
            drawLoading(graphics, font);
        } else if (!status.getString().isBlank() && items.isEmpty()) {
            drawCenteredStatus(graphics, font, status);
        } else if (status.getString().isBlank() && items.isEmpty() && header == null) {
            drawCenteredStatus(graphics, font, Component.translatable("musicplayer.browser.empty"));
        }
    }

    /**
     * 预取封面。
     *
     * <p>只在行滚进可见区才开始下载的话，一定会看到"先一个占位块、过一会儿才变成图"。
     * 这里把可见区上下各多算 {@value #PREFETCH_ROWS} 行，提前发起下载；缩略图下载是单线程
     * 队列、按发起顺序处理，所以往下滚的时候，下一屏的封面基本已经在路上了。</p>
     *
     * <p>每帧调用是安全的：命中缓存或已在下载时只是一次哈希查找，不会重复发起。</p>
     */
    private void prefetchCovers() {
        MediaThumbnails thumbnails = MediaThumbnails.getInstance();
        if (header != null) {
            thumbnails.request(header.coverUrl());
        }
        if (items.isEmpty() || height <= 0) {
            return;
        }
        int firstVisible = Math.max(0, (scroll.offset() - headerHeight()) / ROW_HEIGHT);
        int visibleRows = Math.max(1, height / ROW_HEIGHT + 1);
        int last = Math.min(items.size() - 1, firstVisible + visibleRows + PREFETCH_ROWS);
        int first = Math.max(0, firstVisible - PREFETCH_ROWS);
        for (int index = first; index <= last; index++) {
            thumbnails.request(items.get(index).coverUrl());
        }
    }

    private void drawContent(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        prefetchCovers();
        double mx = mouseX;
        double my = mouseY;
        int hoverIndex = isMouseOver((int) mx, (int) my) ? rowAt(mx, my) : -1;
        int hoverPlay = isMouseOver((int) mx, (int) my) ? playRowAt(mx, my) : -1;
        int hoverSecondary = isMouseOver((int) mx, (int) my) ? secondaryRowAt(mx, my) : -1;
        int hoverTrailing = isMouseOver((int) mx, (int) my) ? trailingRowAt(mx, my) : -1;
        hoveredRow = hoverIndex;
        hover.tick(hoverIndex >= 0, GlassTheme.Motion.HOVER);

        // 越界部分由外层的 GlassWidgets.beginClip 统一遮挡（文字走原版 scissor，
        // 玻璃走 scissorArea），这里只做整行的剔除，省掉看不见的那些行的排版开销。
        drawHeader(graphics, font, (int) mx, (int) my);
        long now = Util.getMillis();
        for (int index = 0; index < items.size(); index++) {
            int top = rowTop(index);
            if (top + ROW_HEIGHT <= getY() || top >= listBottom()) {
                continue;
            }
            float hoverAmount = index == hoverIndex ? hover.eased() : 0.0F;
            // 按下效果只落在"被按中的那个目标"上：点行 → 行底浮一层；点行内播放/次按钮 →
            // 只有那颗按钮有按下回弹，行本身不能跟着一起压下去（否则像整行被点了）。
            float pressValue = index == pressedRow ? GlassWidgets.clamp01(press.value()) : 0.0F;
            float rowPress = pressTarget == PressTarget.ROW ? pressValue : 0.0F;
            float playPress = pressTarget == PressTarget.PLAY ? pressValue : 0.0F;
            float secondaryPress = pressTarget == PressTarget.SECONDARY ? pressValue : 0.0F;
            float trailingPress = pressTarget == PressTarget.TRAILING ? pressValue : 0.0F;
            drawRow(graphics, font, items.get(index), index, top, revealProgress(now, index),
                    hoverAmount, rowPress, playPress, secondaryPress, trailingPress,
                    index == hoverPlay, index == hoverSecondary, index == hoverTrailing);
        }
        drawPagination(graphics, font, mouseX, mouseY);
        drawScrollbar(graphics);
    }

    private float transitionProgress() {
        if (transitionStartMillis < 0L) {
            return 1.0F;
        }
        long elapsed = Util.getMillis() - transitionStartMillis;
        if (elapsed >= TRANSITION_MILLIS) {
            transitionStartMillis = -1L;
            return 1.0F;
        }
        float p = Math.max(0.0F, elapsed / TRANSITION_MILLIS);
        return easeOutCubic(p);
    }

    private static float easeOutCubic(float p) {
        float inverse = 1.0F - p;
        return 1.0F - inverse * inverse * inverse;
    }

    private float revealProgress(long now, int index) {
        int staggerIndex = Math.min(index, REVEAL_MAX_ROWS);
        float delay = staggerIndex * REVEAL_STAGGER_MILLIS;
        float elapsed = now - revealStartMillis - delay;
        if (elapsed <= 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, elapsed / REVEAL_MILLIS);
    }

    // ── 头部 ────────────────────────────────────────────────────────────

    private void drawHeader(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (header == null) {
            return;
        }
        int left = getX();
        int right = contentRight();
        int top = contentTop() + 4;
        // 头部整体跟着滚动：滚出上边界就整块被裁掉（scissor 已经在外面套好了）

        // 歌单详情作为共享元素目标时，封面与标题先交给 overlay；副信息跟着 reveal 淡入。
        boolean suppressHero = heroHeader && heroActive();
        // 飞行结束的那一帧：把飞行图层最后一帧的封面档位交给淡入状态，
        // 已经是大图就直接画，不再从小图淡入一遍。
        if (headerHeroWasActive && !suppressHero && hero != null) {
            headerCoverFade.handoff(hero.coverThumb());
        }
        headerHeroWasActive = suppressHero;
        float reveal = suppressHero ? hero.contentReveal() : 1.0F;
        // 副标题如果也有对应元素，飞行期间同样交给 overlay，避免落点前后出现两份。
        boolean subtitleFlying = suppressHero && hero.subtitleActive();

        if (!suppressHero
                && !headerCoverFade.draw(graphics, header.coverUrl(),
                left, top, left + HEADER_COVER, top + HEADER_COVER)) {
            drawCoverPlaceholder(graphics, left, top, HEADER_COVER);
        }
        int textLeft = left + HEADER_COVER + 8;
        int textWidth = Math.max(1, right - textLeft);
        int titleColor = minimal ? FlatTheme.TEXT : GlassTheme.TEXT;
        int subtitleColor = minimal
                ? FlatTheme.TEXT_SECONDARY
                : GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.30F);
        if (!suppressHero) {
            GlassWidgets.text(graphics, font,
                    Component.literal(ellipsize(font, text(header.title()), textWidth, GlassTheme.Text.LABEL)),
                    textLeft, top + 2, titleColor, GlassTheme.Text.LABEL);
        }
        if (!subtitleFlying && reveal > 0.01F
                && header.subtitle() != null && !header.subtitle().getString().isBlank()) {
            GlassWidgets.text(graphics, font,
                    Component.literal(ellipsize(font, header.subtitle().getString(), textWidth, SUBTITLE_SCALE)),
                    textLeft, top + 14, fade(subtitleColor, reveal), SUBTITLE_SCALE);
        }
        // 有"播放全部"时给小标签让出右侧那一段，避免两者叠在一起
        int statsWidth = header.playAll() ? Math.max(1, textWidth - HEADER_ACTION_WIDTH - 6) : textWidth;
        if (reveal > 0.15F) {
            drawStats(graphics, font, header.stats(), textLeft, top + 25, statsWidth);
        }
        if (header.playAll()) {
            drawHeaderAction(graphics, font, mouseX, mouseY);
        }

        if (reveal > 0.01F && !descriptionLines.isEmpty()) {
            int y = top + HEADER_COVER + 3;
            int descriptionColor = minimal
                    ? FlatTheme.TEXT_TERTIARY
                    : GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.42F);
            for (String line : descriptionLines) {
                GlassWidgets.text(graphics, font, Component.literal(line), left, y,
                        fade(descriptionColor, reveal), SUBTITLE_SCALE);
                y += 8;
            }
        }
    }

    /** 保留 RGB，只把 alpha 乘上淡入系数。 */
    private static int fade(int argb, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        int alpha = Math.round(((argb >>> 24) & 0xFF) * clamped);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static String text(Component component) {
        return component == null ? "" : component.getString();
    }

    /** 简介折行只在头部/宽度变化时算一次：每帧折行会白白产生一堆字符串。 */
    private void refreshHeaderLines() {
        if (header == null || header.description() == null || header.description().isBlank()) {
            descriptionLines = List.of();
            return;
        }
        Font font = Minecraft.getInstance().font;
        descriptionLines = wrap(font, header.description(), Math.max(1, width), SUBTITLE_SCALE, HEADER_MAX_LINES);
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        refreshHeaderLines();
    }

    /**
     * 头部右上角的"播放全部"。
     *
     * <p>歌单动辄几百首，逐首点播放按钮不现实；这里给它一个入口，走的是同一条点播命令
     * （{@code /music play playlist <id>}），和聊天栏点播完全等价。</p>
     */
    private void drawHeaderAction(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int left = headerActionLeft();
        int top = headerActionTop();
        int right = left + HEADER_ACTION_WIDTH;
        int bottom = top + HEADER_ACTION_HEIGHT;
        boolean hovered = isMouseOver(mouseX, mouseY)
                && overHeaderAction(mouseX, mouseY);
        float lift = hovered ? 1.0F : 0.30F;
        // 液态玻璃胶囊：和别处的玻璃按钮走同一套材质，悬停时提亮、边缘折射更明显。
        if (!GlassWidgets.glassBackground(graphics, left, top, right, bottom,
                GlassTheme.BUTTON, lift, 0.0F)) {
            GlassWidgets.roundedFill(graphics, left, top, right, bottom,
                    0xFFFFFF, 0xFFFFFF, HEADER_ACTION_HEIGHT / 2.0F,
                    hovered ? 0.20F : 0.12F, 1.0F);
        }
        int textColor = hovered ? GlassTheme.TEXT : GlassWidgets.dim(GlassTheme.TEXT, 0.14F);
        // 文字按字体真实行高（乘缩放）垂直居中，别用写死的 8：LABEL 缩放下行高只有 7.2，
        // 用 8 会把文字顶高半个像素。
        int textHeight = Math.round(font.lineHeight * GlassTheme.Text.LABEL);
        int textY = top + Math.round((HEADER_ACTION_HEIGHT - textHeight) / 2.0F);
        GlassWidgets.centeredText(graphics, font,
                Component.translatable("musicplayer.browser.play_all"),
                (left + right) / 2, textY,
                textColor, GlassTheme.Text.LABEL);
    }

    private void drawStats(GuiGraphics graphics, Font font, List<MediaStat> stats,
                           int x, int y, int maxWidth) {
        if (stats == null || stats.isEmpty()) {
            return;
        }
        int cursor = x;
        for (MediaStat stat : stats) {
            String value = stat.value().getString();
            String label = stat.label().getString();
            // 没有名称的标签（比如时长）只显示数值，免得出现"3:45 时长"这种别扭的组合
            String text = label.isBlank() ? value : value + " " + label;
            int textWidth = Math.round(font.width(text) * SUBTITLE_SCALE);
            int chipWidth = textWidth + 10;
            if (cursor + chipWidth > x + maxWidth) {
                return;
            }
            int chipTop = y - 1;
            int chipBottom = y + 10;
            if (minimal) {
                FlatTheme.fill(graphics, cursor, chipTop, cursor + chipWidth, chipBottom,
                        5.0F, FlatTheme.alpha(0xFFFFFF, 0.06F));
            } else if (!GlassWidgets.glassBackground(graphics, cursor, chipTop,
                    cursor + chipWidth, chipBottom, GlassTheme.BADGE, 0.0F, 0.0F)) {
                GlassWidgets.roundedFill(graphics, cursor, chipTop, cursor + chipWidth, chipBottom,
                        0xFFFFFF, 0xFFFFFF, 5.0F, 0.09F, 1.0F);
            }
            // 文字按缩放后的行高居中，不能写死偏移：这里字号是 0.62 倍，行高只有 5.6 像素，
            // 固定 +1 会让文字整体偏上，看起来"没居中"
            int textY = chipTop + Math.round((chipBottom - chipTop - font.lineHeight * SUBTITLE_SCALE) * 0.5F);
            GlassWidgets.text(graphics, font, Component.literal(text), cursor + 5, textY,
                    minimal ? FlatTheme.TEXT_SECONDARY : GlassTheme.TEXT_SECONDARY, SUBTITLE_SCALE);
            cursor += chipWidth + 4;
        }
    }

    // ── 行 ──────────────────────────────────────────────────────────────

    /**
     * 画一行。
     *
     * <p>一行里从左到右：序号 → 封面 → 标题/副标题 → 补充信息（时长、播放量）→ 播放按钮 → 箭头。
     * 只有封面 + 一行标题的时候，右侧一大片空白会让整行看起来很空，所以补上序号、右对齐的
     * 补充信息和播放按钮；数据源没给就不画那一段，不会留空位。</p>
     */
    private void drawRow(GuiGraphics graphics, Font font, MediaItem item, int index, int top,
                         float reveal, float hoverAmount, float pressAmount, float playPress,
                         float secondaryPress, float trailingPress,
                         boolean queueHovered, boolean secondaryHovered, boolean trailingHovered) {
        if (reveal <= 0.0F) {
            return;
        }
        int left = getX();
        int right = contentRight();
        int rowTop = top + Math.round((1.0F - reveal) * 6.0F);
        int rowBottom = rowTop + ROW_HEIGHT;

        // 列表 item 不用液态玻璃：整行只保留"悬停/按下时浮一层极淡纯色"的反馈，
        // 否则一列玻璃胶囊叠在窗口上方会糊成一片、也压住封面和文字。
        // 行尾的按钮、徽标等小块组件仍然是液态玻璃（见各处 GlassTheme.BUTTON/BADGE）。
        // 只在入场动画早期不画，避免"啪"地出现一块。
        if (reveal > 0.35F) {
            float fill = 0.08F * hoverAmount + 0.05F * pressAmount;
            if (fill > 0.01F) {
                FlatTheme.fill(graphics, left + 2, rowTop + 1, right, rowBottom - 1,
                        6.0F, FlatTheme.alpha(0xFFFFFF, fill));
            }
        }

        // 当前播放行的进度：一块被裁剪到 progress 宽度的主题色液态玻璃。
        // 底层仍能透出世界画面，进度头再补一条细高光，读起来是"玻璃里灌了主题色"，
        // 而不是一条盖在内容上的不透明色块。
        if (reveal > 0.35F && progressRowId != null && progressRowId.equals(item.id())
                && progress > 0.01F) {
            int pLeft = left + 2;
            int pTop = rowTop + 1;
            int pRight = right;
            int pBottom = rowBottom - 1;
            int pFill = pLeft + Math.round((pRight - pLeft) * progress);
            if (pFill > pLeft) {
                GlassWidgets.beginClip(graphics, pLeft, pTop, pFill, pBottom);
                if (!GlassWidgets.glassBackground(graphics, pLeft, pTop, pRight, pBottom,
                        GlassTheme.ROW, 1.0F, 0.0F)) {
                    GlassWidgets.roundedFill(graphics, pLeft, pTop, pRight, pBottom,
                            0xFFFFFF, 0xFFFFFF, 6.0F, 0.10F, 1.0F);
                }
                int accentRgb = FlatTheme.accentRgb();
                GlassWidgets.roundedFill(graphics, pLeft, pTop, pRight, pBottom,
                        accentRgb, accentRgb, 6.0F, 0.34F, 1.0F);
                GlassWidgets.roundedFill(graphics, Math.max(pLeft, pFill - 2), pTop, pFill, pBottom,
                        0xFFFFFF, 0xFFFFFF, 6.0F, 0.28F, 1.0F);
                GlassWidgets.endClip(graphics);
            }
        }

        // 共享元素反向落点：正在飞的那几个元素先不画，避免和目标端重影；
        // 没有参与飞行（没有对应元素）的序号、按钮、箭头仍然照常显示。
        // 过渡结束后 heroActive() 自动为 false，下一帧飞回来的元素原地出现，落点严丝合缝。
        boolean heroRow = heroActive() && heroRowId != null && heroRowId.equals(item.id());
        boolean hideSubtitle = heroRow && (hero.subtitleActive() || hero.albumActive());
        boolean hideMeta = heroRow && hero.metaActive();

        float dim = (1.0F - reveal) * 0.6F;
        int lineY = rowTop + (ROW_HEIGHT - 8) / 2;

        // 序号（从 1 开始，右对齐在左侧窄列）
        int indexColor = minimal
                ? GlassWidgets.dim(FlatTheme.TEXT_FAINT, dim)
                : GlassWidgets.dim(GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.50F), dim);
        GlassWidgets.rightText(graphics, font, Component.literal(Integer.toString(index + 1)),
                left + INDEX_WIDTH + 2, lineY, indexColor, INDEX_SCALE);

        int coverTop = rowTop + (ROW_HEIGHT - COVER_SIZE) / 2;
        int coverLeft = left + INDEX_WIDTH + 4;
        if (!heroRow) {
            drawCover(graphics, coverLeft, coverTop, COVER_SIZE, item.coverUrl(), item.title());
        }

        int textLeft = coverLeft + COVER_SIZE + TEXT_GAP;
        // 右侧从外到内：内边距 → 播放按钮 → 补充信息 → 标题可用宽度
        // （有播放按钮的行不再画右箭头——两者占同一列，箭头会压在按钮上）
        int chevronRight = right - ROW_RIGHT_PADDING;
        int textRight = chevronRight - 9;
        int titleColor = minimal
                ? GlassWidgets.dim(FlatTheme.TEXT, dim)
                : GlassWidgets.dim(GlassTheme.TEXT, dim);
        int subtitleColor = minimal
                ? GlassWidgets.dim(FlatTheme.TEXT_SECONDARY, dim)
                : GlassWidgets.dim(GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.35F), dim);

        boolean showAction = queuable(item) && rowActionsVisible && actionRowAllowed(item);
        if (showAction) {
            if (trailingActionVisible(item)) {
                drawActionButton(graphics, trailingButtonLeft(),
                        rowTop + (ROW_HEIGHT - PLAY_SIZE) / 2,
                        trailingHovered, trailingPress, dim, trailingActionIcon, trailingActionEnabled);
            }
            int buttonLeft = playButtonLeft(item);
            drawActionButton(graphics, buttonLeft, rowTop + (ROW_HEIGHT - PLAY_SIZE) / 2,
                    queueHovered, playPress, dim, actionIcon, actionEnabled);
            textRight = buttonLeft - PLAY_GAP;
            if (secondaryActionVisible(item)) {
                int secondaryLeft = secondaryButtonLeft(item);
                drawActionButton(graphics, secondaryLeft, rowTop + (ROW_HEIGHT - PLAY_SIZE) / 2,
                        secondaryHovered, secondaryPress, dim, secondaryActionIcon, secondaryActionEnabled);
                textRight = secondaryLeft - PLAY_GAP;
            }
        }

        if (item.meta() != null) {
            int metaWidth = Math.round(font.width(item.meta()) * META_SCALE);
            int metaLeft = textRight - metaWidth;
            if (metaLeft > textLeft + 12) {
                if (!hideMeta) {
                    int metaColor = minimal
                            ? GlassWidgets.dim(FlatTheme.TEXT_TERTIARY, dim)
                            : GlassWidgets.dim(GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.22F), dim);
                    GlassWidgets.rightText(graphics, font, Component.literal(item.meta()), textRight, lineY,
                            metaColor, META_SCALE);
                }
                textRight = metaLeft - 6;
            }
        }
        int textWidth = Math.max(1, textRight - textLeft);

        int titleY = item.subtitle() == null ? lineY : rowTop + 8;
        if (!heroRow) {
            GlassWidgets.text(graphics, font,
                    Component.literal(ellipsize(font, item.title(), textWidth, TITLE_SCALE)),
                    textLeft, titleY, titleColor, TITLE_SCALE);
        }
        if (item.subtitle() != null && !hideSubtitle) {
            GlassWidgets.text(graphics, font,
                    Component.literal(ellipsize(font, item.subtitle(), textWidth, SUBTITLE_SCALE)),
                    textLeft, rowTop + 19, subtitleColor, SUBTITLE_SCALE);
        }

        // 右箭头：能点开详情页、且这一行没有播放按钮时才画（播放按钮已占同一列，
        // 再叠箭头会糊在一起；有按钮的行本身就有可点反馈，不必再加箭头）。
        if (navigable(item) && !showAction) {
            int arrowColor = minimal
                    ? GlassWidgets.dim(FlatTheme.TEXT_TERTIARY, dim)
                    : GlassWidgets.dim(GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.55F), dim);
            GlassWidgets.rightText(graphics, font, Component.literal("›"), chevronRight, lineY,
                    arrowColor, GlassTheme.Text.LABEL);
        }
    }

    /**
     * 行尾的操作按钮：一小块圆玻璃 + {@link #actionIcon}。
     *
     * <p>图标走统一的 SVG 组件，不再用手画的加号——字形取自 {@code ＋} 之类的码位要靠 unicode
     * 回退字体，低分辨率下会糊成一团；手画的又和别处图标不是同一套画法。</p>
     *
     * <p>浏览界面里点它发的是 {@code /music play song <id>}：有歌在播时这就是"加入当前播放队列"，
     * 空队列时则是"开始播放"——和聊天栏点播完全是同一条路径。唱片母版界面复用同一套行，
     * 只把图标换成刻录、回调换成发刻录请求（见 {@code DiscMasterScreen}）。</p>
     */
    private void drawActionButton(GuiGraphics graphics, int x, int y, boolean hovered,
                                  float press, float dim, SvgIcon icon, boolean enabled) {
        float pressAmount = GlassWidgets.clamp01(press);
        int color;
        if (minimal) {
            // 悬停淡提亮；按下时再叠一层更明显的填充，形成"被按下去"的反馈。
            float fill = (hovered ? 0.10F : 0.0F) + 0.16F * pressAmount;
            if (fill > 0.01F) {
                FlatTheme.fill(graphics, x, y, x + PLAY_SIZE, y + PLAY_SIZE,
                        PLAY_SIZE / 2.0F, FlatTheme.alpha(0xFFFFFF, fill));
            }
            int base = enabled
                    ? (hovered || pressAmount > 0.01F ? FlatTheme.TEXT : FlatTheme.TEXT_SECONDARY)
                    : FlatTheme.TEXT_FAINT;
            color = GlassWidgets.dim(base, dim);
        } else {
            float lift = (hovered ? 1.0F : 0.25F) * (1.0F - 0.35F * pressAmount);
            if (!GlassWidgets.glassBackground(graphics, x, y, x + PLAY_SIZE, y + PLAY_SIZE,
                    GlassTheme.BUTTON, lift, pressAmount)) {
                GlassWidgets.roundedFill(graphics, x, y, x + PLAY_SIZE, y + PLAY_SIZE,
                        0xFFFFFF, 0xFFFFFF, PLAY_SIZE / 2.0F,
                        0.10F + 0.10F * (hovered ? 1.0F : 0.0F) + 0.12F * pressAmount, 1.0F);
            }
            int enabledDim = GlassWidgets.dim(GlassTheme.TEXT, 0.12F);
            color = enabled
                    ? GlassWidgets.dim(enabledDim, dim)
                    : GlassWidgets.dim(enabledDim, 0.55F);
        }
        icon.drawCentered(graphics, x, y, PLAY_SIZE, PLAY_ICON_SIZE, color);
    }

    private void drawPagination(GuiGraphics graphics, Font font, double mouseX, double mouseY) {
        if (paginationHeight() <= 0) {
            return;
        }
        int top = footerTop();
        int buttonTop = footerButtonTop();
        boolean prev = canGoPrevPage();
        boolean next = canGoNextPage();
        boolean prevHover = prev && overPrevPage(mouseX, mouseY);
        boolean nextHover = next && overNextPage(mouseX, mouseY);

        if (!prev && !next) {
            return;
        }
        drawPageButton(graphics, prevPageLeft(), buttonTop, SvgIcons.CHEVRON_LEFT, prev, prevHover);
        drawPageButton(graphics, nextPageLeft(), buttonTop, SvgIcons.CHEVRON_RIGHT, next, nextHover);

        Component label = totalPages > 0
                ? Component.translatable("musicplayer.browser.page_indicator", pageNumber, totalPages)
                : Component.translatable("musicplayer.browser.page", pageNumber);
        int labelColor = minimal ? FlatTheme.TEXT_SECONDARY : GlassTheme.TEXT_SECONDARY;
        GlassWidgets.centeredText(graphics, font, label, (getX() + contentRight()) / 2,
                top + (PAGE_FOOTER_HEIGHT - 8) / 2, labelColor, GlassTheme.Text.LABEL);
    }

    private void drawPageButton(GuiGraphics graphics, int x, int y,
                                SvgIcon icon, boolean enabled, boolean hovered) {
        int color;
        if (minimal) {
            if (enabled && hovered) {
                FlatTheme.fill(graphics, x, y, x + PAGE_ARROW_SIZE, y + PAGE_ARROW_SIZE,
                        PAGE_ARROW_SIZE / 2.0F, FlatTheme.alpha(0xFFFFFF, 0.10F));
            }
            color = enabled
                    ? (hovered ? FlatTheme.TEXT : FlatTheme.TEXT_TERTIARY)
                    : FlatTheme.TEXT_FAINT;
        } else {
            float lift = enabled ? (hovered ? 1.0F : 0.20F) : 0.10F;
            if (!GlassWidgets.glassBackground(graphics, x, y, x + PAGE_ARROW_SIZE,
                    y + PAGE_ARROW_SIZE, GlassTheme.BUTTON, lift, 0.0F)) {
                GlassWidgets.roundedFill(graphics, x, y, x + PAGE_ARROW_SIZE, y + PAGE_ARROW_SIZE,
                        0xFFFFFF, 0xFFFFFF, PAGE_ARROW_SIZE / 2.0F,
                        enabled ? 0.08F + 0.08F * (hovered ? 1.0F : 0.0F) : 0.04F, 1.0F);
            }
            color = enabled ? GlassTheme.TEXT : GlassWidgets.dim(GlassTheme.TEXT, 0.55F);
        }
        // 图标按墨迹在方框里水平垂直居中（drawCentered 用图标真实墨迹中心，不按 viewBox，
        // 避免每个图标四周留白不同导致视觉偏移）。
        int iconSize = Math.max(6, Math.round(PAGE_ARROW_SIZE * 0.55F));
        icon.drawCentered(graphics, x, y, PAGE_ARROW_SIZE, iconSize, color);
    }

    /**
     * 封面/头像：就绪就贴图，没好就画占位块（不闪、不等待）。
     *
     * <p>贴图走 {@link MediaThumbnails#draw}：在 {@code size × size} 方框内<b>等比内接</b>，
     * 整张封面都在、不裁切，缩放用线性采样。</p>
     */
    private void drawCover(GuiGraphics graphics, int x, int y, int size, String url, String title) {
        MediaThumbnails.Thumb thumb = url == null || url.isBlank()
                ? null : MediaThumbnails.getInstance().get(url);
        if (thumb != null) {
            MediaThumbnails.draw(graphics, thumb, x, y, size);
            return;
        }
        drawCoverPlaceholder(graphics, x, y, size);
    }

    /** 封面还没就绪时的占位：圆角底 + 中间一小块更亮的形状。 */
    private void drawCoverPlaceholder(GuiGraphics graphics, int x, int y, int size) {
        // 占位：圆角底 + 中间一小块更亮的形状，视觉上像"封面位置"而不是空缺
        float radius = size * 0.22F;
        float baseAlpha = minimal ? 0.10F : 0.14F;
        float innerAlpha = minimal ? 0.07F : 0.10F;
        GlassWidgets.roundedFill(graphics, x, y, x + size, y + size, 0xFFFFFF, 0xFFFFFF,
                radius, baseAlpha, 1.0F);
        int inset = Math.max(2, size / 4);
        GlassWidgets.roundedFill(graphics, x + inset, y + inset, x + size - inset, y + size - inset,
                0xFFFFFF, 0xFFFFFF, Math.max(1.0F, radius - 1.0F), innerAlpha, 1.0F);
    }

    /**
     * 滚动条。
     *
     * <p>画在行内容右侧预留的那一列里（{@link #contentRight()} 已经把这块让出来了），
     * 所以它和行不会重叠。一条极淡的轨道 + 一根滑块，只有真的能滚动时才出现。</p>
     */
    private void drawScrollbar(GuiGraphics graphics) {
        double max = maxScroll();
        if (max <= 0.0D) {
            return;
        }
        // 滚动条只在滚动时出现，停手后 400ms 内淡出：常驻一条竖线会一直占着右侧，
        // 而且列表本身已经有行的分隔感，不需要它来做视觉锚点。
        float alpha = GlassWidgets.clamp01((scrollbarUntilMillis - Util.getMillis()) / SCROLLBAR_FADE_MILLIS);
        if (alpha <= 0.02F) {
            return;
        }
        int trackX = getX() + width - SCROLLBAR_WIDTH;
        int trackTop = getY() + 2;
        int trackHeight = height - 4;
        if (trackHeight <= 4) {
            return;
        }
        GlassWidgets.roundedFill(graphics, trackX, trackTop, trackX + SCROLLBAR_WIDTH,
                trackTop + trackHeight, 0xFFFFFF, 0xFFFFFF, SCROLLBAR_WIDTH / 2.0F, 0.05F * alpha, 1.0F);
        int total = Math.max(1, contentHeight());
        int thumbHeight = Math.max(14, trackHeight * height / total);
        int thumbTop = trackTop + (int) Math.round((trackHeight - thumbHeight) * (scroll.position() / max));
        GlassWidgets.roundedFill(graphics, trackX, thumbTop, trackX + SCROLLBAR_WIDTH,
                thumbTop + thumbHeight, 0xFFFFFF, 0xFFFFFF, SCROLLBAR_WIDTH / 2.0F, 0.24F * alpha, 1.0F);
    }

    // ── 状态层 ──────────────────────────────────────────────────────────

    private void drawLoading(GuiGraphics graphics, Font font) {
        int centerX = getX() + width / 2;
        int centerY = getY() + height / 2 - 8;
        drawSpinner(graphics, centerX, centerY, 9);
        int color = minimal
                ? FlatTheme.TEXT_TERTIARY
                : GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.35F);
        GlassWidgets.centeredText(graphics, font, Component.translatable("musicplayer.browser.loading"),
                centerX, centerY + 14, color, GlassTheme.Text.LABEL);
    }

    /** 8 个点绕圈，按时间错开透明度——比一张旋转贴图省事，也不占资源。 */
    private void drawSpinner(GuiGraphics graphics, int centerX, int centerY, int radius) {
        float phase = (Util.getMillis() % 900L) / 900.0F;
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4.0D;
            int x = centerX + (int) Math.round(Math.cos(angle) * radius) - 1;
            int y = centerY + (int) Math.round(Math.sin(angle) * radius) - 1;
            float brightness = (index / 8.0F - phase + 1.0F) % 1.0F;
            float alpha = 0.18F + 0.62F * brightness;
            graphics.fill(x, y, x + 2, y + 2, ((int) (alpha * 255.0F) << 24) | 0xFFFFFF);
        }
    }

    private void drawCenteredStatus(GuiGraphics graphics, Font font, Component text) {
        int color = minimal
                ? FlatTheme.TEXT_TERTIARY
                : GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.40F);
        GlassWidgets.centeredText(graphics, font, text, getX() + width / 2,
                getY() + height / 2 - 4, color, GlassTheme.Text.LABEL);
    }

    // ── 文字工具 ────────────────────────────────────────────────────────

    /** 按缩放后的可用宽度截断并补省略号。 */
    private static String ellipsize(Font font, String text, int pixelWidth, float scale) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int available = Math.max(1, Math.round(pixelWidth / scale));
        if (font.width(text) <= available) {
            return text;
        }
        String ellipsis = "…";
        String clipped = font.plainSubstrByWidth(text, Math.max(0, available - font.width(ellipsis)));
        return clipped + ellipsis;
    }

    /** 简单换行：按可用宽度折成最多 {@code maxLines} 行，最后一行超出补省略号。 */
    private static List<String> wrap(Font font, String text, int pixelWidth, float scale, int maxLines) {
        int available = Math.max(1, Math.round(pixelWidth / scale));
        List<String> lines = new ArrayList<>(maxLines);
        String remaining = text.trim();
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            if (font.width(remaining) <= available) {
                lines.add(remaining);
                remaining = "";
                break;
            }
            String line = font.plainSubstrByWidth(remaining, available);
            if (line.isEmpty()) {
                break;
            }
            lines.add(line);
            remaining = remaining.substring(line.length()).trim();
        }
        if (!remaining.isEmpty() && !lines.isEmpty()) {
            int last = lines.size() - 1;
            lines.set(last, ellipsize(font, lines.get(last) + remaining, pixelWidth, scale));
        }
        return lines;
    }
}
