package com.junhsiun.musicplayer.client.gui.window;

/**
 * 缓动函数：把 0..1 的线性进度映射成动画进度。
 *
 * <p>返回值<b>允许短暂越界</b>（大于 1 或小于 0），这正是"冲过头再弹回来"的来源。
 * 想要自定义手感，实现这个接口传进 {@link WindowAnimations} 的工厂方法即可。</p>
 */
@FunctionalInterface
public interface Easing {

    float apply(float progress);

    Easing LINEAR = progress -> progress;

    Easing EASE_OUT_QUAD = progress -> 1.0F - (1.0F - progress) * (1.0F - progress);

    Easing EASE_OUT_CUBIC = progress -> {
        float inverted = 1.0F - progress;
        return 1.0F - inverted * inverted * inverted;
    };

    Easing EASE_IN_OUT_CUBIC = progress -> {
        if (progress < 0.5F) {
            return 4.0F * progress * progress * progress;
        }
        float shifted = -2.0F * progress + 2.0F;
        return 1.0F - shifted * shifted * shifted * 0.5F;
    };

    /** 冲过头约 10% 再回落，最常用的"弹一下"。 */
    Easing EASE_OUT_BACK = progress -> {
        float overshoot = 1.70158F;
        float scaled = overshoot + 1.0F;
        float inverted = progress - 1.0F;
        return 1.0F + scaled * inverted * inverted * inverted + overshoot * inverted * inverted;
    };

    /** 阻尼弹簧：尾部来回振荡若干次后收敛，弹性最强。 */
    Easing EASE_OUT_ELASTIC = progress -> {
        if (progress <= 0.0F) {
            return 0.0F;
        }
        if (progress >= 1.0F) {
            return 1.0F;
        }
        float period = (2.0F * (float) Math.PI) / 3.0F;
        return (float) Math.pow(2.0F, -10.0F * progress)
                * (float) Math.sin((progress * 10.0F - 0.75F) * period) + 1.0F;
    };
}
