package com.junhsiun.musicplayer.client.gui.window;

/**
 * 窗口尺寸策略：根据屏幕大小决定窗口应该多大。
 *
 * <p>做成函数式接口而不是固定值，是为了让尺寸能随分辨率、界面缩放或业务状态变化。
 * 常用策略见各个静态工厂；要自定义直接写 lambda，例如
 * {@code (sw, sh) -> new Dimensions(sw / 3, 240)}。</p>
 */
@FunctionalInterface
public interface WindowSize {

    Dimensions resolve(int screenWidth, int screenHeight);

    /** 解析后的实际尺寸，至少 1 像素。 */
    record Dimensions(int width, int height) {
        public Dimensions {
            width = Math.max(1, width);
            height = Math.max(1, height);
        }
    }

    /** 固定尺寸。屏幕装不下时由 {@link WindowBounds#clampTo(int, int)} 兜底。 */
    static WindowSize fixed(int width, int height) {
        return (screenWidth, screenHeight) -> new Dimensions(width, height);
    }

    /** 首选尺寸，四周至少留出 {@code margin}，空间不足就等比例收缩。 */
    static WindowSize preferred(int width, int height, int margin) {
        return (screenWidth, screenHeight) -> new Dimensions(
                Math.min(width, screenWidth - margin * 2),
                Math.min(height, screenHeight - margin * 2));
    }

    /** 铺满屏幕并留出边距。 */
    static WindowSize fill(int marginX, int marginY) {
        return (screenWidth, screenHeight) -> new Dimensions(
                screenWidth - marginX * 2,
                screenHeight - marginY * 2);
    }

    /** 按屏幕比例。 */
    static WindowSize fraction(float widthFraction, float heightFraction) {
        return (screenWidth, screenHeight) -> new Dimensions(
                Math.round(screenWidth * widthFraction),
                Math.round(screenHeight * heightFraction));
    }

    /** 在已有策略之上再限制最大尺寸。 */
    default WindowSize cappedAt(int maxWidth, int maxHeight) {
        return (screenWidth, screenHeight) -> {
            Dimensions base = resolve(screenWidth, screenHeight);
            return new Dimensions(
                    Math.min(base.width(), maxWidth),
                    Math.min(base.height(), maxHeight));
        };
    }
}
