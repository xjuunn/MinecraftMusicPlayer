package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 分段控件。
 *
 * <p>一条 6% 白的浅色底槽 + 一枚液态玻璃选中胶囊，切换时胶囊在段与段之间滑过去。
 * 选中胶囊叠一层主题色，和侧边栏选中项的语言一致；未选中段只做亮度层次。</p>
 */
public class GlassTabs extends AbstractWidget {

    /** 胶囊滑动的时间常数（秒）：越小越快。 */
    private static final float SLIDE_TAU = 0.055F;
    /** 按压收缩比例。 */
    private static final float PRESS_SHRINK = 0.04F;
    /** 未选中标签的亮度。 */
    private static final float IDLE_TEXT_KEEP = 0.22F;
    private static final float LABEL_SCALE = GlassTheme.Text.CONTROL;

    private final List<Component> labels = new ArrayList<>();
    private final GlassWidgets.Spring press = new GlassWidgets.Spring();
    private final List<GlassWidgets.Spring> hoverSprings = new ArrayList<>();

    private IntConsumer onSelect;
    private int selected;
    private int hovered = -1;
    private int pressed = -1;

    /** 胶囊左边缘（GUI 像素），按真实时间逼近目标位置。 */
    private float pillX;
    private long lastFrameMillis = -1L;
    private boolean pillInitialized;
    private float lastSegmentWidth = -1.0F;

    public GlassTabs(int x, int y, int width, int height, List<Component> labels, int selected) {
        super(x, y, width, height, Component.empty());
        if (labels != null) {
            this.labels.addAll(labels);
        }
        this.selected = clampIndex(selected);
    }

    public GlassTabs onSelect(IntConsumer listener) {
        this.onSelect = listener;
        return this;
    }

    public int selected() {
        return selected;
    }

    public void setLabels(List<Component> labels) {
        this.labels.clear();
        if (labels != null) {
            this.labels.addAll(labels);
        }
        this.selected = clampIndex(selected);
        this.pillInitialized = false;
    }

    /** 外部改变选中项（不会触发回调）。 */
    public void setSelected(int index) {
        this.selected = clampIndex(index);
    }

    // ── 几何 ────────────────────────────────────────────────────────────

    private int count() {
        return labels.size();
    }

    private float segmentWidth() {
        int n = count();
        return n == 0 ? width : (float) width / n;
    }

    private float segmentLeft(int index) {
        return getX() + index * segmentWidth();
    }

    private int indexAt(double mouseX) {
        if (count() == 0) {
            return -1;
        }
        int index = (int) ((mouseX - getX()) / segmentWidth());
        return Math.max(0, Math.min(count() - 1, index));
    }

    private int clampIndex(int index) {
        if (labels.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(labels.size() - 1, index));
    }

    // ── 交互 ────────────────────────────────────────────────────────────

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (count() == 0) {
            return;
        }
        pressed = indexAt(event.x());
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        boolean armed = pressed >= 0;
        pressed = -1;
        if (!armed || count() == 0 || !isMouseOver(event.x(), event.y())) {
            return;
        }
        int index = indexAt(event.x());
        if (index != selected) {
            selected = index;
            if (onSelect != null) {
                onSelect.accept(index);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    // ── 绘制 ────────────────────────────────────────────────────────────

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (count() == 0 || width <= 0 || height <= 0) {
            return;
        }
        boolean usable = isActive();
        Font font = Minecraft.getInstance().font;
        press.tickSpring(pressed >= 0 && usable,
                pressed >= 0 ? GlassTheme.Motion.PRESS : GlassTheme.Motion.RELEASE);
        float segWidth = segmentWidth();
        float radius = Math.min(height * 0.5F, segWidth * 0.5F);

        int left = getX();
        int top = getY();
        int right = left + width;
        int bottom = top + height;

        // 底槽
        FlatTheme.fill(graphics, left, top, right, bottom, radius,
                FlatTheme.alpha(0xFFFFFF, usable ? 0.06F : 0.035F));

        // 悬停段
        int hoveredIndex = -1;
        if (usable && pressed < 0 && isMouseOver(mouseX, mouseY)) {
            hoveredIndex = indexAt(mouseX);
        }
        hovered = hoveredIndex;
        updateHover(hoveredIndex);

        for (int index = 0; index < count(); index++) {
            if (index == selected) {
                continue;
            }
            float lift = hoverSpring(index).eased();
            if (lift <= 0.01F) {
                continue;
            }
            int segLeft = Math.round(segmentLeft(index)) + 2;
            int segRight = Math.round(segmentLeft(index) + segWidth) - 2;
            FlatTheme.fill(graphics, segLeft, top + 2, segRight, bottom - 2,
                    Math.max(1.0F, radius - 2.0F),
                    FlatTheme.alpha(0xFFFFFF, 0.05F * lift));
        }

        // 选中胶囊：在段间滑动。选中态是一块真正的液态玻璃，
        // 再罩一层主题色，和侧边栏选中项保持同一套语言。
        updatePill(segWidth);
        float pressAmount = GlassWidgets.clamp01(press.value());
        float shrink = 1.0F - PRESS_SHRINK * pressAmount;
        float pillWidth = segWidth * shrink;
        float pillCenter = pillX + segWidth * 0.5F;
        int pillLeft = Math.round(pillCenter - pillWidth * 0.5F) + 2;
        int pillRight = Math.round(pillCenter + pillWidth * 0.5F) - 2;
        float pillRadius = Math.max(1.0F, radius - 2.0F);
        boolean pillGlass = GlassWidgets.glassBackground(graphics, pillLeft, top + 2,
                pillRight, bottom - 2, GlassTheme.PILL, 0.90F, pressAmount);
        if (!pillGlass) {
            FlatTheme.fill(graphics, pillLeft, top + 2, pillRight, bottom - 2,
                    pillRadius, FlatTheme.alpha(0xFFFFFF, 0.16F - 0.04F * pressAmount));
        }
        int accentRgb = FlatTheme.accentRgb();
        GlassWidgets.roundedFill(graphics, pillLeft, top + 2, pillRight, bottom - 2,
                accentRgb, accentRgb, pillRadius, 0.16F - 0.03F * pressAmount, 1.0F);

        // 标签
        for (int index = 0; index < count(); index++) {
            Component label = labels.get(index);
            int centerX = Math.round(segmentLeft(index) + segWidth * 0.5F);
            int textY = Math.round(top + (height - 8.0F * LABEL_SCALE) * 0.5F);
            boolean active = index == selected;
            int color = active
                    ? (usable ? FlatTheme.TEXT : FlatTheme.TEXT_TERTIARY)
                    : GlassWidgets.dim(FlatTheme.TEXT_SECONDARY, usable ? IDLE_TEXT_KEEP : 0.55F);
            GlassWidgets.centeredText(graphics, font, label, centerX, textY, color, LABEL_SCALE);
        }
    }

    private void updateHover(int hoveredIndex) {
        while (hoverSprings.size() < count()) {
            hoverSprings.add(new GlassWidgets.Spring());
        }
        for (int index = 0; index < hoverSprings.size(); index++) {
            hoverSprings.get(index).tick(index == hoveredIndex && index != selected,
                    GlassTheme.Motion.HOVER);
        }
    }

    private GlassWidgets.Spring hoverSpring(int index) {
        return hoverSprings.get(index);
    }

    /** 每帧按真实时间把胶囊挪向目标位置。 */
    private void updatePill(float segWidth) {
        long now = Util.getMillis();
        float targetX = segmentLeft(selected);
        if (!pillInitialized) {
            pillX = targetX;
            pillInitialized = true;
            lastFrameMillis = now;
            return;
        }
        float deltaSeconds = lastFrameMillis < 0L ? 0.0F : (now - lastFrameMillis) / 1000.0F;
        lastFrameMillis = now;
        deltaSeconds = Math.min(Math.max(deltaSeconds, 0.0F), 0.1F);
        if (deltaSeconds <= 0.0F) {
            return;
        }
        float t = 1.0F - (float) Math.exp(-deltaSeconds / SLIDE_TAU);
        pillX += (targetX - pillX) * t;
        if (Math.abs(targetX - pillX) < 0.05F) {
            pillX = targetX;
        }
        if (Math.abs(segWidth - lastSegmentWidth) > 0.5F) {
            lastSegmentWidth = segWidth;
            pillX = targetX;
        }
    }
}
