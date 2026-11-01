package com.junhsiun.musicplayer.client.gui.glass;

/**
 * 玻璃的"外观"参数：背景模糊度 + 所在窗口的叠加层浓度。
 *
 * <p>这两项都是<b>编译期</b>常量：界面渲染路径拿不到 {@code RenderPass} 绑定逐 draw uniform，
 * 而顶点格式只有一路 color、已经被六项光学参数按位域占满。所以把它们量化成有限的档位，
 * 同一档位共用一份管线。档位刻意取粗：拖滑块时最多编译十来次，之后全部命中缓存。</p>
 *
 * <p>{@code windowTint} 的存在是为了让<b>子控件把窗口当成背景</b>。按钮、滑块、开关在
 * 视觉上都位于窗口玻璃之内，它们折射的应该是"世界背景 + 窗口那层浅色叠加"，
 * 而不是裸的世界画面。着色器用这个值在采样后合成一次，等价于把窗口本身当作背景。</p>
 *
 * @param blur      背景模糊度 0..1，0 = 完全无模糊（只剩折射畸变）
 * @param windowTint 窗口叠加层的合成浓度 0..1，与 {@code GlassTheme.overlayAlpha()} 一致
 */
public record GlassLook(float blur, float windowTint) {

    /**
     * 模糊度量化的档数。
     *
     * <p>每档一份管线，一种材质最多 {@code BLUR_STEPS + 1} 份。取 10 是为了让默认值
     * 20% 能<b>精确</b>落在档位上（{@code round(0.2 × 10)/10 = 0.2}）；8 档时
     * 0.2 会被量化成 0.25，默认观感与配置值不符。10 档在观感上已经是连续的。</p>
     */
    private static final int BLUR_STEPS = 10;

    /**
     * 窗口叠加层浓度的量化档数。
     *
     * <p>比模糊度取得细得多：这一项决定子控件底色的"白"程度，一旦量化误差偏大，
     * 控件边缘和窗口表面之间就会出现一圈可见色差。实测 8 档最坏会差 16/255，
     * 32 档降到 4/255，肉眼已无法分辨。档数多也不会频繁重编译——
     * 这个值只随透明度滑块变化，不是逐帧量。</p>
     */
    private static final int TINT_STEPS = 32;

    public GlassLook {
        blur = clamp01(blur);
        windowTint = clamp01(windowTint);
    }

    /** 只有模糊度、不含窗口叠加（窗口自身用它，因为窗口不需要把自己当背景）。 */
    public GlassLook(float blur) {
        this(blur, 0.0F);
    }

    /** 量化后的模糊值，用于 shader define。 */
    public float quantizedBlur() {
        return Math.round(blur * BLUR_STEPS) / (float) BLUR_STEPS;
    }

    /** 量化后的窗口叠加浓度，用于 shader define。 */
    public float quantizedWindowTint() {
        return Math.round(windowTint * TINT_STEPS) / (float) TINT_STEPS;
    }

    /** 管线缓存键。 */
    public String key() {
        return quantizedBlur() + "/" + quantizedWindowTint();
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }
}
