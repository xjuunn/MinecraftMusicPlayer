package com.junhsiun.musicplayer.client.gui.window;

/**
 * 内置窗口动画工厂。
 *
 * <p>所有动画都是"时间 → {@link WindowTransform}"的纯函数，起点和终点都落在
 * {@link WindowTransform#IDENTITY} 附近，所以可以任意叠加而不会互相干扰。
 * 需要新效果时，优先用 {@link WindowAnimations#together} 组合现有动画；
 * 组合不出来再直接写 lambda 构造 {@link WindowAnimation}。</p>
 *
 * <p>时间单位统一是毫秒，位移单位是 GUI 像素。</p>
 */
public final class WindowAnimations {

    /** 升起（位移）时长：偏短，制造"快速弹出"。 */
    private static final long RISE_DURATION = 340L;
    /** 弹跳时长：比升起长，负责落定后的余韵。 */
    private static final long RISE_BOUNCE_DURATION = 620L;
    /** 弹跳幅度（GUI 像素）。 */
    private static final float RISE_BOUNCE_AMPLITUDE = 14.0F;
    /** 拉伸时长与幅度。 */
    private static final long RISE_STRETCH_DURATION = 560L;
    private static final float RISE_STRETCH_AMPLITUDE = 0.045F;

    /** 弹跳 / 拉伸的作用轴。 */
    public enum Axis {
        /** 左右 */
        HORIZONTAL,
        /** 上下 */
        VERTICAL,
        /** 两个轴同时 */
        BOTH
    }

    private WindowAnimations() {
    }

    /** 不做任何动画。 */
    public static WindowAnimation none() {
        return WindowAnimation.of(0L, elapsed -> WindowTransform.IDENTITY);
    }

    /**
     * 从缩小状态弹到正常大小。
     *
     * <p>配合 {@link Easing#EASE_OUT_BACK} 会先冲过 1.0 再回落，这就是"弹出时弹一下"。
     * 缩放以窗口中心为支点。</p>
     *
     * @param fromScale 起始缩放，例如 0.86 表示从 86% 大小长出来
     */
    public static WindowAnimation pop(long durationMillis, float fromScale, Easing easing) {
        return WindowAnimation.of(durationMillis, elapsed -> {
            float progress = progress(elapsed, durationMillis);
            float scale = fromScale + (1.0F - fromScale) * easing.apply(progress);
            return new WindowTransform(0.0F, 0.0F, scale, scale);
        });
    }

    /** {@link #pop(long, float, Easing)} 的常用默认值。 */
    public static WindowAnimation pop(long durationMillis) {
        return pop(durationMillis, 0.86F, Easing.EASE_OUT_BACK);
    }

    /**
     * 沿指定轴来回弹跳后归位。
     *
     * @param amplitude    最大偏移量（GUI 像素）
     * @param oscillations 来回次数，{@code 2} 就是"弹两下"
     * @param axis         作用轴，左右用 {@link Axis#HORIZONTAL}，上下用 {@link Axis#VERTICAL}
     */
    public static WindowAnimation bounce(long durationMillis, float amplitude,
                                         float oscillations, Axis axis) {
        return WindowAnimation.of(durationMillis, elapsed -> {
            float value = wave(elapsed, durationMillis, oscillations, amplitude);
            return switch (axis) {
                case HORIZONTAL -> new WindowTransform(value, 0.0F, 1.0F, 1.0F);
                case VERTICAL -> new WindowTransform(0.0F, value, 1.0F, 1.0F);
                case BOTH -> new WindowTransform(value, value, 1.0F, 1.0F);
            };
        });
    }

    /**
     * 弹性拉伸形变：两个轴的缩放反向振荡，看起来像有弹性的胶体。
     *
     * <p>横向拉伸时左压扁、纵向同时鼓起来（近似保持面积），这是"果冻感"的关键；
     * 两个轴同向振荡就退化成呼吸式的整体缩放。</p>
     *
     * @param amplitude 形变幅度，0.05 约等于 ±5% 缩放
     */
    public static WindowAnimation stretch(long durationMillis, float amplitude,
                                          float oscillations, Axis axis) {
        return WindowAnimation.of(durationMillis, elapsed -> {
            float value = wave(elapsed, durationMillis, oscillations, amplitude);
            return switch (axis) {
                case HORIZONTAL -> new WindowTransform(0.0F, 0.0F, 1.0F + value, 1.0F - value);
                case VERTICAL -> new WindowTransform(0.0F, 0.0F, 1.0F - value, 1.0F + value);
                case BOTH -> new WindowTransform(0.0F, 0.0F, 1.0F + value, 1.0F + value);
            };
        });
    }

    /**
     * 从偏移位置滑入并停在基准位置。
     *
     * @param fromX 起点相对基准位置的偏移，正数表示从右边滑进来
     */
    public static WindowAnimation slide(long durationMillis, float fromX, float fromY, Easing easing) {
        return WindowAnimation.of(durationMillis, elapsed -> {
            float progress = progress(elapsed, durationMillis);
            float remaining = 1.0F - easing.apply(progress);
            return new WindowTransform(fromX * remaining, fromY * remaining, 1.0F, 1.0F);
        });
    }

    /** 一边滑入一边弹跳，常用的"从左弹进来"。 */
    public static WindowAnimation slideAndBounce(long durationMillis, float fromX, float fromY,
                                                 float amplitude, float oscillations, Axis axis) {
        return together(
                slide(durationMillis, fromX, fromY, Easing.EASE_OUT_CUBIC),
                bounce(durationMillis, amplitude, oscillations, axis));
    }

    /** 原地弹出 + 轻微拉伸 + 水平来回弹两下（不位移的开场）。 */
    public static WindowAnimation elasticPop() {
        return together(
                pop(280L, 0.86F, Easing.EASE_OUT_BACK),
                stretch(420L, 0.035F, 2.0F, Axis.HORIZONTAL),
                bounce(520L, 12.0F, 2.0F, Axis.HORIZONTAL));
    }

    /**
     * 默认开场：从<b>屏幕外</b>下方滑入，伴随上下弹跳和拉伸形变。
     *
     * <p>三段并行叠加，各自负责一个维度，互不干扰：</p>
     * <ul>
     *   <li>{@code slide} 从{@link #travel}（完全移出屏幕下边缘的距离）处升到基准位置，
     *       所以起点在屏幕之外，窗口是"整体移进来"而不是在画面中间凭空出现；</li>
     *   <li>{@code bounce} 负责上下弹两下，阻尼正弦末段收敛到 0，所以落点精确；</li>
     *   <li>{@code stretch} 负责纵向拉伸，与弹跳同频，看起来像上升时被拉长、落定时回弹压扁。</li>
     * </ul>
     *
     * <p>位移单位是 GUI 像素，正值向下；起点用正值才表示"从下面升上来"。</p>
     *
     * <p><b>为什么起点要用 travel 而不是固定像素：</b>早先用的是固定
     * {@code 110px}，但居中窗口离屏幕下边缘往往有 200~400px，滑完那 110px 时
     * 窗口早已整个可见，剩下的位移只是小幅上浮——观感就是"突然出现后抖一下"。
     * 用 travel 才能保证第一帧确实在屏幕之外。</p>
     */
    public static WindowAnimation riseFromBottom() {
        return together(
                slideFromBelow(RISE_DURATION, Easing.EASE_OUT_CUBIC),
                bounce(RISE_BOUNCE_DURATION, RISE_BOUNCE_AMPLITUDE, 2.0F, Axis.VERTICAL),
                stretch(RISE_STRETCH_DURATION, RISE_STRETCH_AMPLITUDE, 2.0F, Axis.VERTICAL));
    }

    /**
     * 从完全移出屏幕下边缘的位置滑到基准位置。
     *
     * <p>位移量等于采样时传入的 {@code travel}，由调用方按窗口和屏幕的实际几何算出。</p>
     */
    public static WindowAnimation slideFromBelow(long durationMillis, Easing easing) {
        return new WindowAnimation(durationMillis, (elapsed, travel) -> {
            float progress = progress(elapsed, durationMillis);
            float remaining = 1.0F - easing.apply(progress);
            return new WindowTransform(0.0F, travel * remaining, 1.0F, 1.0F);
        });
    }

    /**
     * 关闭动画时长：210ms，约 12 帧。
     *
     * <p>在 300ms 要求线以内。位移虽横跨整屏（{@code travel} 通常 300~500px），
     * 但曲线起步就快，不需要为"看得清"而延长。</p>
     */
    private static final long DISMISS_DURATION = 210L;

    /**
     * 默认关闭动画：{@link #riseFromBottom()} 的反向——整体滑出屏幕下边缘。
     *
     * <p><b>不做缩放。</b>关闭时若同时缩小，窗口会在还没离开画面时就缩成一小团然后消失，
     * 看起来仍然是"凭空不见"。既然要求"移动到屏幕外面"，就只做平移：窗口保持原尺寸
     * 一路滑出去，边缘被屏幕裁掉，这才是真正的"移出视野"。</p>
     *
     * <p><b>缓动用 {@link Easing#EASE_OUT_QUAD}</b>（起步极快、收尾平顺），这是"灵动"的来源。
     * 位移 416px 下逐帧实测（60fps）：</p>
     * <ul>
     *   <li>第一帧就移动 63.5px——窗口立刻响应，没有"酝酿"的迟滞感；</li>
     *   <li>末帧只剩 5.7px——缓缓收进屏幕边缘，不是硬生生撞出去。</li>
     * </ul>
     *
     * <p>走过的弯路，都被实测否掉了：</p>
     * <ul>
     *   <li>{@code EASE_IN_CUBIC}（末尾加速）：末帧 73.4px，是匀速的 2.7 倍，
     *       窗口最后几帧像被甩出画面，观感就是"突然消失"；</li>
     *   <li>{@code EASE_IN_OUT_CUBIC}（中段加速）：最大单帧 75.9px，峰值移到中段，更糟；</li>
     *   <li>线性 + 末段收速：最大单帧仅 29px 很平顺，但整体匀速、缺乏生命力，
     *       读起来像机器在拖动窗口，而不是界面主动离场。</li>
     * </ul>
     *
     * <p>关键是让速度峰值落在<b>开头</b>而不是结尾：既快又不突兀。
     * 缓出曲线的速度随时间单调递减，天然满足这点。</p>
     */
    public static WindowAnimation dismiss() {
        return new WindowAnimation(DISMISS_DURATION, (elapsed, travel) -> {
            float progress = progress(elapsed, DISMISS_DURATION);
            float eased = Easing.EASE_OUT_QUAD.apply(progress);
            return new WindowTransform(0.0F, travel * eased, 1.0F, 1.0F);
        });
    }

    /** 并行播放，时长取最长的一段。{@code travel} 原样透传给每一段。 */
    public static WindowAnimation together(WindowAnimation... parts) {
        long duration = longest(parts);
        return new WindowAnimation(duration, (elapsed, travel) -> {
            WindowTransform result = WindowTransform.IDENTITY;
            for (WindowAnimation part : parts) {
                result = result.combine(part.sampleAt(elapsed, travel));
            }
            return result;
        });
    }

    /** 依次播放，时长是各段之和。{@code travel} 原样透传给每一段。 */
    public static WindowAnimation sequence(WindowAnimation... parts) {
        long total = 0L;
        for (WindowAnimation part : parts) {
            total += part.durationMillis();
        }
        long duration = total;
        return new WindowAnimation(duration, (elapsed, travel) -> {
            WindowTransform result = WindowTransform.IDENTITY;
            long cursor = 0L;
            for (WindowAnimation part : parts) {
                long local = elapsed - cursor;
                if (local > 0L) {
                    result = result.combine(part.sampleAt(local, travel));
                }
                cursor += part.durationMillis();
            }
            return result;
        });
    }

    private static long longest(WindowAnimation[] parts) {
        long duration = 0L;
        for (WindowAnimation part : parts) {
            duration = Math.max(duration, part.durationMillis());
        }
        return duration;
    }

    /** 阻尼振荡：起点和终点都精确为 0，因此可以和别的动画自由叠加。 */
    private static float wave(long elapsedMillis, long durationMillis,
                              float oscillations, float amplitude) {
        float progress = progress(elapsedMillis, durationMillis);
        double angle = 2.0 * Math.PI * oscillations * progress;
        return (float) Math.sin(angle) * (1.0F - progress) * amplitude;
    }

    private static float progress(long elapsedMillis, long durationMillis) {
        if (durationMillis <= 0L) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (float) elapsedMillis / (float) durationMillis));
    }
}
