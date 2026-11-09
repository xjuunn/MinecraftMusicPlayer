package com.junhsiun.musicplayer.client.gui.glass.theme;

import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 窗口内部的扁平视觉语言。
 *
 * <p>整个窗口只有最外层是液态玻璃，内部不再叠玻璃层：侧边栏、播放栏、列表行、表单控件
 * 全部用纯色半透明层次来区分。这样玻璃的折射只作用在「世界 → 窗口」这一层，内容始终清晰，
 * 也不会出现玻璃叠玻璃导致的糊成一团。</p>
 *
 * <p>配色刻意压得很低：面板 7%、悬停 12%、按下 17%，分隔线 12%。层次靠亮度、留白和
 * 一根强调色细线建立，而不是靠更多颜色。</p>
 */
public final class FlatTheme {

    /** 强调色：默认比系统蓝稍亮一档，可在外观设置里改成任意主题色。 */
    public static int accent() {
        return 0xFF000000 | MusicPlayerConfigManager.get().glass.themeColorRgb();
    }

    /** 强调色的 RGB 部分（不含 alpha）。 */
    public static int accentRgb() {
        return MusicPlayerConfigManager.get().glass.themeColorRgb();
    }

    /** 主文字。 */
    public static final int TEXT = 0xFFFFFFFF;
    /** 次级文字（78% 白）。 */
    public static final int TEXT_SECONDARY = 0xC7FFFFFF;
    /** 三级文字 / 说明（54% 白）。 */
    public static final int TEXT_TERTIARY = 0x8AFFFFFF;
    /** 最弱文字 / 占位（36% 白）。 */
    public static final int TEXT_FAINT = 0x5CFFFFFF;

    /** 面板底色（7% 白）。 */
    public static final int SURFACE = 0x12FFFFFF;
    /** 悬停底色（12% 白）。 */
    public static final int SURFACE_HOVER = 0x1FFFFFFF;
    /** 按下底色（17% 白）。 */
    public static final int SURFACE_PRESS = 0x2BFFFFFF;
    /** 比普通面板更实一档的选中底（14% 白）。 */
    public static final int SURFACE_STRONG = 0x24FFFFFF;
    /** 输入框内部：轻微压暗，让可输入区域有明确的边界。 */
    public static final int FIELD = 0x33000000;
    /** 分隔线（12% 白）。 */
    public static final int DIVIDER = 0x1FFFFFFF;

    /** 行 / 导航项圆角。 */
    public static final float RADIUS_ROW = 7.0F;
    /** 输入框圆角。 */
    public static final float RADIUS_FIELD = 8.0F;
    /** 胶囊：着色器会夹到半高。 */
    public static final float RADIUS_PILL = 999.0F;

    private FlatTheme() {
    }

    /** 把 RGB 与 0..1 的透明度合成 ARGB。 */
    public static int alpha(int rgb, float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return (Math.round(clamped * 255.0F) << 24) | (rgb & 0xFFFFFF);
    }

    /** 纯色圆角填充；透明度取自 {@code argb} 的高 8 位。 */
    public static void fill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                            float radius, int argb) {
        float alpha = ((argb >>> 24) & 0xFF) / 255.0F;
        GlassWidgets.roundedFill(graphics, x0, y0, x1, y1,
                argb & 0xFFFFFF, argb & 0xFFFFFF, radius, alpha, 1.0F);
    }

    /** 带独立描边的圆角填充。 */
    public static void fillBorder(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                  float radius, int fillRgb, int borderRgb, float alpha) {
        GlassWidgets.roundedFill(graphics, x0, y0, x1, y1,
                fillRgb & 0xFFFFFF, borderRgb & 0xFFFFFF, radius, alpha, 1.0F);
    }

    /** 输入框：1 像素描边 + 轻微压暗的内部；聚焦时底部浮出一条强调色细线。 */
    public static void field(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                             float radius, float lift, boolean focused) {
        int borderAlpha = alpha(0xFFFFFF, 0.16F + 0.10F * lift);
        fill(graphics, x0, y0, x1, y1, radius, borderAlpha);
        if (x1 - x0 > 2 && y1 - y0 > 2) {
            fill(graphics, x0 + 1, y0 + 1, x1 - 1, y1 - 1,
                    Math.max(1.0F, radius - 1.0F), FIELD);
            if (focused) {
                // 两端内收，避开圆角，读起来是一条下划线而不是一圈蓝框
                fill(graphics, x0 + 4, y1 - 1, x1 - 4, y1, 0.5F, accent());
            }
        }
    }

    /** 1 像素分隔线。 */
    public static void divider(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0, y0, x1, y1, DIVIDER);
    }
}
