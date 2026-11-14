package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 液态玻璃文本输入框。
 *
 * <p>关掉原版底图，自己画一块折射玻璃底（和搜索框同一套 {@link GlassTheme#FIELD} 材质），
 * 再叠一层很淡的暗底让它在窗口表面上有明确边界；聚焦时同样保持玻璃观感。
 * 用于设置页里的服务地址、代理、歌单 ID 这类单行文本。</p>
 */
public class GlassTextField extends EditBox {

    /** 底与内容的水平内边距。 */
    private static final int INSET = 8;

    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private Runnable onSubmit;

    public GlassTextField(int x, int y, int width, int height, Component hint,
                          Consumer<String> responder) {
        super(Minecraft.getInstance().font, x + INSET, y, Math.max(1, width - INSET * 2), height,
                Component.empty());
        setBordered(false);
        setMaxLength(512);
        setTextColor(FlatTheme.TEXT);
        setTextShadow(false);
        setHint(hint);
        setResponder(responder);
    }

    public static GlassTextField create(int x, int y, int width, int height, Component hint,
                                        Consumer<String> responder) {
        return new GlassTextField(x, y, width, height, hint, responder);
    }

    /** 回车提交回调。设置页里用来把一次编辑合并成一次网络请求 / 落盘。 */
    public GlassTextField onSubmit(Runnable action) {
        this.onSubmit = action;
        return this;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && visible
                && mouseX >= getX() - INSET && mouseX < getX() + getWidth() + INSET
                && mouseY >= getY() && mouseY < getY() + height;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isFocused() && onSubmit != null && isEnter(event)) {
            onSubmit.run();
            return true;
        }
        if (super.keyPressed(event)) {
            return true;
        }
        if (!isFocused()) {
            return false;
        }
        if (event.key() == InputConstants.KEY_TAB || event.key() == InputConstants.KEY_RETURN) {
            return false;
        }
        return true;
    }

    private static boolean isEnter(KeyEvent event) {
        return event.key() == InputConstants.KEY_RETURN
                || event.key() == InputConstants.KEY_NUMPADENTER;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean usable = isActive();
        hover.tick((isHovered() || isFocused()) && usable, GlassTheme.Motion.HOVER);
        float lift = hover.eased();

        int x0 = getX() - INSET;
        int y0 = getY();
        int x1 = x0 + getWidth() + INSET * 2;
        int y1 = y0 + height;
        float radius = Math.min(GlassTheme.FIELD.radius(), height / 2.0F);

        // 液态玻璃底：和搜索框同一套 FIELD 材质（圆角由着色器夹到半径/半高）。
        if (!GlassWidgets.glassBackground(graphics, x0, y0, x1, y1, GlassTheme.FIELD, lift, 0.0F)) {
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, 0xFFFFFF, 0x000000, radius, 0.35F, 1.0F);
        }
        // 极淡的暗底：输入框要在窗口表面上有明确边界，否则和卡片底色糊在一起（不透明度压得很低，保持清澈）。
        GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, 0x000000, 0x000000, radius, 0.06F, 1.0F);

        graphics.pose().pushMatrix();
        graphics.pose().translate(0, Math.round((height - 8) / 2.0F));
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popMatrix();
    }
}
