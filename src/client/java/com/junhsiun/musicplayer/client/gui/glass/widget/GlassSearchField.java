package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * macOS 搜索框：玻璃胶囊底 + 放大镜 + 原生 {@link EditBox}。
 *
 * <p><b>为什么不直接用 EditBox 的皮肤：</b>原版底图是一整块不透明的九宫格贴图，
 * 压在玻璃窗口上会把液态玻璃完全盖住。这里用 {@link #setBordered(boolean)} 关掉底图，
 * 自己画玻璃底，只借用原版的光标、选区、输入法组合（preedit）与文本横向滚动——
 * 这些逻辑重写没有意义。</p>
 *
 * <p><b>文字位置：</b>EditBox 的 {@code textX}/{@code textY} 是私有字段：
 * 关掉边框后文字画在 {@code getX()}/{@code getY()} 处。所以这里让控件本身占满整个胶囊
 * （命中区就是整块搜索框），再在绘制前把画笔整体下移 {@code (height-8)/2}，
 * 文字就垂直居中了，而命中区不受影响。</p>
 *
 * <p>左侧留出放大镜的位置，文字起点因此右移 {@link #TEXT_OFFSET} 像素。</p>
 */
public class GlassSearchField extends EditBox {

    /** 胶囊底与其内容的水平内边距。 */
    private static final int INSET = 8;
    /** 放大镜与文字之间的间距。 */
    private static final int GAP = 6;
    /** 文字起点相对胶囊左边缘的偏移量。 */
    private static final int TEXT_OFFSET = INSET + 8 + GAP - 4;

    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();

    public GlassSearchField(int x, int y, int width, int height, Component hint, Consumer<String> responder) {
        super(Minecraft.getInstance().font, x + TEXT_OFFSET, y, width - TEXT_OFFSET, height, Component.empty());
        setBordered(false);
        setMaxLength(64);
        setTextColor(GlassTheme.TEXT);
        setTextShadow(false);
        setHint(hint);
        setResponder(responder);
    }

    public static GlassSearchField create(int x, int y, int width, int height, Component hint,
                                          Consumer<String> responder) {
        return new GlassSearchField(x, y, width, height, hint, responder);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // 命中区取整个胶囊：EditBox 本体左侧让给了放大镜，只按控件矩形判定的话
        // 点在放大镜上不会聚焦，看起来就是"点了没反应"
        return active && visible
                && mouseX >= getX() - TEXT_OFFSET && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + height;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
        boolean usable = isActive();
        hover.tick(isHoveredOrFocused() && usable, GlassTheme.Motion.HOVER);
        float lift = hover.eased();

        int x0 = getX() - TEXT_OFFSET;
        int y0 = getY();
        int x1 = x0 + getWidth() + TEXT_OFFSET;
        int y1 = y0 + height;
        // 胶囊半径：着色器取 min(材质半径, 半宽, 半高)，叠加层与退化路径必须用同一套规则，
        // 否则胶囊外面会多出一层小圆角矩形
        float radius = height / 2.0F;

        if (!GlassWidgets.glassBackground(graphics, x0, y0, x1, y1, GlassTheme.FIELD, lift, 0.0F)) {
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, 0xFFFFFF, 0x000000, radius, 0.35F, 1.0F);
        }
        // 叠一层很淡的暗底：搜索框要在窗口表面上有明确边界，否则和卡片底色糊在一起
        GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, 0x000000, 0x000000, radius, 0.10F, 1.0F);

        drawMagnifier(graphics, x0 + INSET, (y0 + y1) / 2,
                getValue().isEmpty() && !isFocused() ? GlassTheme.TEXT_SECONDARY : GlassTheme.TEXT);

        // 文字垂直居中：EditBox 关掉边框后文字画在 getY()，把画笔整体下移即可
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, Math.round((height - 8) / 2.0F));
        super.extractWidgetRenderState(graphics);
        graphics.pose().popMatrix();
    }

    /** 放大镜：4 段粗线画圆环 + 一条斜柄，省掉一张贴图。 */
    private static void drawMagnifier(GuiGraphicsExtractor graphics, int centerX, int centerY, int color) {
        int radius = 3;
        int left = centerX - radius;
        int top = centerY - radius - 1;
        int right = left + radius * 2;
        int bottom = top + radius * 2;
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
        graphics.fill(right - 1, bottom - 1, right + 2, bottom, color);
        graphics.fill(right + 1, bottom, right + 3, bottom + 1, color);
    }
}
