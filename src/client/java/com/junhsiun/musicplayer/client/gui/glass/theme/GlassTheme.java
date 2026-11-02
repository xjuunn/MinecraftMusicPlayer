package com.junhsiun.musicplayer.client.gui.glass.theme;

import com.junhsiun.musicplayer.client.gui.glass.GlassLook;
import com.junhsiun.musicplayer.client.gui.glass.GlassShadow;
import com.junhsiun.musicplayer.client.gui.glass.GlassStyle;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;

/** 控件配色、尺寸、材质与动效参数。 */
public final class GlassTheme {

    /** 强调蓝。 */
    public static final int ACCENT = 0xFF007AFF;
    /** 主文字：永远是白色，不随背景变。 */
    public static final int TEXT = 0xFFFFFFFF;
    /** 次级文字：略带透明度的白，保证层次又不失对比。 */
    public static final int TEXT_SECONDARY = 0xE6FFFFFF;

    /** macOS 控件尺寸（GUI 像素）。 */
    public static final class Sizes {
        public static final int CONTROL = 16;
        public static final int CONTROL_SMALL = 13;
        public static final int CONTROL_LARGE = 22;
        public static final int RADIUS = 5;
        public static final int SLIDER_TRACK = 3;
        public static final int SLIDER_KNOB = 12;
        public static final int TOGGLE_WIDTH = 32;
        public static final int TOGGLE_HEIGHT = 18;
        public static final int TOGGLE_KNOB = 14;
        public static final int TOGGLE_INSET = 2;
        /** 窗口圆角，窗口叠加层要用同一个半径。 */
        public static final int WINDOW_RADIUS = 30;

        private Sizes() {
        }
    }

    /** 文字缩放，把 8px 字体压到 macOS 观感。 */
    public static final class Text {
        public static final float CONTROL = 0.75F;
        public static final float LABEL = 0.8F;
        public static final float VALUE = 0.7F;

        private Text() {
        }
    }

    /** 动效时间常数（秒）。 */
    public static final class Motion {
        public static final float HOVER = 0.055F;
        public static final float PRESS = 0.035F;
        public static final float RELEASE = 0.075F;
        public static final float TOGGLE = 0.075F;

        private Motion() {
        }
    }

    /**
     * 控件玻璃：只做纯折射，不带染色。
     *
     * <p>{@code tintA} 必须为 0。染色由叠加层用顶点色实现，这样拖动透明度滑块
     * 不会触发管线重编译。{@code radius} 给得比控件大，着色器取
     * {@code min(RADIUS, 半宽, 半高)}，同一份材质在小控件上是圆角矩形、圆珠上自动变正圆。</p>
     */
    public static final GlassStyle BUTTON = new GlassStyle(
            32.0F,
            1.0F, 1.0F, 1.0F, 0.0F,
            0.90F, 1.10F,
            3.0F, 0.28F, 1.2F,
            1.35F,
            GlassShadow.NONE,
            0xF2FFFFFF,
            1.0F,
            2.0F, 1.10F, 3.5F,
            0.50F);

    /**
     * 输入框玻璃：搜索框这类"要长时间停留、里面还要写字"的控件。
     *
     * <p>比 {@link #BUTTON} 再收敛一档折射与色散。按钮上强折射会让它显得"活起来"，
     * 但文字压在强折射背景上会发飘，读起来累。圆角同样给 32，由着色器夹到半高，
     * 22 像素高的搜索框因此自动变成胶囊。</p>
     */
    public static final GlassStyle FIELD = new GlassStyle(
            32.0F,
            1.0F, 1.0F, 1.0F, 0.0F,
            0.86F, 1.06F,
            2.0F, 0.16F, 0.8F,
            1.20F,
            GlassShadow.NONE,
            0xF2FFFFFF,
            1.0F,
            2.0F, 1.00F, 3.0F,
            0.46F);

    public static final GlassStyle TOGGLE = new GlassStyle(
            32.0F,
            1.0F, 1.0F, 1.0F, 0.0F,
            0.90F, 1.08F,
            2.5F, 0.24F, 1.0F,
            1.30F,
            GlassShadow.NONE,
            0xF2FFFFFF,
            1.0F,
            2.0F, 1.10F, 3.0F,
            0.48F);

    public static final GlassStyle KNOB = new GlassStyle(
            32.0F,
            1.0F, 1.0F, 1.0F, 0.0F,
            0.92F, 1.04F,
            2.0F, 0.14F, 0.7F,
            1.55F,
            GlassShadow.NONE,
            0xF2FFFFFF,
            1.0F,
            1.5F, 1.05F, 2.5F,
            0.58F);

    /**
     * 卡片材质：窗口内的<b>第二层玻璃</b>容器（见 {@code GlassCard}）。
     *
     * <p>不能直接复用窗口材质——玻璃叠玻璃会让折射重复施加两次，中心区域糊成一团。
     * 这里把折射与中心收缩都收到窗口的四成左右，色散减半，只留一点边缘存在感。</p>
     *
     * <p>圆角给 14：比控件（接近胶囊）方正，比窗口（30）收敛，
     * 层次上一眼能看出「这是窗口里的一块面板」而不是另一个窗口。</p>
     *
     * <p>投影仍然是 {@code NONE}。窗口投影由配置决定，卡片再叠一层会显脏；
     * 卡片靠边缘高光和折射与窗口表面区分开。</p>
     */
    public static final GlassStyle CARD = new GlassStyle(
            14.0F,
            1.0F, 1.0F, 1.0F, 0.0F,
            0.72F, 1.20F,
            12.0F, 0.35F, 2.4F,
            0.95F,
            GlassShadow.NONE,
            0xE6F2F3F7,
            1.0F,
            3.0F, 0.75F, 4.0F,
            0.30F);

    /**
     * 分段控件（tabs）的<b>底槽</b>：一整条浅玻璃。
     *
     * <p>它和 {@link #CARD} 一样是"窗口里的第二层玻璃"，所以折射、色散、中心收缩都要比
     * 窗口收一档，否则两层折射叠在一起，中间会糊成一块。圆角给 11 是因为分段控件高 20~22，
     * 着色器会夹到半高，正好成为一条胶囊。</p>
     */
    public static final GlassStyle TRACK = new GlassStyle(
            11.0F,
            1.0F, 1.0F, 1.0F, 0.0F,
            0.70F, 1.24F,
            8.0F, 0.22F, 1.6F,
            0.72F,
            GlassShadow.NONE,
            0xE6F2F3F7,
            1.0F,
            2.5F, 0.70F, 2.6F,
            0.26F);

    /**
     * 分段控件的<b>选中胶囊</b>：比底槽亮一档，用来"顶起"当前标签。
     *
     * <p>它是叠在底槽上的第三层，折射再收一档，只保留边缘高光与轻微折射——观感上是一块
     * 被抬起来的玻璃，而不是又一块会晃动的放大镜。</p>
     */
    public static final GlassStyle PILL = new GlassStyle(
            10.0F,
            1.0F, 1.0F, 1.0F, 0.0F,
            0.80F, 1.18F,
            5.0F, 0.16F, 1.1F,
            1.00F,
            GlassShadow.NONE,
            0xF2F3F7,
            1.0F,
            2.0F, 0.65F, 1.6F,
            0.34F);

    /**
     * 列表行：每条结果自己的一块薄玻璃。
     *
     * <p>行高只有二十几像素、又密密麻麻排一列，所以折射与色散都压到最低，只留圆角、边缘
     * 高光和一丁点折射——远看是一排玻璃片，近看不会互相打架。悬停/按下由
     * {@code GlassWidgets.glassBackground} 的 lift / press 参数继续提亮。</p>
     */
    public static final GlassStyle ROW = new GlassStyle(
            7.0F,
            1.0F, 1.0F, 1.0F, 0.0F,
            0.74F, 1.16F,
            5.0F, 0.12F, 1.0F,
            0.62F,
            GlassShadow.NONE,
            0xE6F2F3F7,
            1.0F,
            2.0F, 0.70F, 1.4F,
            0.22F);

    /** 详情页上的数据小标签（badge），比列表行再薄一档。 */
    public static final GlassStyle BADGE = new GlassStyle(
            5.0F,
            1.0F, 1.0F, 1.0F, 0.0F,
            0.76F, 1.14F,
            3.5F, 0.08F, 0.7F,
            0.56F,
            GlassShadow.NONE,
            0xE6F2F3F7,
            1.0F,
            1.5F, 0.70F, 0.9F,
            0.18F);

    private GlassTheme() {
    }

    /** 窗口材质：统一清透玻璃，染色同样走叠加层。 */
    public static GlassStyle windowStyle() {
        return GlassStyle.WINDOW;
    }

    /** 背景不透明度配置值，0 = 完全透明，1 = 最厚。 */
    public static float opacity() {
        return (float) clamp01(glassSettings().opacity);
    }

    public static void setOpacity(float value) {
        glassSettings().opacity = clamp01(value);
        MusicPlayerConfigManager.markDirty();
    }

    private static MusicPlayerConfig.GlassSettings glassSettings() {
        return MusicPlayerConfigManager.get().glass;
    }

    private static double clamp01(double value) {
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }

    /** 背景模糊度 0..1。 */
    public static float blur() {
        return (float) clamp01(glassSettings().blur);
    }

    public static void setBlur(float value) {
        glassSettings().blur = clamp01(value);
        MusicPlayerConfigManager.markDirty();
    }

    /** 是否绘制窗口底部投影。 */
    public static boolean shadowEnabled() {
        return glassSettings().shadow;
    }

    public static void setShadowEnabled(boolean value) {
        glassSettings().shadow = value;
        MusicPlayerConfigManager.markDirty();
    }

    /** 本帧要用的玻璃外观。 */
    public static GlassLook look() {
        return new GlassLook(blur());
    }

    /**
     * 叠加层不透明度：窗口与所有控件共用这一个值，拖动滑块时全部一起变。
     *
     * <p>曲线是 {@code 0.92 * opacity^1.6}：低端几乎不变，玻璃的通透与折射都还在；
     * 拉到 100% 时接近实色的浅色面板。</p>
     */
    public static float overlayAlpha() {
        float t = opacity();
        return 0.92F * (float) Math.pow(t, 1.6D);
    }

    /** 叠加层颜色：固定的 macOS 浅色材质色，不随背景切换。 */
    public static int overlayColor() {
        return 0xF2F3F7;
    }

    /**
     * 叠加层颜色的归一化 RGB，供着色器定义使用。
     *
     * <p>着色器要用同一个颜色做「窗口当背景」的合成，从 {@link #overlayColor()}
     * 派生出来可以保证两边永远一致，不会因为改了颜色而漏掉另一处。</p>
     */
    public static float[] overlayColorRgb() {
        int rgb = overlayColor() & 0xFFFFFF;
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F
        };
    }
}
