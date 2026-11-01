package com.junhsiun.musicplayer.client.gui.window;

/**
 * 窗口动画的采样函数：给定"已播放多久"和"移出屏幕需要多远"，算出这一帧的变换。
 *
 * <p>为什么需要 {@code travel}：像"从屏幕下方滑入"这类动画，位移量取决于窗口在屏幕上的
 * 实际位置，构造动画时还不知道（那时窗口尺寸、屏幕高度都没定）。所以把它作为采样参数
 * 传进来，而不是写死一个几十像素的常数——写死的常数只会让窗口在画面中间"凭空出现"。</p>
 *
 * <p>{@code travel} 的含义：把窗口<b>完全</b>移出屏幕下边缘所需的垂直位移（GUI 像素）。
 * 位移量等于它时，窗口上边缘正好落在屏幕下边缘之外，也就是彻底看不见。</p>
 *
 * <p>不需要位移的动画（缩放、弹跳、拉伸）直接忽略这个参数即可。</p>
 */
@FunctionalInterface
public interface WindowSampler {

    /**
     * @param elapsedMillis 已播放毫秒数，调用方保证落在 {@code 0..duration} 内
     * @param travel        完全移出屏幕下边缘所需的垂直位移，非负
     */
    WindowTransform sample(long elapsedMillis, float travel);
}
