package com.junhsiun.musicplayer.client.gui.window;

import java.util.Objects;

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
 * <p>采样时需要额外的 {@code travel} 参数（完全移出屏幕的位移量），
 * 原因见 {@link WindowSampler}。</p>
 *
 * @param durationMillis 时长（毫秒），0 表示瞬时
 * @param sampler        采样函数，入参是已播放毫秒数与移出屏幕所需的位移
 */
public record WindowAnimation(long durationMillis, WindowSampler sampler) {

    public WindowAnimation {
        durationMillis = Math.max(0L, durationMillis);
        Objects.requireNonNull(sampler, "sampler");
    }

    /**
     * 不依赖屏幕位置的动画。
     *
     * <p>缩放、弹跳、拉伸这类动画的幅度是固定的，不需要知道窗口离屏幕边缘多远，
     * 用这个构造器即可；{@code travel} 会被忽略。</p>
     */
    public static WindowAnimation of(long durationMillis,
                                     java.util.function.LongFunction<WindowTransform> sampler) {
        Objects.requireNonNull(sampler, "sampler");
        return new WindowAnimation(durationMillis, (elapsed, travel) -> sampler.apply(elapsed));
    }

    /** 采样这一帧的变换，把窗口完全移出屏幕需要 {@code travel} 像素。 */
    public WindowTransform sampleAt(long elapsedMillis, float travel) {
        long clamped = Math.max(0L, Math.min(elapsedMillis, durationMillis));
        return sampler.sample(clamped, Math.max(0.0F, travel));
    }

    /** 假设不需要屏幕位移的采样，仅供不关心 {@code travel} 的动画使用。 */
    public WindowTransform sampleAt(long elapsedMillis) {
        return sampleAt(elapsedMillis, 0.0F);
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
                (elapsed, travel) -> sampleAt(elapsed - delayMillis, travel));
    }

    /**
     * 按指定的缓动函数重映射进度。
     *
     * <p>注意缓动作用在<b>归一化进度</b>上，所以这里要重新按比例还原成毫秒再交给原采样器。</p>
     */
    public WindowAnimation eased(Easing easing) {
        return new WindowAnimation(durationMillis, (elapsed, travel) -> {
            float easedProgress = easing.apply(progressAt(elapsed));
            long remapped = Math.round(easedProgress * durationMillis);
            return sampler.sample(Math.max(0L, Math.min(remapped, durationMillis)), travel);
        });
    }

    /** 按进度缩放整体幅度，用来统一调节"动得多厉害"。 */
    public WindowAnimation scaled(float factor) {
        return new WindowAnimation(durationMillis, (elapsed, travel) -> {
            WindowTransform transform = sampleAt(elapsed, travel);
            return new WindowTransform(
                    transform.offsetX() * factor,
                    transform.offsetY() * factor,
                    1.0F + (transform.scaleX() - 1.0F) * factor,
                    1.0F + (transform.scaleY() - 1.0F) * factor);
        });
    }
}
