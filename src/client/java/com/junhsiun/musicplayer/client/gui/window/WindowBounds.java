package com.junhsiun.musicplayer.client.gui.window;

/**
 * 一个轴对齐矩形（GUI 像素，左上原点，右下开区间）。
 *
 * <p>窗口框架里所有的几何计算都以此为单位：窗口本身、内容区、控件位置。</p>
 */
public record WindowBounds(int x0, int y0, int x1, int y1) {

    public static final WindowBounds EMPTY = new WindowBounds(0, 0, 0, 0);

    public static WindowBounds of(int x, int y, int width, int height) {
        return new WindowBounds(x, y, x + width, y + height);
    }

    public int width() {
        return x1 - x0;
    }

    public int height() {
        return y1 - y0;
    }

    /** 宽或高非正即视为不可见，调用方据此跳过绘制。 */
    public boolean isEmpty() {
        return x1 <= x0 || y1 <= y0;
    }

    public float centerX() {
        return (x0 + x1) * 0.5F;
    }

    public float centerY() {
        return (y0 + y1) * 0.5F;
    }

    /** 四边各内缩，用来算内容区。 */
    public WindowBounds inset(int dx, int dy) {
        return new WindowBounds(x0 + dx, y0 + dy, x1 - dx, y1 - dy);
    }

    public WindowBounds offset(int dx, int dy) {
        return new WindowBounds(x0 + dx, y0 + dy, x1 + dx, y1 + dy);
    }

    /** 把矩形夹进屏幕，避免窗口被拖到看不见的地方。 */
    public WindowBounds clampTo(int screenWidth, int screenHeight) {
        int width = Math.min(width(), screenWidth);
        int height = Math.min(height(), screenHeight);
        int x = Math.max(0, Math.min(x0, screenWidth - width));
        int y = Math.max(0, Math.min(y0, screenHeight - height));
        return of(x, y, width, height);
    }
}
