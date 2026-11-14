package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.GlassStyle;
import com.junhsiun.musicplayer.client.gui.glass.layout.GlassLayout;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.window.WindowBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 窗口内的<b>第二层玻璃卡片</b>：既能当作玻璃绘制单元，又能当作布局容器。
 *
 * <h2>用法</h2>
 *
 * <pre>{@code
 * GlassCard card = GlassCard.of(GlassTheme.CARD)
 *         .title("播放列表")
 *         .padding(12);
 * card.content(area -> {
 *     GlassLayout.Column column = new GlassLayout.Column().gap(6);
 *     column.fill(16, slot -> addRenderableWidget(new GlassLabel(...)));
 *     column.layout(area);
 * });
 * card.layout(new GlassLayout.Slot(16, 16, 200, 300));
 * addRenderableWidget(card);
 * }</pre>
 *
 * <p>典型用法是在 {@code GlassWindowScreen.buildContent} 里用 {@link GlassLayout.Row}
 * 切出左右两栏，各放一张卡片：</p>
 *
 * <pre>{@code
 * new GlassLayout.Row().gap(12)
 *         .add(180, height, slot -> leftCard.layout(slot))
 *         .grow(height, slot -> rightCard.layout(slot))
 *         .layout(content);
 * }</pre>
 *
 * <h2>为什么卡片用 {@code childLook} 而不是窗口的 {@code look}</h2>
 *
 * <p>卡片位于窗口玻璃<b>之内</b>，它折射的应该是"世界背景 + 窗口那层浅色叠加"，
 * 而不是裸的世界画面。{@code GlassPanel.drawTuned} 走的正是带窗口叠加浓度的
 * {@code childLook}，所以卡片看起来是嵌在窗口里的一层，而不是浮在窗口外面。</p>
 *
 * <h2>为什么圆角要跟着尺寸夹</h2>
 *
 * <p>着色器取 {@code min(材质半径, 半宽, 半高)}，叠加层必须用<b>同一套规则</b>，
 * 否则轮廓对不上，会多出一圈可见的圆角矩形（控件上曾出现过这个问题）。
 * 这里由 {@link GlassPanel#drawTuned} 与叠加层共用同一个夹取结果。</p>
 *
 * <h2>尺寸与定位</h2>
 *
 * <p>卡片继承 {@code AbstractWidget}，所以会被 {@code GlassWindowScreen} 登记为
 * 跟随窗口动画的控件；它的坐标就是玻璃矩形的左上角，宽高由布局给定。</p>
 */
public class GlassCard extends AbstractWidget {

    /** 内容区相对卡片边缘的内边距。 */
    private int padding = 8;
    /** 标题与内容区之间的额外间距；没有标题时为 0。 */
    private int titleGap = 4;
    /** 标题文字缩放。 */
    private float titleScale = GlassTheme.Text.LABEL;

    private GlassStyle style;
    private String title;
    private Content content;
    /** 卡片所在的容器矩形，用于让圆角与容器同心；null 表示不参与对齐。 */
    private WindowBounds containerBounds;
    /** 容器圆角半径。 */
    private float containerRadius;
    /** 卡片自身底色浓度，见 {@link #tint(float)}。 */
    private float tint = DEFAULT_TINT;

    /**
     * 卡片底色浓度的默认值。
     *
     * <p>刻意<b>不</b>跟随全局透明度配置：该配置默认就是 0（{@code overlayAlpha()} 也随之为 0），
     * 若卡片也乘同一个系数，卡片会完全失去底色，只剩折射，看上去和窗口表面分不开。
     * 二层玻璃需要一点独立的底，才能读作"窗口里的一块面板"。</p>
     */
    public static final float DEFAULT_TINT = 0.12F;

    /** 内容布局回调：拿到的是<b>已经扣掉内边距</b>的可用区域。 */
    @FunctionalInterface
    public interface Content {
        void layout(WindowBounds area);
    }

    protected GlassCard(int x, int y, int width, int height, GlassStyle style) {
        super(x, y, width, height, Component.empty());
        this.style = style;
        // 卡片本身不接收点击：它只是背景容器，交互由里面的控件负责
        this.active = false;
    }

    // ── 构造 ────────────────────────────────────────────────────────────

    /** 用指定材质创建卡片。 */
    public static GlassCard of(GlassStyle style) {
        return new GlassCard(0, 0, 0, 0, style);
    }

    /** 用默认卡片材质创建。 */
    public static GlassCard create() {
        return of(GlassTheme.CARD);
    }

    // ── 链式配置 ────────────────────────────────────────────────────────

    public GlassCard style(GlassStyle value) {
        this.style = value == null ? GlassTheme.CARD : value;
        return this;
    }

    /** 卡片标题。传 {@code null} 表示不显示标题。 */
    public GlassCard title(String value) {
        this.title = value;
        return this;
    }

    /** 内容区内边距。 */
    public GlassCard padding(int value) {
        this.padding = Math.max(0, value);
        return this;
    }

    /** 标题与内容的间距。 */
    public GlassCard titleGap(int value) {
        this.titleGap = Math.max(0, value);
        return this;
    }

    /** 标题文字缩放。 */
    public GlassCard titleScale(float value) {
        this.titleScale = value;
        return this;
    }

    /**
     * 卡片自身底色浓度 0..1，默认 {@link #DEFAULT_TINT}。
     *
     * <p>传 0 可以让卡片完全透明、只剩折射与边缘高光。</p>
     */
    public GlassCard tint(float value) {
        this.tint = GlassWidgets.clamp01(value);
        return this;
    }

    /** 内容布局回调，见 {@link Content}。 */
    public GlassCard content(Content value) {
        this.content = value;
        return this;
    }

    // ── 布局 ────────────────────────────────────────────────────────────

    /**
     * 只设置卡片的矩形，<b>不</b>执行内容布局。
     *
     * <p>配合 {@link #layoutContent()} 使用，用于"必须先定位、才能注册"的场景：</p>
     *
     * <pre>{@code
     * card.bounds(slot);            // 1. 先定位
     * addRenderableWidget(card);    // 2. 再注册（基准坐标此刻才是正确的）
     * card.layoutContent();         // 3. 最后排内容，内容控件才会叠在卡片之上
     * }</pre>
     *
     * <p><b>顺序不能换。</b>{@code GlassWindowScreen} 在 {@code addRenderableWidget} 那一刻
     * 就把控件当时的坐标记成基准位置，用于之后的窗口动画。若先注册再定位，基准会停在
     * {@code (0, 0)}，窗口开场的平移 / 缩放会把卡片拽到左上角。</p>
     *
     * @return {@code this}
     */
    public GlassCard bounds(WindowBounds value) {
        setX(value.x0());
        setY(value.y0());
        setWidth(value.width());
        setHeight(value.height());
        return this;
    }

    /** 同 {@link #bounds(WindowBounds)}，接受布局槽位。 */
    public GlassCard bounds(GlassLayout.Slot slot) {
        return bounds(slot.toBounds());
    }

    /**
     * 把卡片放到给定槽位，并立即执行内容布局。
     *
     * <p>便捷写法，等价于 {@link #bounds(WindowBounds)} + {@link #layoutContent()}。
     * 只在卡片<b>已经注册</b>时才可以直接用，否则会打乱基准坐标。</p>
     *
     * @return 卡片可用的内容区（已扣掉内边距与标题）
     */
    public WindowBounds layout(GlassLayout.Slot slot) {
        return layout(slot.toBounds());
    }

    /** 同上，接受一个矩形区域。 */
    public WindowBounds layout(WindowBounds bounds) {
        bounds(bounds);
        return layoutContent();
    }

    // ── 圆角与容器对齐 ──────────────────────────────────────────────────

    /**
     * 声明卡片所在的容器矩形（通常是窗口整体）。
     *
     * <p>设置后，卡片会自动把圆角算成<b>与容器圆角同心</b>：
     * 取卡片四边到容器四边的最小距离 {@code m}，圆角取 {@code max(0, 容器半径 − m)}。
     * 这是两层圆角重合的充要条件——圆心都落在容器角的
     * {@code (半径, 半径)} 处。若卡片圆角比该值小，两条弧线会在角落错开，
     * 视觉上就是"窗口角和卡片角圆角不一致"。</p>
     *
     * <p>之所以按<b>实际几何</b>反推而不是用固定的内边距常量：卡片可能被放进任意布局
     * （左右分栏、上方留白等），四边到容器的距离未必相同。取最小值可保证
     * 无论卡片贴哪一边都不会超出容器圆角。</p>
     *
     * @return {@code this}
     */
    public GlassCard container(WindowBounds value, float containerRadius) {
        this.containerBounds = value;
        this.containerRadius = Math.max(0.0F, containerRadius);
        return this;
    }

    /**
     * 卡片当前应采用的圆角半径。
     *
     * <p>没有声明容器时直接用材质里的半径；声明了则与容器圆角同心。</p>
     */
    public float resolvedRadius() {
        if (containerBounds == null) {
            return style.radius();
        }
        int margin = Math.min(
                Math.min(getX() - containerBounds.x0(), containerBounds.x1() - (getX() + getWidth())),
                Math.min(getY() - containerBounds.y0(), containerBounds.y1() - (getY() + getHeight())));
        if (margin < 0) {
            // 卡片超出容器（布局尚未就位）：退回材质半径，别算出负圆角
            return style.radius();
        }
        return Math.max(0.0F, containerRadius - margin);
    }

    /**
     * 按当前矩形重新排内容区。坐标或尺寸变化后需要再调一次。
     *
     * @return 内容区
     */
    public WindowBounds layoutContent() {
        WindowBounds inner = contentBounds();
        if (content != null && !inner.isEmpty()) {
            content.layout(inner);
        }
        return inner;
    }

    /** 扣掉内边距与标题占位后的可用区域。 */
    public WindowBounds contentBounds() {
        int top = padding + titleHeight();
        return new WindowBounds(getX() + padding, getY() + top,
                getX() + getWidth() - padding, getY() + getHeight() - padding);
    }

    /** 标题占用的高度（含 {@link #titleGap}）；无标题时为 0。 */
    private int titleHeight() {
        if (title == null || title.isBlank()) {
            return 0;
        }
        return Math.round(8.0F * titleScale) + titleGap;
    }

    public GlassStyle style() {
        return style;
    }

    public int padding() {
        return padding;
    }

    // ── 绘制 ────────────────────────────────────────────────────────────

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x0 = getX();
        int y0 = getY();
        int x1 = getX() + getWidth();
        int y1 = getY() + getHeight();
        if (x1 <= x0 || y1 <= y0) {
            return;
        }

        // 圆角按「与容器同心」解析，再派生出一份只改半径的材质。
        // 玻璃与叠加层必须共用<b>同一个</b>半径，否则两者轮廓对不上，会多出一圈色块。
        float radius = resolvedRadius();
        GlassStyle drawn = style.withRadius(radius);

        // 卡片是"嵌在窗口里的玻璃"：走 drawTuned 才会用带窗口叠加浓度的 childLook。
        // 失败时退回圆角填充，保证界面依旧可用。
        boolean ok = GlassWidgets.glassBackground(graphics, x0, y0, x1, y1, drawn, 0.0F, 0.0F);
        if (!ok) {
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1,
                    drawn.fallback() & 0xFFFFFF, drawn.fallback() & 0xFFFFFF,
                    radius, 0.70F, 1.0F);
        } else if (tint > 0.01F) {
            // 与着色器同一套夹取规则：min(半径, 半宽, 半高)
            float clamped = Math.min(radius, Math.min((x1 - x0) * 0.5F, (y1 - y0) * 0.5F));
            int rgb = GlassTheme.overlayColor() & 0xFFFFFF;
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, rgb, rgb, clamped, tint, 1.0F);
        }

        if (title != null && !title.isBlank()) {
            GlassWidgets.text(graphics, Minecraft.getInstance().font,
                    Component.literal(title),
                    x0 + padding, y0 + padding, GlassTheme.TEXT, titleScale);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
