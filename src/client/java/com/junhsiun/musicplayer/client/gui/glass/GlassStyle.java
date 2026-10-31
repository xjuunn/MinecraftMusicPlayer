package com.junhsiun.musicplayer.client.gui.glass;

/**
 * 液态玻璃的材质参数，字段与取值完全对应 DangoMusic 的 {@code GlassStyle} + {@code Theme}。
 *
 * <p>所有数值都会在编译期注入到 {@code glass.fsh} 的 shader define 里，一种参数组合对应一份
 * {@code RenderPipeline}（首次绘制时惰性编译，之后复用），因此参数应当是"少数几套固定风格"，
 * 而不是每帧变化的量。面板位置和尺寸不需要参数，着色器自己从顶点数据推导。</p>
 *
 * @param radius     圆角半径（GUI 像素）
 * @param tintR      染色 R
 * @param tintG      染色 G
 * @param tintB      染色 B
 * @param tintA      染色强度（0 = 不染色，1 = 完全变成染色）
 * @param maxLuma    亮度上限，超出后按比例压暗，保证白字在天空/雪地上可读
 * @param saturation 饱和度增益
 * @param frost      磨砂层混合比例（0 = 清透，1 = 全磨砂）
 * @param refraction 边缘折射强度（GUI 像素）
 * @param bevel      斜面宽度（GUI 像素）
 * @param dispersion 边缘色散强度（棱镜色边，随边缘折射位移等比缩放）
 * @param centerDispersion 面板内横向色差强度（GUI 像素）。从面板中心沿径向线性增长，
 *                         让面板中部也有稳定的颜色分离，而不只是边缘一圈
 * @param highlight  高光增益
 * @param shadow     投影
 * @param fallback   没有背景可采样时的退化填充色（ARGB）
 * @param opacity    整体不透明度
 *
 * <p>其中 {@code refraction}、{@code bevel}、{@code dispersion}、{@code centerDispersion}
 * 不烘焙进管线，而是逐 draw 经顶点色传入，可在运行时连续调节（见 {@link GlassDebug}）。
 * 这里的取值是它们的默认值。</p>
 */
public record GlassStyle(
        float radius,
        float tintR,
        float tintG,
        float tintB,
        float tintA,
        float maxLuma,
        float saturation,
        float frost,
        float refraction,
        float bevel,
        float dispersion,
        float centerDispersion,
        float highlight,
        GlassShadow shadow,
        int fallback,
        float opacity
) {
    /**
     * 主窗口 / 弹窗：iOS 清透液态玻璃，基于参考实现的 {@code Theme.GLASS_WINDOW}。
     *
     * <p>相对参考值有意调整了两项，其余（圆角、染色、饱和度、折射、斜面、色散、高光、投影）
     * 与参考完全一致：</p>
     * <ul>
     *   <li>{@code frost} 0.85 → 0.30：参考的磨砂层是 1/8 分辨率 + 约 51px 模糊，
     *       占 85% 会让背景糊成大色块。现在磨砂层本身已收敛到 1/4 分辨率（见
     *       {@link GlassBackdrop}），混合比再降低，让 1/2 分辨率的清透层主导，
     *       背景保持可辨——这是 iOS Liquid Glass 与"毛玻璃"的分界。</li>
     *   <li>{@code maxLuma} 0.30 → 0.55：参考的亮度上限把画面压得过暗，观感不通透。
     *       着色器里的封顶也改成只压亮度、保留彩度，否则室外画面会整体发灰。</li>
     *   <li>{@code dispersion} 0.30 → 0.85：加强边缘色散，让窗口边框出现明显的
     *       RGB 分离（棱镜色边），这是液态玻璃的关键特征。</li>
     *   <li>{@code refraction} 22 → 32：加强边缘透镜位移，让背景的形状畸变更明显。</li>
     *   <li>{@code centerDispersion} 新增（参考实现没有这一项）：4 GUI 像素的横向色差，
     *       从面板中心沿径向线性增长，使面板<b>中部</b>也有颜色分离，而不是只有边上一圈。
     *       边缘色散乘在折射位移上、超出斜面宽度就归零，所以它无法覆盖面板中部。</li>
     * </ul>
     */
    public static final GlassStyle WINDOW = new GlassStyle(
            30.0F,
            0.10F, 0.10F, 0.13F, 0.18F,
            0.55F, 1.45F, 0.30F,
            32.0F, 26.0F, 0.85F, 4.0F,
            1.0F,
            new GlassShadow(36.0F, 0.0F, -10.0F, 0.30F),
            0xE01C1C21,
            1.0F);

    /** 侧栏 / 次级面板，对应 {@code Theme.GLASS_SIDEBAR}。 */
    public static final GlassStyle SIDEBAR = new GlassStyle(
            22.0F,
            1.0F, 1.0F, 1.0F, 0.05F,
            0.36F, 1.40F, 0.55F,
            16.0F, 18.0F, 0.25F, 2.5F,
            0.90F,
            new GlassShadow(18.0F, 0.0F, -4.0F, 0.18F),
            0xE01C1C21,
            1.0F);

    /** 列表选中行 / 悬停，对应 {@code Theme.GLASS_SELECTION}（无投影）。 */
    public static final GlassStyle SELECTION = new GlassStyle(
            12.0F,
            1.0F, 1.0F, 1.0F, 0.13F,
            0.48F, 1.50F, 0.20F,
            10.0F, 12.0F, 0.35F, 2.0F,
            1.10F,
            GlassShadow.NONE,
            0xE01C1C21,
            1.0F);

    public GlassStyle {
        radius = Math.max(0.0F, radius);
        maxLuma = Math.max(0.0F, maxLuma);
        saturation = Math.max(0.0F, saturation);
        frost = clamp01(frost);
        refraction = Math.max(0.0F, refraction);
        bevel = Math.max(0.001F, bevel);
        dispersion = clamp01(dispersion);
        centerDispersion = Math.max(0.0F, centerDispersion);
        highlight = Math.max(0.0F, highlight);
        opacity = clamp01(opacity);
        if (shadow == null) {
            shadow = GlassShadow.NONE;
        }
    }

    /**
     * 管线缓存键：只包含<b>真正被烘焙进 shader define</b> 的参数。
     *
     * <p>折射、斜面、色散、中心色散这四项走顶点色逐 draw 传入（见 {@link GlassDebug}），
     * 不参与管线定义，因此<b>不能</b>出现在这个键里——否则拖动滑条会不停编译出新管线，
     * 既卡顿又会持续泄漏着色器程序。</p>
     */
    public String pipelineKey() {
        return radius + "/" + tintR + "/" + tintG + "/" + tintB + "/" + tintA + "/" + maxLuma + "/"
                + saturation + "/" + frost + "/" + highlight + "/" + shadow.size() + "/"
                + shadow.offsetX() + "/" + shadow.offsetY() + "/" + shadow.alpha() + "/" + opacity;
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, value);
    }
}
