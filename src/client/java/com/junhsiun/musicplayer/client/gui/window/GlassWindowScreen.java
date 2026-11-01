package com.junhsiun.musicplayer.client.gui.window;

import com.junhsiun.musicplayer.client.gui.glass.GlassPanel;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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

    /**
     * 计算"移出屏幕"位移时额外多给的安全余量（GUI 像素）。
     *
     * <p>窗口的圆角与边缘高光会略微溢出矩形本身，正好贴在屏幕上时可能还露出一条亮边。
     * 多推这么远，确保起点／终点是真正的"完全不可见"。</p>
     */
    private static final float TRAVEL_MARGIN = 8.0F;

    private final GlassWindow window;
    private final List<TrackedWidget> trackedWidgets = new ArrayList<>();

    private WindowBounds baseBounds = WindowBounds.EMPTY;
    private long animationStartMillis = -1L;

    /** 关闭动画已开始的时刻；{@code -1} 表示还没开始关闭。 */
    private long closingStartMillis = -1L;
    /** 关闭动画播完后是否已经真正退出界面，避免每帧重复调 {@code onClose()}。 */
    private boolean closeDispatched;

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

    /**
     * 关闭动画期间屏蔽鼠标交互。
     *
     * <p>窗口正在缩小下沉，此时还能点到按钮会显得"界面已经走了但还在响应"。
     * 只拦鼠标；键盘仍走 {@link #keyPressed}，否则中途想再按一次 E 也没反应。</p>
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closingStartMillis >= 0L) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (closingStartMillis >= 0L) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (closingStartMillis >= 0L) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * 关闭窗口。
     *
     * <p>不是立刻退出，而是先把关闭动画播完（{@link #closing()} 为 true 期间界面仍在渲染），
     * 由 {@link #tick()} 在动画结束后真正调 {@code super.onClose()}。</p>
     *
     * <p><b>关闭的瞬间就抓回鼠标</b>，让玩家在动画期间立刻能转视角，而不是等动画播完。
     * 原理见 {@link #grabMouseForClosing()}。</p>
     *
     * <p>重入保护：动画期间再次触发关闭（例如连按两次 E）直接忽略，不会重置时间轴。</p>
     */
    @Override
    public void onClose() {
        if (closingStartMillis >= 0L) {
            return;
        }
        closingStartMillis = Util.getMillis();
        grabMouseForClosing();
    }

    /**
     * 关闭动画期间接管鼠标视角。
     *
     * <p>界面还在，鼠标默认是松开状态（可以移动指针点控件），视角转不动；而关闭动画
     * 有两百多毫秒，这段时间玩家会明显感到"鼠标失灵"。</p>
     *
     * <p>做法是提前把鼠标抓回来（相对模式）。查过原版 {@code MouseHandler.handleAccumulatedMovement}
     * 的字节码，转视角的唯一条件是 {@code isMouseGrabbed()}，<b>并不检查界面是否还在</b>：</p>
     * <pre>
     *   287: isMouseGrabbed()
     *   291: ifeq 309            // 未抓取就跳过
     *   294: ... player.turn(...)
     * </pre>
     * <p>所以在界面关闭前抓回鼠标，视角就能立刻恢复跟随，动画照常播完。</p>
     *
     * <p>关闭期间本类的鼠标事件已全部被拦住（{@link #mouseClicked} 等），
     * 因此抓回鼠标不会导致误触控件。</p>
     */
    private void grabMouseForClosing() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.mouseHandler == null) {
            return;
        }
        if (!minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    /** 是否正在播关闭动画。 */
    protected final boolean closing() {
        return closingStartMillis >= 0L;
    }

    /**
     * 每帧推进关闭动画，播完后再真正退出界面。
     *
     * <p>放在 {@code tick} 而不是 {@code extractRenderState} 里：提取阶段只该做
     * "收集这一帧要画什么"，在那里调 {@code setScreen(null)} 会在同一帧中途拆掉界面，
     * 容易出现空指针或残留一帧。</p>
     */
    @Override
    public void tick() {
        super.tick();
        if (closingStartMillis < 0L || closeDispatched) {
            return;
        }
        if (WindowAnimations.dismiss().isFinished(Util.getMillis() - closingStartMillis)) {
            closeDispatched = true;
            super.onClose();
        }
    }

    /**
     * 把窗口<b>完全</b>移出屏幕下边缘所需的垂直位移。
     *
     * <p>算法即"让窗口上边缘停在屏幕下边缘"：{@code 屏幕高度 - 窗口上边缘}。
     * 位移等于它时窗口一个像素都看不见，这才是真正的"移出屏幕外"。</p>
     *
     * <p>用运行时几何实时算，而不是写死常数：窗口居中时它通常是两三百像素，
     * 写死几十像素的动画会让窗口还没离开画面就结束，看起来就是"突然出现／消失"。</p>
     *
     * <p>额外多给 {@link #TRAVEL_MARGIN} 像素余量，覆盖圆角与边缘高光的溢出，
     * 避免窗口贴着屏幕边缘时还露出一条亮边。</p>
     */
    private float travel() {
        return Math.max(0.0F, this.height - baseBounds.y0() + TRAVEL_MARGIN);
    }

    /**
     * 按「打开背包」键关闭窗口。
     *
     * <p>鼠标打开的界面用鼠标关不顺手，而 Esc 在某些场景下会连带退出到菜单，
     * 所以把「背包键」也接成关闭键，和大多数容器界面（箱子、工作台）习惯一致。</p>
     *
     * <p>用 {@code keyInventory.matches(...)} 判断而不是写死 {@code GLFW_KEY_E}：
     * 玩家改过按键绑定后仍然有效。</p>
     *
     * <p>顺序上必须先交给 {@code super}：Esc 关闭、Tab 与方向键的焦点导航、以及
     * 界面内控件的按键处理都在基类里，抢在前面会把它们全部吃掉。</p>
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options != null && minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics) {
        // 背景交给液态玻璃自己抓取：这里画原版暗色遮罩会让玻璃采样到被压暗的画面
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics) {
        WindowTransform transform = currentTransform();
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

    /**
     * 这一帧要用的变换。
     *
     * <p>开场和关闭是两条独立的时间轴，各自从自己的起点开始算，取当前真正生效的那条：</p>
     * <ul>
     *   <li>关闭动画一旦开始，就<b>只用</b>关闭的变换，不再叠加开场——否则窗口会
     *       同时"升起"和"滑出"，位移互相抵消，看起来像卡住不动；</li>
     *   <li>关闭与开场都以窗口的<b>基准矩形</b>为参照，位移量同取 {@link #travel()}，
     *       所以两段动画首尾对称：从屏幕外升进来，再滑出到屏幕外。</li>
     * </ul>
     */
    private WindowTransform currentTransform() {
        float travel = travel();
        if (closingStartMillis >= 0L) {
            return WindowAnimations.dismiss().sampleAt(Util.getMillis() - closingStartMillis, travel);
        }
        return window.transform(elapsed(), travel);
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
