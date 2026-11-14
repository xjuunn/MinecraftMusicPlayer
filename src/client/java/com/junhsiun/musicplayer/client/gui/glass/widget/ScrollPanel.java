package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * 垂直滚动容器：把若干控件装进一个固定高度的视口里，内容超出时可以滚动。
 *
 * <p><b>为什么需要它：</b>设置页是一整页表单，控件各自是独立的 {@link AbstractWidget}。
 * 如果直接把它们铺在窗口里，屏幕高度不够时控件会画到玻璃窗口外面去；而原版没有
 * 可以嵌套任意控件的滚动容器。</p>
 *
 * <p><b>做法：</b>容器本身是唯一加入界面的控件，内部子控件按基准坐标 + 滚动偏移
 * 重新摆放，绘制时用 {@link GlassWidgets#beginClip} 统一裁剪，再把鼠标、键盘事件
 * 转发给命中的子控件。这样滚动、裁剪、拖拽、文本输入都与普通控件一致，
 * 也不需要改动任何已有控件。</p>
 */
public class ScrollPanel extends AbstractWidget implements ContainerEventHandler {

    /** 右下角滚动条的宽度。 */
    private static final int SCROLLBAR_WIDTH = 3;
    /** 滚动条与内容右边缘的间隙。 */
    private static final int SCROLLBAR_GAP = 4;
    /** 滚动条停手后淡出的时长（毫秒）。 */
    private static final float SCROLLBAR_FADE_MILLIS = 400.0F;
    /** 滚动条最后一次滚动后保持完全可见的时长（毫秒）。 */
    private static final long SCROLLBAR_HOLD_MILLIS = 700L;
    /** 一次滚轮折算的位移（GUI 像素）。 */
    private static final double SCROLL_STEP = 26.0D;

    private final List<Child> children = new ArrayList<>();
    private final GlassScroll scroll = new GlassScroll();

    private GuiEventListener focusedChild;
    private boolean dragging;
    private int contentHeight;
    private long scrollbarUntilMillis;

    public ScrollPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        // 容器本身要接收鼠标：AbstractWidget.isMouseOver 会在 active=false 时直接返回 false，
        // 那样滚轮和子控件点击都到不了这里。
        this.active = true;
    }

    /**
     * 加入一个子控件。控件当前的坐标会被当作滚动偏移为 0 时的基准位置。
     * 子控件不要另外加入界面，否则会被重复绘制。
     */
    public ScrollPanel add(AbstractWidget widget) {
        if (widget != null) {
            children.add(new Child(widget, widget.getX() - getX(), widget.getY() - getY()));
            reflow();
        }
        return this;
    }

    /** 内容全部加完后调一次，重新计算滚动范围。 */
    public void finish() {
        reflow();
        scroll.setMax(maxScroll());
    }

    /** 当前滚动位置（像素）；重建控件前记下来，建好后可以还原。 */
    public double position() {
        return scroll.position();
    }

    /** 立刻回到指定滚动位置，不做平滑动画（重建后还原位置用）。 */
    public void scrollTo(double value) {
        scroll.setMax(maxScroll());
        scroll.jumpTo(value);
        scroll.snap();
    }

    private void reflow() {
        int bottom = 0;
        for (Child child : children) {
            bottom = Math.max(bottom, child.baseY() + Math.max(1, child.widget().getHeight()));
        }
        contentHeight = bottom;
    }

    private double maxScroll() {
        return Math.max(0.0D, contentHeight - height);
    }

    private void layoutChildren() {
        int offset = scroll.offset();
        for (Child child : children) {
            child.widget().setX(getX() + child.baseX());
            child.widget().setY(getY() + child.baseY() - offset);
        }
    }

    /** 鼠标下最上层的可见子控件。 */
    private AbstractWidget childAt(double mouseX, double mouseY) {
        for (int index = children.size() - 1; index >= 0; index--) {
            AbstractWidget widget = children.get(index).widget();
            if (widget.visible && widget.isMouseOver(mouseX, mouseY)) {
                return widget;
            }
        }
        return null;
    }

    private void markScrolling() {
        scrollbarUntilMillis = Util.getMillis()
                + (long) SCROLLBAR_FADE_MILLIS + SCROLLBAR_HOLD_MILLIS;
    }

    // ── 绘制 ────────────────────────────────────────────────────────────

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (width <= 0 || height <= 0) {
            return;
        }
        scroll.setMax(maxScroll());
        scroll.tick();
        layoutChildren();

        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;

        GlassWidgets.beginClip(graphics, x0, y0, x1, y1);
        for (Child child : children) {
            AbstractWidget widget = child.widget();
            if (!widget.visible) {
                continue;
            }
            int top = widget.getY();
            int bottom = top + widget.getHeight();
            // 完全在视口外：跳过绘制，长表单滚动时不必每帧排版所有行
            if (bottom <= y0 || top >= y1) {
                continue;
            }
            widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
        GlassWidgets.endClip(graphics);

        drawScrollbar(graphics, x0, y0, x1, y1);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        double max = maxScroll();
        if (max <= 0.0D) {
            return;
        }
        float alpha = GlassWidgets.clamp01(
                (scrollbarUntilMillis - Util.getMillis()) / SCROLLBAR_FADE_MILLIS);
        if (alpha <= 0.02F) {
            return;
        }
        int trackX = x1 - SCROLLBAR_WIDTH;
        int trackTop = y0 + 2;
        int trackHeight = Math.max(0, y1 - y0 - 4);
        if (trackHeight <= 4) {
            return;
        }
        FlatTheme.fill(graphics, trackX, trackTop, trackX + SCROLLBAR_WIDTH,
                trackTop + trackHeight, SCROLLBAR_WIDTH / 2.0F,
                FlatTheme.alpha(0xFFFFFF, 0.05F * alpha));
        int total = Math.max(1, contentHeight);
        int thumbHeight = Math.max(14, trackHeight * height / total);
        int thumbTop = trackTop
                + (int) Math.round((trackHeight - thumbHeight) * (scroll.position() / max));
        FlatTheme.fill(graphics, trackX, thumbTop, trackX + SCROLLBAR_WIDTH,
                thumbTop + thumbHeight, SCROLLBAR_WIDTH / 2.0F,
                FlatTheme.alpha(0xFFFFFF, 0.24F * alpha));
    }

    // ── 输入转发 ────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (!isMouseOver(event.x(), event.y())) {
            return false;
        }
        AbstractWidget target = childAt(event.x(), event.y());
        if (target == null) {
            setFocused(null);
            return false;
        }
        boolean handled = target.mouseClicked(event, doubled);
        if (handled) {
            setFocused(target);
            dragging = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        GuiEventListener focused = focusedChild;
        return focused != null && focused.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging || focusedChild == null
                || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        return focusedChild.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0.0D) {
            return false;
        }
        scroll.setMax(maxScroll());
        if (!scroll.canScroll()) {
            return false;
        }
        scroll.impulse(-scrollY * SCROLL_STEP);
        markScrolling();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return focusedChild != null && focusedChild.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return focusedChild != null && focusedChild.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return focusedChild != null && focusedChild.charTyped(event);
    }

    // ── 焦点 / 拖拽 ──────────────────────────────────────────────────────

    @Override
    public List<? extends GuiEventListener> children() {
        List<GuiEventListener> widgets = new ArrayList<>(children.size());
        for (Child child : children) {
            widgets.add(child.widget());
        }
        return widgets;
    }

    @Override
    public GuiEventListener getFocused() {
        return focusedChild;
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        if (focusedChild == focused) {
            return;
        }
        GuiEventListener previous = focusedChild;
        focusedChild = focused;
        if (previous != null) {
            previous.setFocused(false);
        }
        if (focused != null) {
            focused.setFocused(true);
        }
    }

    /** 容器失去焦点时，把内部控件的高亮一起清掉。 */
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            setFocused((GuiEventListener) null);
            dragging = false;
        }
    }

    @Override
    public boolean isDragging() {
        return dragging;
    }

    @Override
    public void setDragging(boolean value) {
        this.dragging = value;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private record Child(AbstractWidget widget, int baseX, int baseY) {
    }
}