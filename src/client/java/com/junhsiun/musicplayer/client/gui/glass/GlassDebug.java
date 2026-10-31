package com.junhsiun.musicplayer.client.gui.glass;

/**
 * 液态玻璃四项光学参数的运行时覆盖层，供调试使用。
 *
 * <p>这四项（折射、边缘色散、中心色散、斜面宽度）不参与管线定义，而是随顶点色逐 draw
 * 传给着色器（编码见 {@link #encode(GlassStyle)}）。所以调节它们<b>不会触发着色器重编译</b>，
 * 可以连续拖动。</p>
 *
 * <p>{@link GlassStyle} 依旧是这些参数的权威来源：只有被显式设置过的那一项才会覆盖
 * 材质里的值，{@link #reset()} 可以清空全部覆盖，回到材质本身。</p>
 */
public final class GlassDebug {

    /** 折射强度的编码上限（GUI 像素），顶点色只有 8 位，超出会被截断。 */
    public static final float MAX_REFRACTION = 64.0F;
    /** 中心色散的编码上限（GUI 像素）。 */
    public static final float MAX_CENTER_DISPERSION = 32.0F;
    /** 斜面宽度的编码上限（GUI 像素）。 */
    public static final float MAX_BEVEL = 64.0F;

    private static Float refraction;
    private static Float dispersion;
    private static Float centerDispersion;
    private static Float bevel;

    private GlassDebug() {
    }

    public static float refraction(GlassStyle style) {
        return refraction != null ? refraction : style.refraction();
    }

    public static float dispersion(GlassStyle style) {
        return dispersion != null ? dispersion : style.dispersion();
    }

    public static float centerDispersion(GlassStyle style) {
        return centerDispersion != null ? centerDispersion : style.centerDispersion();
    }

    public static float bevel(GlassStyle style) {
        return bevel != null ? bevel : style.bevel();
    }

    public static void refraction(float value) {
        refraction = clamp(value, 0.0F, MAX_REFRACTION);
    }

    public static void dispersion(float value) {
        dispersion = clamp(value, 0.0F, 1.0F);
    }

    public static void centerDispersion(float value) {
        centerDispersion = clamp(value, 0.0F, MAX_CENTER_DISPERSION);
    }

    public static void bevel(float value) {
        bevel = clamp(value, 0.1F, MAX_BEVEL);
    }

    /** 清空全部覆盖，回到 {@link GlassStyle} 里的值。 */
    public static void reset() {
        refraction = null;
        dispersion = null;
        centerDispersion = null;
        bevel = null;
    }

    /**
     * 把四项参数打包成顶点色，随 {@code BlitRenderState} 的 color 字段送进着色器。
     *
     * <p>顶点色是 {@code RGBA8_UNORM}：Java 侧按 ARGB 整数写入，着色器里按 vec4 读出来，
     * 每个通道 0..1，精度 1/255。着色器按 {@code r * 64}、{@code g}、{@code b * 32}、
     * {@code a * 64} 解码。</p>
     */
    public static int encode(GlassStyle style) {
        int r = quantize(refraction(style) / MAX_REFRACTION);
        int g = quantize(dispersion(style));
        int b = quantize(centerDispersion(style) / MAX_CENTER_DISPERSION);
        int a = quantize(bevel(style) / MAX_BEVEL);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int quantize(float normalized) {
        int value = Math.round(clamp(normalized, 0.0F, 1.0F) * 255.0F);
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
