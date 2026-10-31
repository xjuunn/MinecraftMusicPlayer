package com.junhsiun.musicplayer.client.gui.window;

/**
 * 窗口停靠位置，默认 {@link #CENTER}。
 *
 * <p>两个分量是"剩余空间的使用比例"：0 贴左/上，0.5 居中，1 贴右/下。按比例而不是按
 * 固定像素，是为了让同一个锚点在不同分辨率下表现一致。</p>
 */
public enum WindowAnchor {

    TOP_LEFT(0.0F, 0.0F),
    TOP(0.5F, 0.0F),
    TOP_RIGHT(1.0F, 0.0F),
    LEFT(0.0F, 0.5F),
    CENTER(0.5F, 0.5F),
    RIGHT(1.0F, 0.5F),
    BOTTOM_LEFT(0.0F, 1.0F),
    BOTTOM(0.5F, 1.0F),
    BOTTOM_RIGHT(1.0F, 1.0F);

    private final float horizontal;
    private final float vertical;

    WindowAnchor(float horizontal, float vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    /** 按锚点和偏移算出窗口矩形。 */
    public WindowBounds layout(int screenWidth, int screenHeight, int width, int height,
                               int offsetX, int offsetY) {
        int x = Math.round((screenWidth - width) * horizontal) + offsetX;
        int y = Math.round((screenHeight - height) * vertical) + offsetY;
        return WindowBounds.of(x, y, width, height);
    }
}
