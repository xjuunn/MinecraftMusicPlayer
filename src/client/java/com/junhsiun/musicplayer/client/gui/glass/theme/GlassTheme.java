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
