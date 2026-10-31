package com.junhsiun.musicplayer.client.gui.window;

import java.util.Objects;
import java.util.function.LongFunction;

/**
 * 一段窗口动画：给定"已经播放了多久"，算出这一帧的 {@link WindowTransform}。
 *
 * <p>刻意做成"时间 → 变换"的纯函数，而不是有状态的对象。好处是：</p>
 * <ul>
 *   <li>可以直接用 lambda 自定义，不需要继承；</li>
 *   <li>任何时刻都能重新求值，跳帧、暂停、重播都不会错位；</li>
 *   <li>可以自由组合（{@link #and(WindowAnimation)} 并行叠加、{@link #then(WindowAnimation)} 顺序播放）。</li>
 * </ul>
 *
 * @param durationMillis 时长（毫秒），0 表示瞬时
 * @param sampler        采样函数，入参是已播放毫秒数
 */
public record WindowAnimation(long durationMillis, LongFunction<WindowTransform> sampler) {

    public WindowAnimation {
        durationMillis = Math.max(0L, durationMillis);
        Objects.requireNonNull(sampler, "sampler");
    }

    /** 采样这一帧的变换。超出时长的部分夹到末尾，所以动画结束后会稳定停在终态。 */
    public WindowTransform sampleAt(long elapsedMillis) {
        return sampler.apply(Math.max(0L, Math.min(elapsedMillis, durationMillis)));
    }

    /** 归一化播放进度 0..1。 */
    public float progressAt(long elapsedMillis) {
        if (durationMillis <= 0L) {
            return 1.0F;
        }
        float progress = (float) elapsedMillis / (float) durationMillis;
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    public boolean isFinished(long elapsedMillis) {
        return elapsedMillis >= durationMillis;
    }

    /** 和另一段动画<b>同时</b>播放（时长取两者较大值）。 */
    public WindowAnimation and(WindowAnimation other) {
        return WindowAnimations.together(this, other);
    }

    /** 在另一段动画<b>之后</b>播放。 */
    public WindowAnimation then(WindowAnimation next) {
        return WindowAnimations.sequence(this, next);
    }

    /** 平移整段动画的时间轴，用来做"延迟出现"。 */
    public WindowAnimation delayed(long delayMillis) {
        return new WindowAnimation(durationMillis + Math.max(0L, delayMillis),
                elapsed -> sampleAt(elapsed - delayMillis));
    }

    /**
     * 按指定的缓动函数重映射进度。
     *
     * <p>注意缓动作用在<b>归一化进度</b>上，所以这里要重新按比例还原成毫秒再交给原采样器。</p>
     */
    public WindowAnimation eased(Easing easing) {
        return new WindowAnimation(durationMillis, elapsed -> {
            float eased = easing.apply(progressAt(elapsed));
            long remapped = Math.round(eased * durationMillis);
            return sampler.apply(Math.max(0L, remapped));
        });
    }

    /** 按进度缩放整体幅度，用来统一调节"动得多厉害"。 */
    public WindowAnimation scaled(float factor) {
        return new WindowAnimation(durationMillis, elapsed -> {
            WindowTransform transform = sampleAt(elapsed);
            return new WindowTransform(
                    transform.offsetX() * factor,
                    transform.offsetY() * factor,
                    1.0F + (transform.scaleX() - 1.0F) * factor,
                    1.0F + (transform.scaleY() - 1.0F) * factor);
        });
    }
}
