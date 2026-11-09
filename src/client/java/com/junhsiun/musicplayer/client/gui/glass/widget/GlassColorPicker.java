package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * 主题色色板：一排预设色块，点一下立即生效。
 *
 * <p>不引入色轮：主题色只用于侧边栏选中、Tab 胶囊、滑块这些强调元素，
 * 预设的 8 档已经覆盖冷暖色系，选色也比拖三根 RGB 滑块快。</p>
 */
public final class GlassColorPicker extends AbstractWidget {

    /** 预设主题色（6 位十六进制，与配置里的格式一致）。 */
    public static final String[] PRESETS = {
            "0A84FF", // 系统蓝
            "64D2FF", // 天青
            "30D158", // 绿
            "FFD60A", // 黄
            "FF9F0A", // 橙
            "FF453A", // 红
            "FF375F", // 粉
            "BF5AF2"  // 紫
    };

    private static final int SWATCH = 18;
    private static final int GAP = 6;
    private static final int SELECTED_RING = 2;

    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private final Consumer<String> onChange;
    private String selected;
    private int hoveredIndex = -1;

    public GlassColorPicker(int x, int y, int width, int height, String selected,
                            Consumer<String> onChange) {
        super(x, y, width, height, Component.empty());
        this.selected = normalize(selected);
        this.onChange = onChange;
    }

    public void setSelected(String value) {
        this.selected = normalize(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        return text.toUpperCase(Locale.ROOT);
    }

    /** 所有色块整体占的宽度。 */
    public static int preferredWidth() {
        return PRESETS.length * SWATCH + (PRESETS.length - 1) * GAP;
    }

    private int swatchLeft(int index) {
        int total = preferredWidth();
        return getX() + width - total + index * (SWATCH + GAP);
    }

    private int swatchTop(int index) {
        return getY() + (height - SWATCH) / 2;
    }

    private int indexAt(double mouseX, double mouseY) {
        for (int index = 0; index < PRESETS.length; index++) {
            int left = swatchLeft(index);
            int top = swatchTop(index);
            if (mouseX >= left && mouseX <= left + SWATCH
                    && mouseY >= top && mouseY <= top + SWATCH) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        int index = indexAt(event.x(), event.y());
        if (index < 0 || onChange == null) {
            return;
        }
        String color = PRESETS[index];
        if (color.equals(selected)) {
            return;
        }
        selected = color;
        onChange.accept(color);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
        hoveredIndex = indexAt(graphics.mouseX(), graphics.mouseY());
        hover.tick(hoveredIndex >= 0, 0.09F);
        float lift = hover.eased();

        for (int index = 0; index < PRESETS.length; index++) {
            int rgb = Integer.parseInt(PRESETS[index], 16);
            boolean selectedSwatch = PRESETS[index].equals(selected);
            boolean hovered = index == hoveredIndex;
            int grow = selectedSwatch ? SELECTED_RING + 1 : (hovered ? 1 : 0);
            int left = swatchLeft(index) - grow;
            int top = swatchTop(index) - grow;
            int right = left + SWATCH + grow * 2;
            int bottom = top + SWATCH + grow * 2;
            float radius = (right - left) / 2.0F;

            if (selectedSwatch) {
                GlassWidgets.roundedFill(graphics, left, top, right, bottom,
                        0xFFFFFF, 0xFFFFFF, radius, 0.92F, 1.0F);
                left += SELECTED_RING;
                top += SELECTED_RING;
                right -= SELECTED_RING;
                bottom -= SELECTED_RING;
                radius = (right - left) / 2.0F;
            } else if (hovered) {
                GlassWidgets.roundedFill(graphics, left, top, right, bottom,
                        0xFFFFFF, 0xFFFFFF, radius, 0.10F + 0.08F * lift, 1.0F);
            }
            GlassWidgets.roundedFill(graphics, left, top, right, bottom,
                    rgb, rgb, radius, selectedSwatch || hovered ? 1.0F : 0.86F, 1.0F);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}