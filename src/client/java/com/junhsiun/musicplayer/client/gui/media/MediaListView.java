package com.junhsiun.musicplayer.client.gui.media;

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
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

        /** 点了行尾的操作按钮（默认是"加入播放队列"，唱片母版界面是"刻录"）。 */
        void onPlay(MediaItem item);

        /** 点了列表末尾的"加载更多"。 */
        void onLoadMore();

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
    /** 行尾按钮当前是否可点。刻录请求在途时置 {@code false}，避免连点重复刻录。 */
    private boolean actionEnabled = true;

    private Header header;
    private List<String> descriptionLines = List.of();
    private List<MediaItem> items = List.of();
    private boolean hasMore;
    private boolean loadingMore;
    private boolean loading;
    private Component status = Component.empty();
    private int hoveredRow = -1;
    private int pressedRow = -1;
    private boolean pressActive;

    private long revealStartMillis = Util.getMillis();
    /** 滚动条显示到什么时候（毫秒时间戳）。滚动一下续期，停手后自己淡出。 */
    private long scrollbarUntilMillis;
    private long transitionStartMillis = -1L;
    private boolean transitionForward = true;

    public MediaListView(int x, int y, int width, int height, Listener listener) {
        super(x, y, width, height, Component.empty());
        this.listener = listener;
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
        this.hasMore = hasMore;
        this.loadingMore = loadingMore;
        this.hoveredRow = -1;
        this.pressedRow = -1;
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
        int extra = hasMore ? ROW_HEIGHT : 0;
        return headerHeight() + items.size() * ROW_HEIGHT + extra + BOTTOM_PADDING;
    }

    private double maxScroll() {
        return Math.max(0.0D, contentHeight() - height);
    }

    /** 第 {@code index} 行的上沿（已含头部偏移与滚动偏移）。 */
    private int rowTop(int index) {
        return getY() + headerHeight() + index * ROW_HEIGHT - scroll.offset();
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
     */
    private int playButtonRight() {
        return contentRight() - ROW_RIGHT_PADDING;
    }

    /** 播放按钮左边界。 */
    private int playButtonLeft() {
        return playButtonRight() - PLAY_SIZE;
    }

    /** 鼠标是否落在某一行的操作按钮上。 */
    private int playRowAt(double mouseX, double mouseY) {
        if (!actionEnabled) {
            return -1;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0 || !queuable(items.get(index))) {
            return -1;
        }
        int right = playButtonRight();
        int left = playButtonLeft();
        int top = rowTop(index) + (ROW_HEIGHT - PLAY_SIZE) / 2;
        if (mouseX < left || mouseX > right || mouseY < top || mouseY > top + PLAY_SIZE) {
            return -1;
        }
        return index;
    }


    private boolean overLoadMore(double mouseX, double mouseY) {
        if (!hasMore) {
            return false;
        }
        int top = rowTop(items.size());
        return isMouseOver(mouseX, mouseY) && mouseY >= top && mouseY < top + ROW_HEIGHT;
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

    /** 这一行点下去会不会翻页（歌曲没有详情页，点了不做任何事）。 */
    private static boolean navigable(MediaItem item) {
        return item != null && item.kind() != MediaKind.SONG;
    }

    // ── 交互 ────────────────────────────────────────────────────────────

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (overHeaderAction(event.x(), event.y())) {
            pressActive = true;
            return;
        }
        if (overLoadMore(event.x(), event.y())) {
            pressActive = true;
            return;
        }
        int queueIndex = playRowAt(event.x(), event.y());
        if (queueIndex >= 0) {
            pressedRow = queueIndex;
            pressActive = true;
            return;
        }
        int index = rowAt(event.x(), event.y());
        // 歌曲没有详情页，点行本身不做事：连按下的回弹也不给，免得看起来"点了有反应"
        if (index < 0 || !navigable(items.get(index))) {
            pressedRow = -1;
            return;
        }
        pressedRow = index;
        pressActive = true;
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        int index = pressedRow;
        pressedRow = -1;
        pressActive = false;
        if (overHeaderAction(event.x(), event.y())) {
            listener.onPlayAll();
            return;
        }
        if (overLoadMore(event.x(), event.y())) {
            if (!loadingMore) {
                listener.onLoadMore();
            }
            return;
        }
        if (index < 0) {
            return;
        }
        if (playRowAt(event.x(), event.y()) == index) {
            listener.onPlay(items.get(index));
            return;
        }
        if (rowAt(event.x(), event.y()) != index) {
            return;
        }
        listener.onItemClick(items.get(index));
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        pressedRow = -1;
        pressActive = false;
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
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
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

        drawContent(graphics, font);

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

    private void drawContent(GuiGraphicsExtractor graphics, Font font) {
        prefetchCovers();
        double mouseX = graphics.mouseX();
        double mouseY = graphics.mouseY();
        int hoverIndex = isMouseOver(mouseX, mouseY) ? rowAt(mouseX, mouseY) : -1;
        int hoverPlay = isMouseOver(mouseX, mouseY) ? playRowAt(mouseX, mouseY) : -1;
        hoveredRow = hoverIndex;
        hover.tick(hoverIndex >= 0, GlassTheme.Motion.HOVER);

        // 越界部分由外层的 GlassWidgets.beginClip 统一遮挡（文字走原版 scissor，
        // 玻璃走 scissorArea），这里只做整行的剔除，省掉看不见的那些行的排版开销。
        drawHeader(graphics, font);
        long now = Util.getMillis();
        for (int index = 0; index < items.size(); index++) {
            int top = rowTop(index);
            if (top + ROW_HEIGHT <= getY() || top >= listBottom()) {
                continue;
            }
            float hoverAmount = index == hoverIndex ? hover.eased() : 0.0F;
            float pressAmount = index == pressedRow ? GlassWidgets.clamp01(press.value()) : 0.0F;
            drawRow(graphics, font, items.get(index), index, top, revealProgress(now, index),
                    hoverAmount, pressAmount, index == hoverPlay);
        }
        drawLoadMore(graphics, font, mouseX, mouseY);
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

    private void drawHeader(GuiGraphicsExtractor graphics, Font font) {
        if (header == null) {
            return;
        }
        int left = getX();
        int right = contentRight();
        int top = contentTop() + 4;
        // 头部整体跟着滚动：滚出上边界就整块被裁掉（scissor 已经在外面套好了）

        drawCover(graphics, left, top, HEADER_COVER, header.coverUrl(), text(header.title()));
        int textLeft = left + HEADER_COVER + 8;
        int textWidth = Math.max(1, right - textLeft);
        GlassWidgets.text(graphics, font,
                Component.literal(ellipsize(font, text(header.title()), textWidth, GlassTheme.Text.LABEL)),
                textLeft, top + 2, GlassTheme.TEXT, GlassTheme.Text.LABEL);
        if (header.subtitle() != null && !header.subtitle().getString().isBlank()) {
            GlassWidgets.text(graphics, font,
                    Component.literal(ellipsize(font, header.subtitle().getString(), textWidth, SUBTITLE_SCALE)),
                    textLeft, top + 14, GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.30F), SUBTITLE_SCALE);
        }
        // 有"播放全部"时给小标签让出右侧那一段，避免两者叠在一起
        int statsWidth = header.playAll() ? Math.max(1, textWidth - HEADER_ACTION_WIDTH - 6) : textWidth;
        drawStats(graphics, font, header.stats(), textLeft, top + 25, statsWidth);
        if (header.playAll()) {
            drawHeaderAction(graphics, font);
        }

        if (!descriptionLines.isEmpty()) {
            int y = top + HEADER_COVER + 3;
            for (String line : descriptionLines) {
                GlassWidgets.text(graphics, font, Component.literal(line), left, y,
                        GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.42F), SUBTITLE_SCALE);
                y += 8;
            }
        }
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
    private void drawHeaderAction(GuiGraphicsExtractor graphics, Font font) {
        int left = headerActionLeft();
        int top = headerActionTop();
        int right = left + HEADER_ACTION_WIDTH;
        int bottom = top + HEADER_ACTION_HEIGHT;
        boolean hovered = isMouseOver(graphics.mouseX(), graphics.mouseY())
                && overHeaderAction(graphics.mouseX(), graphics.mouseY());
        if (!GlassWidgets.glassBackground(graphics, left, top, right, bottom,
                GlassTheme.BADGE, hovered ? 1.0F : 0.35F, 0.0F)) {
            GlassWidgets.roundedFill(graphics, left, top, right, bottom,
                    0xFFFFFF, 0xFFFFFF, HEADER_ACTION_HEIGHT / 2.0F,
                    hovered ? 0.18F : 0.10F, 1.0F);
        }
        GlassWidgets.centeredText(graphics, font,
                Component.translatable("musicplayer.browser.play_all"),
                (left + right) / 2, top + (HEADER_ACTION_HEIGHT - 8) / 2,
                hovered ? GlassTheme.TEXT : GlassWidgets.dim(GlassTheme.TEXT, 0.18F),
                GlassTheme.Text.LABEL);
    }

    private void drawStats(GuiGraphicsExtractor graphics, Font font, List<MediaStat> stats,
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
            // 数据小标签也是液态玻璃：BADGE 是三层里最薄的一档
            if (!GlassWidgets.glassBackground(graphics, cursor, chipTop, cursor + chipWidth, chipBottom,
                    GlassTheme.BADGE, 0.0F, 0.0F)) {
                GlassWidgets.roundedFill(graphics, cursor, chipTop, cursor + chipWidth, chipBottom,
                        0xFFFFFF, 0xFFFFFF, 5.0F, 0.09F, 1.0F);
            }
            // 文字按缩放后的行高居中，不能写死偏移：这里字号是 0.62 倍，行高只有 5.6 像素，
            // 固定 +1 会让文字整体偏上，看起来"没居中"
            int textY = chipTop + Math.round((chipBottom - chipTop - font.lineHeight * SUBTITLE_SCALE) * 0.5F);
            GlassWidgets.text(graphics, font, Component.literal(text), cursor + 5, textY,
                    GlassTheme.TEXT_SECONDARY, SUBTITLE_SCALE);
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
    private void drawRow(GuiGraphicsExtractor graphics, Font font, MediaItem item, int index, int top,
                         float reveal, float hoverAmount, float pressAmount, boolean queueHovered) {
        if (reveal <= 0.0F) {
            return;
        }
        int left = getX();
        int right = contentRight();
        int rowTop = top + Math.round((1.0F - reveal) * 6.0F);
        int rowBottom = rowTop + ROW_HEIGHT;

        // 每一行自己就是一小块液态玻璃（不是"悬停时才亮一下的纯色块"）。
        //
        // 只在入场动画早期不画（玻璃没有 alpha 参数，硬画会"啪"地出现一块）。
        // 滚出可见区的那部分不在这里夹几何：整块内容在 extractWidgetRenderState 里
        // 已经开了裁剪，玻璃会作为 scissorArea 一起被裁掉，形状不受影响。
        if (reveal > 0.35F) {
            float lift = 0.18F + 0.82F * hoverAmount;
            if (!GlassWidgets.glassBackground(graphics, left + 2, rowTop + 1, right, rowBottom - 1,
                    GlassTheme.ROW, lift, pressAmount)) {
                GlassWidgets.roundedFill(graphics, left + 2, rowTop + 1, right, rowBottom - 1,
                        0xFFFFFF, 0xFFFFFF, 6.0F,
                        0.05F + 0.09F * hoverAmount + 0.06F * pressAmount, 1.0F);
            }
        }

        float dim = (1.0F - reveal) * 0.6F;
        int lineY = rowTop + (ROW_HEIGHT - 8) / 2;

        // 序号（从 1 开始，右对齐在左侧窄列）
        GlassWidgets.rightText(graphics, font, Component.literal(Integer.toString(index + 1)),
                left + INDEX_WIDTH + 2, lineY,
                GlassWidgets.dim(GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.50F), dim), INDEX_SCALE);

        int coverTop = rowTop + (ROW_HEIGHT - COVER_SIZE) / 2;
        int coverLeft = left + INDEX_WIDTH + 4;
        drawCover(graphics, coverLeft, coverTop, COVER_SIZE, item.coverUrl(), item.title());

        int textLeft = coverLeft + COVER_SIZE + TEXT_GAP;
        // 右侧从外到内：内边距 → 箭头 → 播放按钮 → 补充信息 → 标题可用宽度
        int chevronRight = right - ROW_RIGHT_PADDING;
        int textRight = chevronRight - 9;
        int titleColor = GlassWidgets.dim(GlassTheme.TEXT, dim);
        int subtitleColor = GlassWidgets.dim(GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.35F), dim);

        if (queuable(item)) {
            int buttonLeft = playButtonLeft();
            drawActionButton(graphics, buttonLeft, rowTop + (ROW_HEIGHT - PLAY_SIZE) / 2, queueHovered, dim);
            textRight = buttonLeft - PLAY_GAP;
        }

        if (item.meta() != null) {
            int metaWidth = Math.round(font.width(item.meta()) * META_SCALE);
            int metaLeft = textRight - metaWidth;
            if (metaLeft > textLeft + 12) {
                GlassWidgets.rightText(graphics, font, Component.literal(item.meta()), textRight, lineY,
                        GlassWidgets.dim(GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.22F), dim), META_SCALE);
                textRight = metaLeft - 6;
            }
        }
        int textWidth = Math.max(1, textRight - textLeft);

        int titleY = item.subtitle() == null ? lineY : rowTop + 8;
        GlassWidgets.text(graphics, font,
                Component.literal(ellipsize(font, item.title(), textWidth, TITLE_SCALE)),
                textLeft, titleY, titleColor, TITLE_SCALE);
        if (item.subtitle() != null) {
            GlassWidgets.text(graphics, font,
                    Component.literal(ellipsize(font, item.subtitle(), textWidth, SUBTITLE_SCALE)),
                    textLeft, rowTop + 19, subtitleColor, SUBTITLE_SCALE);
        }

        // 右箭头：只在"点开有下一页"的行上画。歌曲没有详情页，画了会误导
        if (navigable(item)) {
            int arrowColor = GlassWidgets.dim(GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.55F), dim);
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
    private void drawActionButton(GuiGraphicsExtractor graphics, int x, int y, boolean hovered, float dim) {
        float lift = hovered ? 1.0F : 0.25F;
        if (!GlassWidgets.glassBackground(graphics, x, y, x + PLAY_SIZE, y + PLAY_SIZE,
                GlassTheme.BUTTON, lift, 0.0F)) {
            GlassWidgets.roundedFill(graphics, x, y, x + PLAY_SIZE, y + PLAY_SIZE,
                    0xFFFFFF, 0xFFFFFF, PLAY_SIZE / 2.0F, 0.10F + 0.10F * (hovered ? 1.0F : 0.0F), 1.0F);
        }
        // 不可点时图标压暗：按钮还在原处，但一眼看得出"现在点不动"
        int enabledDim = GlassWidgets.dim(GlassTheme.TEXT, 0.12F);
        int color = actionEnabled
                ? GlassWidgets.dim(enabledDim, dim)
                : GlassWidgets.dim(enabledDim, 0.55F);
        actionIcon.drawCentered(graphics, x, y, PLAY_SIZE, PLAY_ICON_SIZE, color);
    }

    private void drawLoadMore(GuiGraphicsExtractor graphics, Font font, double mouseX, double mouseY) {
        if (!hasMore) {
            return;
        }
        int left = getX();
        int top = rowTop(items.size());
        // 不夹几何：越界部分由外层裁剪遮挡，夹了会把圆角压歪
        int moreTop = top + 2;
        int moreBottom = top + ROW_HEIGHT - 2;
        boolean over = overLoadMore(mouseX, mouseY);
        if (!GlassWidgets.glassBackground(graphics, left + 2, moreTop, contentRight(), moreBottom,
                GlassTheme.ROW, over ? 1.0F : 0.20F, 0.0F)) {
            GlassWidgets.roundedFill(graphics, left + 2, moreTop, contentRight(), moreBottom,
                    0xFFFFFF, 0xFFFFFF, 5.0F, 0.06F + 0.07F * (over ? 1.0F : 0.0F), 1.0F);
        }
        Component label = loadingMore
                ? Component.translatable("musicplayer.browser.loading")
                : Component.translatable("musicplayer.browser.load_more");
        GlassWidgets.centeredText(graphics, font, label, (left + contentRight()) / 2,
                (moreTop + moreBottom) / 2 - 4,
                over ? GlassTheme.TEXT : GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.35F),
                GlassTheme.Text.LABEL);
    }

    /**
     * 封面/头像：就绪就贴图，没好就画占位块（不闪、不等待）。
     *
     * <p>贴图走 {@link MediaThumbnails#draw}：在 {@code size × size} 方框内<b>等比内接</b>，
     * 整张封面都在、不裁切，缩放用线性采样。</p>
     */
    private void drawCover(GuiGraphicsExtractor graphics, int x, int y, int size, String url, String title) {
        MediaThumbnails.Thumb thumb = url == null || url.isBlank()
                ? null : MediaThumbnails.getInstance().get(url);
        if (thumb != null) {
            MediaThumbnails.draw(graphics, thumb, x, y, size);
            return;
        }
        // 占位：圆角底 + 中间一小块更亮的形状，视觉上像"封面位置"而不是空缺
        float radius = size * 0.22F;
        GlassWidgets.roundedFill(graphics, x, y, x + size, y + size, 0xFFFFFF, 0xFFFFFF, radius, 0.14F, 1.0F);
        int inset = Math.max(2, size / 4);
        GlassWidgets.roundedFill(graphics, x + inset, y + inset, x + size - inset, y + size - inset,
                0xFFFFFF, 0xFFFFFF, Math.max(1.0F, radius - 1.0F), 0.10F, 1.0F);
    }

    /**
     * 滚动条。
     *
     * <p>画在行内容右侧预留的那一列里（{@link #contentRight()} 已经把这块让出来了），
     * 所以它和行不会重叠。一条极淡的轨道 + 一根滑块，只有真的能滚动时才出现。</p>
     */
    private void drawScrollbar(GuiGraphicsExtractor graphics) {
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

    private void drawLoading(GuiGraphicsExtractor graphics, Font font) {
        int centerX = getX() + width / 2;
        int centerY = getY() + height / 2 - 8;
        drawSpinner(graphics, centerX, centerY, 9);
        GlassWidgets.centeredText(graphics, font, Component.translatable("musicplayer.browser.loading"),
                centerX, centerY + 14, GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.35F),
                GlassTheme.Text.LABEL);
    }

    /** 8 个点绕圈，按时间错开透明度——比一张旋转贴图省事，也不占资源。 */
    private void drawSpinner(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius) {
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

    private void drawCenteredStatus(GuiGraphicsExtractor graphics, Font font, Component text) {
        GlassWidgets.centeredText(graphics, font, text, getX() + width / 2,
                getY() + height / 2 - 4, GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, 0.40F),
                GlassTheme.Text.LABEL);
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
