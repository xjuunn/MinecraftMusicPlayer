package com.junhsiun.musicplayer.client.gui.window;

/**
 * 一帧的窗口变换：相对基准位置的平移，加上绕窗口中心的缩放。
 *
 * <p>这是整个动画系统的通用货币——弹出、弹跳、拉伸、滑入全部归结为"某一时刻的
 * 一个 {@code WindowTransform}"。多个动画通过 {@link #combine(WindowTransform)}
 * 叠加：位移相加、缩放相乘。</p>
 *
 * <p>缩放以窗口中心为支点，所以窗口"从中心长出来"。同一条变换也会作用到窗口里的
 * 控件上，让内容和容器一起动。</p>
 *
 * @param offsetX 水平位移（GUI 像素，右为正）
 * @param offsetY 垂直位移（GUI 像素，下为正）
 * @param scaleX  水平缩放（1 为原始尺寸）
 * @param scaleY  垂直缩放
 */
public record WindowTransform(float offsetX, float offsetY, float scaleX, float scaleY) {

    public static final WindowTransform IDENTITY = new WindowTransform(0.0F, 0.0F, 1.0F, 1.0F);

    /** 位移相加、缩放相乘。交换律成立，所以合成顺序不影响结果。 */
    public WindowTransform combine(WindowTransform other) {
        return new WindowTransform(
                offsetX + other.offsetX,
                offsetY + other.offsetY,
                scaleX * other.scaleX,
                scaleY * other.scaleY);
    }

    /** 把变换作用到窗口矩形上，得到这一帧真正要绘制的矩形。 */
    public WindowBounds apply(WindowBounds base) {
        float centerX = base.centerX();
        float centerY = base.centerY();
        float halfWidth = base.width() * 0.5F * scaleX;
        float halfHeight = base.height() * 0.5F * scaleY;
        float x0 = centerX - halfWidth + offsetX;
        float y0 = centerY - halfHeight + offsetY;
        return new WindowBounds(
                Math.round(x0), Math.round(y0),
                Math.round(x0 + halfWidth * 2.0F), Math.round(y0 + halfHeight * 2.0F));
    }

    /** 对单个点施加同一条变换，用于让控件跟着窗口一起平移和缩放。 */
    public float applyX(float x, float centerX) {
        return centerX + (x - centerX) * scaleX + offsetX;
    }

    public float applyY(float y, float centerY) {
        return centerY + (y - centerY) * scaleY + offsetY;
    }
}
