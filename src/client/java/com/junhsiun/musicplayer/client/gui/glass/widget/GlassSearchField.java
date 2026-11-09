package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.icon.SvgIcons;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
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
    /**
     * 放大镜图标的边长（正方形，对应 SVG 的 1em 视口）。
     *
     * <p>取 12 是为了让像素画的方块落在整像素上：图标的每个方块是 2 个 viewBox 单位，
     * {@code 2 / 24 × 12 = 1} 个 GUI 像素，放大到设备分辨率后是干净的 2×2 / 4×4 方块；
     * 取 10 的话方块是 0.83 像素，会出现有的方块 1 像素、有的 2 像素的参差。</p>
     */
    private static final int ICON_SIZE = 12;
    /** 放大镜与文字之间的间距。 */
    private static final int GAP = 6;
    /** 文字起点相对胶囊左边缘的偏移量。 */
    private static final int TEXT_OFFSET = INSET + ICON_SIZE + GAP;

    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private boolean flat;

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

    /** 改成扁平样式：不再叠玻璃层。 */
    public GlassSearchField flat() {
        this.flat = true;
        return this;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // 命中区取整个胶囊：EditBox 本体左侧让给了放大镜，只按控件矩形判定的话
        // 点在放大镜上不会聚焦，看起来就是"点了没反应"
        return active && visible
                && mouseX >= getX() - TEXT_OFFSET && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + height;
    }

    /**
     * 有焦点时把按键拦下来，不让它变成界面快捷键。
     *
     * <p>输入框有焦点时玩家是在<b>打字</b>：字母 E 必须当成一个字符，而不是"背包键"。
     * 之前 {@code GlassWindowScreen} 把背包键接成了关闭窗口，于是输入框里敲个 e
     * 整个界面就没了。这里把 {@link EditBox} 没消费的按键一律吃掉。</p>
     *
     * <p>放行两类：Esc 根本到不了这里——{@code Screen.keyPressed} 在委托给控件之前
     * 就处理了关闭；Tab 是焦点导航（切到「搜索」按钮），需要留给基类。</p>
     *
     * <p>F1/F3/F11 这类全局键由 {@code KeyboardHandler} 处理，不经过界面，不受影响。</p>
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        if (!isFocused()) {
            return false;
        }
        if (event.key() == InputConstants.KEY_TAB || event.keycode() == InputConstants.KEYCODE_TAB) {
            return false;
        }
        return true;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
        boolean usable = isActive();
        hover.tick((isHovered() || isFocused()) && usable, GlassTheme.Motion.HOVER);
        float lift = hover.eased();

        int x0 = getX() - TEXT_OFFSET;
        int y0 = getY();
        int x1 = x0 + getWidth() + TEXT_OFFSET;
        int y1 = y0 + height;
        // 胶囊半径：着色器取 min(材质半径, 半宽, 半高)，叠加层与退化路径必须用同一套规则，
        // 否则胶囊外面会多出一层小圆角矩形
        float radius = height / 2.0F;

        if (flat) {
            FlatTheme.field(graphics, x0, y0, x1, y1, radius, lift, isFocused());
        } else {
            if (!GlassWidgets.glassBackground(graphics, x0, y0, x1, y1, GlassTheme.FIELD, lift, 0.0F)) {
                GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, 0xFFFFFF, 0x000000, radius, 0.35F, 1.0F);
            }
            // 叠一层很淡的暗底：搜索框要在窗口表面上有明确边界，否则和卡片底色糊在一起
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, 0x000000, 0x000000, radius, 0.10F, 1.0F);
        }

        SvgIcons.SEARCH.draw(graphics, x0 + INSET, (y0 + y1) / 2 - ICON_SIZE / 2, ICON_SIZE,
                getValue().isEmpty() && !isFocused()
                        ? (flat ? FlatTheme.TEXT_TERTIARY : GlassTheme.TEXT_SECONDARY)
                        : (flat ? FlatTheme.TEXT : GlassTheme.TEXT));

        // 文字垂直居中：EditBox 关掉边框后文字画在 getY()，把画笔整体下移即可
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, Math.round((height - 8) / 2.0F));
        super.extractWidgetRenderState(graphics);
        graphics.pose().popMatrix();
    }
}
