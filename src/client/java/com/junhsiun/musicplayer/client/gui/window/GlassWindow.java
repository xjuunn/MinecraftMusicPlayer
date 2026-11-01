package com.junhsiun.musicplayer.client.gui.window;

import com.junhsiun.musicplayer.client.gui.glass.GlassStyle;

/**
 * 一个可复用的液态玻璃窗口：尺寸策略 + 停靠位置 + 材质 + 开场动画 + 内容内边距。
 *
 * <p>这是纯配置对象，不持有任何渲染状态，也不依赖界面生命周期，因此可以在任何地方构造、
 * 复用同一份配置开多个界面。所有 setter 都是链式的，方便一次性声明：</p>
 *
 * <pre>{@code
 * GlassWindow window = new GlassWindow()
 *         .style(GlassStyle.WINDOW)
 *         .size(WindowSize.preferred(880, 560, 32))
 *         .anchor(WindowAnchor.CENTER)
 *         .opening(WindowAnimations.riseFromBottom());
 * }</pre>
 *
 * <p>几何计算分三层，互不耦合：</p>
 * <ol>
 *   <li>{@link #layout(int, int)}：尺寸策略 + 锚点 + 偏移 → 基准矩形；</li>
 *   <li>{@link WindowAnimation}：时间 → 变换；</li>
 *   <li>{@link #transform(long)} + {@link WindowTransform#apply(WindowBounds)} → 这一帧的矩形。</li>
 * </ol>
 */
public final class GlassWindow {

    private GlassStyle style = GlassStyle.WINDOW;
    private WindowSize size = WindowSize.preferred(880, 560, 32);
    private WindowAnchor anchor = WindowAnchor.CENTER;
    private WindowAnimation opening = WindowAnimations.riseFromBottom();
    private int offsetX;
    private int offsetY;
    private int contentPaddingX = 24;
    private int contentPaddingY = 24;
    private boolean clampToScreen = true;

    public GlassWindow style(GlassStyle value) {
        this.style = value;
        return this;
    }

    public GlassWindow size(WindowSize value) {
        this.size = value;
        return this;
    }

    public GlassWindow anchor(WindowAnchor value) {
        this.anchor = value;
        return this;
    }

    public GlassWindow opening(WindowAnimation value) {
        this.opening = value;
        return this;
    }

    /** 相对锚点位置的额外偏移，正数向右 / 向下。 */
    public GlassWindow offset(int x, int y) {
        this.offsetX = x;
        this.offsetY = y;
        return this;
    }

    /** 内容区相对窗口四边的内缩量。 */
    public GlassWindow contentPadding(int x, int y) {
        this.contentPaddingX = Math.max(0, x);
        this.contentPaddingY = Math.max(0, y);
        return this;
    }

    /** 是否把窗口夹进屏幕。关闭后可以用偏移把窗口放到屏幕外。 */
    public GlassWindow clampToScreen(boolean value) {
        this.clampToScreen = value;
        return this;
    }

    public GlassStyle style() {
        return style;
    }

    public WindowAnimation opening() {
        return opening;
    }

    /** 基准矩形：未经动画变换的窗口位置和大小。 */
    public WindowBounds layout(int screenWidth, int screenHeight) {
        WindowSize.Dimensions dimensions = size.resolve(screenWidth, screenHeight);
        WindowBounds bounds = anchor.layout(screenWidth, screenHeight,
                dimensions.width(), dimensions.height(), offsetX, offsetY);
        return clampToScreen ? bounds.clampTo(screenWidth, screenHeight) : bounds;
    }

    /** 内容区：基准矩形四边各内缩一个内边距。 */
    public WindowBounds contentBounds(WindowBounds windowBounds) {
        return windowBounds.inset(contentPaddingX, contentPaddingY);
    }

    /**
     * 开场动画在某一时刻的变换。
     *
     * @param elapsedMillis 已播放毫秒数
     * @param travel        完全移出屏幕下边缘所需的垂直位移，由调用方按实际几何算出
     */
    public WindowTransform transform(long elapsedMillis, float travel) {
        return opening.sampleAt(elapsedMillis, travel);
    }
}
