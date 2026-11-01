package com.junhsiun.musicplayer.client.gui.glass;

/**
 * 液态玻璃的材质参数。
 *
 * <p>数值按 Apple 官方文档的四条核心原理组织：</p>
 * <ol>
 *   <li><b>背景透射与模糊</b>：{@code tintR/G/B/A}、{@code saturation}、{@code maxLuma}、{@code opacity}
 *       ——对应 {@code C_out = α·C_glass + (1−α)·C_blur}；
 *       {@code saturation} 与 {@code maxLuma} 就是 HIG 所说的 regular 变体对背景做的
 *       "亮度调整"，保证前景文字可读。</li>
 *   <li><b>边缘高光与轮廓</b>：{@code edgeLight}、{@code highlight}、{@code radius}。</li>
 *   <li><b>光学响应</b>：{@code refraction}、{@code dispersion}、{@code centerDispersion}
 *       ——菲涅耳反射与屏幕空间折射都由表面法线场驱动。</li>
 *   <li><b>动态形变</b>：由 Java 侧的弹簧与形状插值实现，不进入着色器。</li>
 * </ol>
 *
 * <p>所有数值都会在编译期注入 {@code glass.fsh} 的 shader define，一种参数组合对应一份
 * {@code RenderPipeline}（首次绘制时惰性编译，之后复用），因此参数应当是"少数几套固定风格"，
 * 而不是每帧变化的量。面板位置和尺寸不需要参数，着色器自己从顶点数据推导。</p>
 *
 * @param radius           圆角半径（GUI 像素）
 * @param tintR            玻璃自身颜色 R
 * @param tintG            玻璃自身颜色 G
 * @param tintB            玻璃自身颜色 B
 * @param tintA            玻璃自身颜色的混合权重 α（0 = 纯背景透射，1 = 完全实色）
 * @param maxLuma          亮度上限，超出后按比例压暗，保证白字在天空/雪地上可读
 * @param saturation       饱和度增益（vibrancy）
 * @param refraction       a→b 段正向畸变的峰值幅度（GUI 像素）
 * @param dispersion       边缘色散强度（棱镜色边，随位移等比缩放）
 * @param centerDispersion 面板内横向色差强度（GUI 像素），从中心沿径向增长
 * @param highlight        整体高光增益
 * @param shadow           投影
 * @param fallback         没有背景可采样时的退化填充色（ARGB）
 * @param opacity          整体不透明度
 * @param aDistance        a 位置（GUI 像素）：边框 → a 是反向段，a → 中心是一条单调过渡
 * @param contraRatio      反向畸变相对正向的倍率，0 = 不反向，1 = 等幅反号，&gt;1 反向更强
 * @param shrink           a → 中心逐渐增强的缩小强度，单位是 GUI 像素：
 *                         面板短边半尺寸处的采样点向外偏移这么多。0 = 不缩小
 * @param edgeLight        边缘高光强度 0..1，按 {@code H(p) = e^(−d²/2σ²)}
 *                         贴着轮廓发光，并由表面法线做方向性调制（上缘更亮）
 */
public record GlassStyle(
        float radius,
        float tintR,
        float tintG,
        float tintB,
        float tintA,
        float maxLuma,
        float saturation,
        float refraction,
        float dispersion,
        float centerDispersion,
        float highlight,
        GlassShadow shadow,
        int fallback,
        float opacity,
        float aDistance,
        float contraRatio,
        float shrink,
        float edgeLight
) {
    /**
     * 主窗口 / 弹窗：iOS regular 变体液态玻璃。
     *
     * <p>相对早期实现有意调整的几项：</p>
     * <ul>
     *   <li>{@code tintA} 很低——regular 变体以背景透射为主，玻璃自身只带一点冷灰。</li>
     *   <li>{@code maxLuma} 0.55：HIG 要求 regular 变体"模糊<b>并调整</b>背景亮度"以保证
     *       前景可读，封顶只压亮度、保留彩度。</li>
     *   <li>{@code edgeLight} 0.42：边缘高光是液态玻璃最标志性的特征，
     *       但用高斯衰减 + 法线方向性调制，不会成为一圈均匀白框。</li>
     *   <li>{@code aDistance} / {@code contraRatio} / {@code shrink}
     *       把边缘畸变拆成反向段 + 一条单调过渡。</li>
     * </ul>
     *
     * <p>这 6 项光学参数的运行期真值来自配置文件（{@code glass} 分节），见
     * {@code GlassDebug}；这里的数值是材质兜底，必须与
     * {@code MusicPlayerConfig.GlassSettings} 的默认值保持一致，否则
     * "恢复默认"会让外观突变。</p>
     */
    public static final GlassStyle WINDOW = new GlassStyle(
            30.0F,
            0.10F, 0.10F, 0.13F, 0.18F,
            0.55F, 1.45F,
            30.0F, 0.65F, 6.40F,
            1.0F,
            new GlassShadow(36.0F, 0.0F, -10.0F, 0.30F),
            0xE01C1C21,
            1.0F,
            5.0F, 0.62F, 7.34F,
            0.42F);

    /** 侧栏 / 次级面板。 */
    public static final GlassStyle SIDEBAR = new GlassStyle(
            22.0F,
            1.0F, 1.0F, 1.0F, 0.05F,
            0.36F, 1.40F,
            16.0F, 0.25F, 2.5F,
            0.90F,
            new GlassShadow(18.0F, 0.0F, -4.0F, 0.18F),
            0xE01C1C21,
            1.0F,
            6.0F, 1.10F, 7.0F,
            0.34F);

    /** 列表选中行 / 悬停（无投影）。 */
    public static final GlassStyle SELECTION = new GlassStyle(
            12.0F,
            1.0F, 1.0F, 1.0F, 0.13F,
            0.48F, 1.50F,
            10.0F, 0.35F, 2.0F,
            1.10F,
            GlassShadow.NONE,
            0xE01C1C21,
            1.0F,
            4.0F, 1.00F, 5.0F,
            0.38F);

    /**
     * 复制本材质、只替换圆角半径。
     *
     * <p>用于让内嵌面板的圆角与容器圆角<b>同心</b>。两层圆角要重合，必须满足
     * {@code 内层半径 = 外层半径 − 内层到外层的边距}：两者的圆心才会落在同一点。
     * 内层半径若小于该值，两条弧线会在角落错开，看起来就是"圆角不一致"。</p>
     *
     * <p>这里只改 {@code radius}，其余参数（染色、光学、高光）保持不变，
     * 因此拿来画"窗口里的一层"时视觉上仍是同一种玻璃。</p>
     */
    public GlassStyle withRadius(float value) {
        return new GlassStyle(value, tintR, tintG, tintB, tintA, maxLuma, saturation,
                refraction, dispersion, centerDispersion, highlight, shadow, fallback,
                opacity, aDistance, contraRatio, shrink, edgeLight);
    }

    public GlassStyle {
        radius = Math.max(0.0F, radius);
        maxLuma = Math.max(0.0F, maxLuma);
        saturation = Math.max(0.0F, saturation);
        refraction = Math.max(0.0F, refraction);
        dispersion = clamp01(dispersion);
        centerDispersion = Math.max(0.0F, centerDispersion);
        highlight = Math.max(0.0F, highlight);
        opacity = clamp01(opacity);
        aDistance = Math.max(0.0F, aDistance);
        contraRatio = Math.max(0.0F, contraRatio);
        shrink = Math.max(0.0F, shrink);
        edgeLight = clamp01(edgeLight);
        if (shadow == null) {
            shadow = GlassShadow.NONE;
        }
    }

    /**
     * 管线缓存键：只包含<b>真正被烘焙进 shader define</b> 的参数。
     *
     * <p>八项光学参数走顶点色逐 draw 传入（见 {@link GlassDebug}），不参与管线定义，
     * 因此<b>不能</b>出现在这个键里——否则拖动滑条会不停编译出新管线，
     * 既卡顿又会持续泄漏着色器程序。</p>
     */
    public String pipelineKey() {
        return radius + "/" + tintR + "/" + tintG + "/" + tintB + "/" + tintA + "/" + maxLuma + "/"
                + saturation + "/" + highlight + "/" + edgeLight + "/" + shadow.size() + "/"
                + shadow.offsetX() + "/" + shadow.offsetY() + "/" + shadow.alpha() + "/" + opacity;
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, value);
    }
}
