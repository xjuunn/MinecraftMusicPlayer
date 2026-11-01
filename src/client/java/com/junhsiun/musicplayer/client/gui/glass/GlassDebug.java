package com.junhsiun.musicplayer.client.gui.glass;

import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;

/**
 * 液态玻璃光学参数的<b>配置文件门面</b>。
 *
 * <p>6 项光学参数持久化在模组统一配置文件的 {@code glass} 分节里（见
 * {@link MusicPlayerConfig.GlassSettings}），重启游戏后仍然保留。</p>
 *
 * <p>这些参数不参与管线定义，而是随顶点色逐 draw 传给着色器，所以调节它们
 * <b>不会触发着色器重编译</b>，可以连续拖动。</p>
 *
 * <h2>只有窗口读配置</h2>
 *
 * <p>{@link #refraction(GlassStyle)} 等取值方法会先看传入的材质是不是
 * {@link GlassStyle#WINDOW}：<b>只有窗口</b>采用配置值，按钮 / 开关 / 圆珠继续使用
 * 各自材质里烘焙的数值。</p>
 *
 * <p>这是刻意的。早期实现是"全局静态覆盖、不分材质"，把为窗口调好的数值一并套到了
 * 小控件上：按钮高 16px、短边半尺寸只有 8，而窗口的 {@code refraction} 是 32px，
 * 贴边采样点被外移 400% 的半高，直接推到控件外，表现为中心区域重叠、颜色发浑。
 * 只让窗口消费配置，该问题从结构上不再发生。</p>
 *
 * <h2>为什么要把参数塞进 32 位</h2>
 *
 * <p>界面渲染路径拿不到 {@code RenderPass}，无法绑定逐 draw 的 uniform；而顶点格式只有
 * 一路 {@code vec4} 顶点色，也就是 4 个 {@code RGBA8} 通道、共 32 位。</p>
 *
 * <pre>
 *   [0..5]   折射      6 位，1px 步进 → 0..63 像素
 *   [6..11]  a 位置    6 位，1px 步进 → 0..63 像素
 *   [12..16] 反向倍率  5 位，/31×2 = 0..2
 *   [17..21] 中心缩小  5 位，/31×24 = 0..24 像素
 *   [22..26] 边缘色散  5 位，/31 = 0..1
 *   [27..31] 中心色散  5 位，1px 步进 → 0..31 像素
 * </pre>
 *
 * <p>刚好用满 32 位（全满 = {@code 0xFFFFFFFF}）。过去 {@code a 位置} 是 2px 步进、
 * 中心色散挤在 4 位里，导致 5px 只能编码成 6px、6.4px 只能编码成 6px；
 * 移除 b 位置后省出的位刚好把这两项提成 1px 步进。</p>
 *
 * <p>剩下的量化误差只来自位宽本身：{@code 反向倍率} 步进 0.0645、
 * {@code 中心缩小} 步进 0.774、{@code 边缘色散} 步进 0.0323。这是 32 位顶点色
 * 的硬上限——界面路径拿不到逐 draw uniform。</p>
 *
 * <p>边缘高光强度（{@code edgeLight}）<b>不在</b>这个位域里：它是材质级常量，
 * 走 shader define 注入，因此不受 32 位容量限制。</p>
 */
public final class GlassDebug {

    /** 折射强度的编码上限（GUI 像素）。 */
    public static final float MAX_REFRACTION = 63.0F;
    /** a 位置（反向段宽度）的编码上限，1px 步进。 */
    public static final float MAX_A_DISTANCE = 63.0F;
    /** 反向畸变倍率的编码上限。 */
    public static final float MAX_CONTRA = 2.0F;
    /**
     * 中心缩小强度的编码上限（GUI 像素）。
     *
     * <p>它是"短边半尺寸处采样点向外偏多少像素"，直接以像素计。24px 已经能看到明显的
     * 整体缩小，再大就会采样到面板外太远的地方。</p>
     */
    public static final float MAX_SHRINK = 24.0F;
    /** 中心色散的编码上限（GUI 像素），1px 步进。 */
    public static final float MAX_CENTER_DISPERSION = 31.0F;

    private GlassDebug() {
    }

    // ── 读取 ────────────────────────────────────────────────────────────

    public static float refraction(GlassStyle style) {
        return style == GlassStyle.WINDOW ? glass().refraction : style.refraction();
    }

    public static float dispersion(GlassStyle style) {
        return style == GlassStyle.WINDOW ? glass().dispersion : style.dispersion();
    }

    public static float centerDispersion(GlassStyle style) {
        return style == GlassStyle.WINDOW ? glass().centerDispersion : style.centerDispersion();
    }

    public static float aDistance(GlassStyle style) {
        return style == GlassStyle.WINDOW ? glass().aDistance : style.aDistance();
    }

    public static float contraRatio(GlassStyle style) {
        return style == GlassStyle.WINDOW ? glass().contraRatio : style.contraRatio();
    }

    public static float shrink(GlassStyle style) {
        return style == GlassStyle.WINDOW ? glass().shrink : style.shrink();
    }

    // ── 写入（自动标记待落盘）──────────────────────────────────────────

    public static void refraction(float value) {
        glass().refraction = clamp(value, 0.0F, MAX_REFRACTION);
        MusicPlayerConfigManager.markDirty();
    }

    public static void dispersion(float value) {
        glass().dispersion = clamp(value, 0.0F, 1.0F);
        MusicPlayerConfigManager.markDirty();
    }

    public static void centerDispersion(float value) {
        glass().centerDispersion = clamp(value, 0.0F, MAX_CENTER_DISPERSION);
        MusicPlayerConfigManager.markDirty();
    }

    public static void aDistance(float value) {
        glass().aDistance = clamp(value, 0.0F, MAX_A_DISTANCE);
        MusicPlayerConfigManager.markDirty();
    }

    public static void contraRatio(float value) {
        glass().contraRatio = clamp(value, 0.0F, MAX_CONTRA);
        MusicPlayerConfigManager.markDirty();
    }

    public static void shrink(float value) {
        glass().shrink = clamp(value, 0.0F, MAX_SHRINK);
        MusicPlayerConfigManager.markDirty();
    }

    /** 把玻璃外观一键恢复默认并立即落盘。 */
    public static void reset() {
        MusicPlayerConfigManager.resetGlass();
    }

    private static MusicPlayerConfig.GlassSettings glass() {
        return MusicPlayerConfigManager.get().glass;
    }

    // ── 顶点色编码 ──────────────────────────────────────────────────────

    /**
     * 把全部参数打包成顶点色，随 {@code BlitRenderState} 的 color 字段送进着色器。
     *
     * <p>顶点色是 {@code RGBA8_UNORM}：Java 侧按 ARGB 整数写入，着色器里按 vec4 读出，
     * 每个通道 0..1、精度 1/255。着色器把四个通道重新拼回一个 32 位整数再按位域拆解，
     * 对应实现在 {@code glass.fsh} 的 {@code unpackTuning()}。</p>
     */
    public static int encode(GlassStyle style) {
        return pack(refraction(style), dispersion(style), centerDispersion(style),
                aDistance(style), contraRatio(style), shrink(style));
    }

    /** 与 {@link #encode(GlassStyle)} 同一套编码，但参数由调用方给出。 */
    public static int pack(float refraction, float dispersion, float centerDispersion,
                           float aDistance, float contraRatio, float shrink) {
        // 必须用 long 打包：高位字段左移后最高位会成为 int 的符号位，
        // 一旦按 int 运算，符号位会被带进后面的字段（实测会让中间字段恒为 0）。
        long packed = quantize(refraction, 0.0F, MAX_REFRACTION, 63)
                | ((long) quantize(aDistance, 0.0F, MAX_A_DISTANCE, 63) << 6)
                | ((long) quantize(contraRatio, 0.0F, MAX_CONTRA, 31) << 12)
                | ((long) quantize(shrink, 0.0F, MAX_SHRINK, 31) << 17)
                | ((long) quantize(dispersion, 0.0F, 1.0F, 31) << 22)
                | ((long) quantize(centerDispersion, 0.0F, MAX_CENTER_DISPERSION, 31) << 27);
        int r = (int) (packed & 0xFFL);
        int g = (int) ((packed >>> 8) & 0xFFL);
        int b = (int) ((packed >>> 16) & 0xFFL);
        int a = (int) ((packed >>> 24) & 0xFFL);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 把 {@code value} 从 {@code [min, max]} 映射到 {@code [0, maxCode]} 的整数。 */
    private static int quantize(float value, float min, float max, int maxCode) {
        float normalized = (clamp(value, min, max) - min) / Math.max(max - min, 1.0E-6F);
        return Math.max(0, Math.min(maxCode, Math.round(normalized * maxCode)));
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
