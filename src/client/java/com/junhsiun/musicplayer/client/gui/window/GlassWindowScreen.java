package com.junhsiun.musicplayer.client.gui.window;

import com.junhsiun.musicplayer.client.gui.glass.GlassPanel;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * 承载 {@link GlassWindow} 的界面基类。
 *
 * <p>子类只需要做两件事：通过构造函数传一个配置好的 {@link GlassWindow}，
 * 然后在 {@link #buildContent(WindowBounds)} 里往内容区摆放控件。窗口的定位、动画推进、
 * 控件跟随、玻璃绘制全部由基类负责。</p>
 *
 * <p><b>控件为什么会动：</b>基类记录每个控件被添加时的坐标作为基准位置，每帧用窗口当前的
 * {@link WindowTransform} 对基准位置做同样的平移和绕心缩放。所以窗口弹出、弹跳、拉伸时，
 * 内容会跟着容器一起动，不需要子类操心态。</p>
 *
 * <p><b>绘制顺序：</b>玻璃先提交、控件后提交。{@code GuiRenderState} 是分层结构，
 * {@code addGuiElement} 会把新元素放进所有"边界相交"元素的上层，因此控件必定浮在玻璃之上，
 * 与管线排序无关。</p>
 */
public abstract class GlassWindowScreen extends Screen {

    private final GlassWindow window;
    private final List<TrackedWidget> trackedWidgets = new ArrayList<>();

    private WindowBounds baseBounds = WindowBounds.EMPTY;
    private long animationStartMillis = -1L;

    protected GlassWindowScreen(GlassWindow window) {
        super(Component.empty());
        this.window = window;
    }

    /** 窗口配置。运行时改尺寸/位置后调用 {@code rebuildWidgets()} 生效。 */
    public final GlassWindow window() {
        return window;
    }

    /**
     * 摆放内容控件。坐标以传入的内容区为参照，控件会自动跟随窗口动画。
     *
     * <p>每次 {@code init()}（首次打开、窗口缩放、{@link #replayOpening()} 触发的重建）都会调用。</p>
     */
    protected abstract void buildContent(WindowBounds content);

    @Override
    protected void init() {
        trackedWidgets.clear();
        baseBounds = window.layout(this.width, this.height);
        buildContent(window.contentBounds(baseBounds));
        // 只有界面第一次 init（真正"打开"）才播开场动画；resize、rebuildWidgets
        // 走到这里时窗口已经在屏幕上了，再弹一遍会很奇怪。
        if (animationStartMillis < 0L) {
            animationStartMillis = Util.getMillis();
        }
    }

    /** 重新播放一次开场动画，便于反复观察手感。 */
    protected final void replayOpening() {
        animationStartMillis = Util.getMillis();
    }

    /** 开场动画是否已经播完。 */
    protected final boolean isOpeningFinished() {
        return window.opening().isFinished(elapsed());
    }

    @Override
    protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget) {
        T added = super.addRenderableWidget(widget);
        if (added instanceof AbstractWidget abstractWidget) {
            trackedWidgets.add(new TrackedWidget(abstractWidget, abstractWidget.getX(), abstractWidget.getY()));
        }
        return added;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics) {
        // 背景交给液态玻璃自己抓取：这里画原版暗色遮罩会让玻璃采样到被压暗的画面
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics) {
        WindowTransform transform = window.transform(elapsed());
        WindowBounds animated = transform.apply(baseBounds);

        GlassPanel.setLook(GlassTheme.look());
        GlassPanel.beginFrame(window.style());
        // 控件玻璃用几份固定材质，提前校验编译
        GlassPanel.beginFrame(GlassTheme.BUTTON);
        GlassPanel.beginFrame(GlassTheme.TOGGLE);
        GlassPanel.beginFrame(GlassTheme.KNOB);
        applyTransformToChildren(transform);
        GlassPanel.draw(graphics, animated.x0(), animated.y0(), animated.x1(), animated.y1(), window.style());
        // 窗口的染色同样走叠加层，和控件共用同一个透明度
        GlassWidgets.windowOverlay(graphics, animated.x0(), animated.y0(),
                animated.x1(), animated.y1(), GlassTheme.Sizes.WINDOW_RADIUS);

        // 控件必须在玻璃之后提交，才会落在玻璃上层（见类注释）
        super.extractRenderState(graphics);
    }

    private long elapsed() {
        return Util.getMillis() - animationStartMillis;
    }

    private void applyTransformToChildren(WindowTransform transform) {
        float centerX = baseBounds.centerX();
        float centerY = baseBounds.centerY();
        for (TrackedWidget entry : trackedWidgets) {
            entry.widget().setX(Math.round(transform.applyX(entry.baseX(), centerX)));
            entry.widget().setY(Math.round(transform.applyY(entry.baseY(), centerY)));
        }
    }

    /** 控件被添加时的基准位置，用来在动画中还原它应该在哪。 */
    private record TrackedWidget(AbstractWidget widget, int baseX, int baseY) {
    }
}
