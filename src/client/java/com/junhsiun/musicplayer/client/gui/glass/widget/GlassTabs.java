package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 分段控件（iOS segmented control 风格）。
 *
 * <p><b>材质：</b>三层液态玻璃。底槽是一条浅玻璃（{@link GlassTheme#TRACK}），悬停段是一块
 * 薄玻璃（{@link GlassTheme#ROW}），选中胶囊是抬起来的玻璃（{@link GlassTheme#PILL}）。
 * 三者都走 {@link GlassWidgets#glassBackground}，和按钮、开关是同一套封装，因此折射、
 * 色散、边缘高光、按下收缩全部一致；折射强度按层递减，叠在一起不会糊成一片。</p>
 *
 * <p><b>动效设计：</b>选中态是一枚玻璃胶囊，切换时在标签之间<b>滑</b>过去，而不是瞬间跳。
 * 位移用"指数逼近"而不是弹簧：分段控件的距离固定、目标明确，弹簧的回弹在这里只会显得毛躁；
 * 指数逼近在 60fps 与 144fps 下观感一致（按真实时间推进，不按帧数）。</p>
 *
 * <p><b>按压反馈：</b>按下时胶囊会轻微收窄并压暗，松开弹回。指针移出后再松开也一定复位——
 * 只认 {@code mouseReleased}，不依赖 hover 状态。</p>
 *
 * <p>没有做"指示器跟随手指拖拽"：标签是离散的，拖拽在 MC 的鼠标模型里收益很低。</p>
 */
public class GlassTabs extends AbstractWidget {

    /** 胶囊滑动的时间常数（秒）：越小越快。 */
    private static final float SLIDE_TAU = 0.055F;
    /** 按压收缩比例。 */
    private static final float PRESS_SHRINK = 0.04F;
    /** 未选中标签的亮度。 */
    private static final float IDLE_TEXT_KEEP = 0.62F;
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
        // 只认"在本控件上按下过"的松开；拖动不清 pressed，否则指针抖 1 像素就白点一次
        boolean armed = pressed >= 0;
        pressed = -1;
        if (!armed || count() == 0 || !isMouseOver(event.x(), event.y())) {
            return;
        }
        // 按落点决定切到哪一段：原版只要指针动 1 像素就会派发 drag 事件，
        // 以前在 onDrag 里清掉 pressed，于是"按下 → 手抖 → 松开"完全不生效，
        // 表现为要点很多次才切一次。顺带支持按住拖到别的段再松手。
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
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics) {
        if (count() == 0 || width <= 0 || height <= 0) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        // 按下快、松开回弹；只在渲染时推进，控件不需要额外的 tick
        press.tickSpring(pressed >= 0, pressed >= 0 ? GlassTheme.Motion.PRESS : GlassTheme.Motion.RELEASE);
        float segWidth = segmentWidth();
        float radius = Math.min(height * 0.5F, segWidth * 0.5F);

        int left = getX();
        int top = getY();
        int right = left + width;
        int bottom = top + height;

        // 底槽：一整条浅玻璃。
        //
        // 这里以前用的是 roundedFill（一层纯白半透明圆角矩形），所以整条分段控件看着是
        // "贴了一张白纸"，没有折射、没有边缘高光，和窗口的材质不是同一种东西。
        // 现在走和按钮/开关同一个封装（GlassWidgets.glassBackground）：底槽是第二层玻璃，
        // 选中胶囊是叠在上面的第三层，三层折射依次收敛，叠起来不会糊。
        if (!GlassWidgets.glassBackground(graphics, left, top, right, bottom,
                GlassTheme.TRACK, 0.0F, 0.0F)) {
            GlassWidgets.roundedFill(graphics, left, top, right, bottom,
                    0xFFFFFF, 0xFFFFFF, radius, 0.07F, 1.0F);
        }

        // 悬停段：鼠标位置每帧从 graphics 里取，不需要界面额外喂
        int hoveredIndex = -1;
        if (pressed < 0 && isMouseOver(graphics.mouseX(), graphics.mouseY())) {
            hoveredIndex = indexAt(graphics.mouseX());
        }
        hovered = hoveredIndex;
        updateHover(hoveredIndex);

        // 选中胶囊。先画悬停、再画胶囊，胶囊永远压在上面
        for (int index = 0; index < count(); index++) {
            if (index == selected) {
                continue;
            }
            float lift = hoverSpring(index).eased();
            if (lift <= 0.01F) {
                continue;
            }
            int segLeft = Math.round(segmentLeft(index)) + 1;
            int segRight = Math.round(segmentLeft(index) + segWidth) - 1;
            if (!GlassWidgets.glassBackground(graphics, segLeft, top + 1, segRight, bottom - 1,
                    GlassTheme.ROW, lift, 0.0F)) {
                GlassWidgets.roundedFill(graphics, segLeft, top + 1, segRight, bottom - 1,
                        0xFFFFFF, 0xFFFFFF, Math.max(1.0F, radius - 1.0F), 0.08F * lift, 1.0F);
            }
        }

        updatePill(segWidth);
        float pressAmount = GlassWidgets.clamp01(press.value());
        float shrink = 1.0F - PRESS_SHRINK * pressAmount;
        float pillWidth = segWidth * shrink;
        float pillCenter = pillX + segWidth * 0.5F;
        int pillLeft = Math.round(pillCenter - pillWidth * 0.5F);
        int pillRight = Math.round(pillCenter + pillWidth * 0.5F);
        // 胶囊是"被抬起来的那一块玻璃"：折射比底槽再收一档，只留边缘高光，
        // 观感上是玻璃片而不是一块实心白条
        if (!GlassWidgets.glassBackground(graphics, pillLeft, top + 1, pillRight, bottom - 1,
                GlassTheme.PILL, 0.0F, pressAmount)) {
            GlassWidgets.roundedFill(graphics, pillLeft, top + 1, pillRight, bottom - 1,
                    0xFFFFFF, 0xFFFFFF, Math.max(1.0F, radius - 1.0F),
                    0.20F - 0.06F * pressAmount, 1.0F);
        }

        // 标签文字
        for (int index = 0; index < count(); index++) {
            Component label = labels.get(index);
            int centerX = Math.round(segmentLeft(index) + segWidth * 0.5F);
            int textY = Math.round(top + (height - 8.0F * LABEL_SCALE) * 0.5F);
            boolean active = index == selected;
            float scale = LABEL_SCALE * (active ? 1.0F : 0.96F);
            int color = active
                    ? GlassTheme.TEXT
                    : GlassWidgets.dim(GlassTheme.TEXT_SECONDARY, IDLE_TEXT_KEEP);
            GlassWidgets.centeredText(graphics, font, label, centerX, textY, color, scale);
        }
    }

    /** 每帧推进悬停弹簧：只有当前悬停的那一段在亮，其余回落到 0。 */
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
        // 掉帧时别一次跳太远，钳到 0.1s，避免瞬间到位看起来像没动画
        deltaSeconds = Math.min(Math.max(deltaSeconds, 0.0F), 0.1F);
        if (deltaSeconds <= 0.0F) {
            return;
        }
        float t = 1.0F - (float) Math.exp(-deltaSeconds / SLIDE_TAU);
        pillX += (targetX - pillX) * t;
        if (Math.abs(targetX - pillX) < 0.05F) {
            pillX = targetX;
        }
        // 段宽变化（窗口缩放）时直接把位置对齐，不做补间
        if (Math.abs(segWidth - lastSegmentWidth) > 0.5F) {
            lastSegmentWidth = segWidth;
            pillX = targetX;
        }
    }

    private float lastSegmentWidth = -1.0F;
}
