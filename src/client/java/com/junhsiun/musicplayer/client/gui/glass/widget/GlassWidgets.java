package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.GlassDebug;
import com.junhsiun.musicplayer.client.gui.glass.GlassPanel;
import com.junhsiun.musicplayer.client.gui.glass.GlassPipelines;
import com.junhsiun.musicplayer.client.gui.glass.GlassStyle;
import com.junhsiun.musicplayer.client.gui.glass.GlassWidgetPipelines;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 控件公共绘制：玻璃底、圆角填充、缩放文字、交互弹簧。 */
public final class GlassWidgets {

    /**
     * 玻璃方块的裁剪区栈。
     *
     * <p>GUI 渲染是单线程的，静态字段在这里是安全的；用栈是为了支持嵌套（列表里再套一层
     * 滚动区），语义与 {@code GuiGraphicsExtractor} 的 scissor 栈一致。</p>
     */
    private static final Deque<ScreenRectangle> CLIP_STACK = new ArrayDeque<>();

    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final Set<String> CHECKED = ConcurrentHashMap.newKeySet();

    private GlassWidgets() {
    }

    /** 归一到 0..1。 */
    public static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    /**
     * 交互进度弹簧，帧率无关。
     *
     * <p>{@link #tick} 平滑不过冲，用于亮度；{@link #tickSpring} 轻微过冲，
     * 用于 macOS 那种按下回弹。</p>
     */
    public static final class Spring {
        private float value;
        private float velocity;
        private long lastMillis = -1L;

        /** 平滑收敛到目标。 */
        public void tick(boolean active, float tau) {
            long now = Util.getMillis();
            float dt = lastMillis < 0L ? 0.0F : Math.min((now - lastMillis) / 1000.0F, 0.1F);
            lastMillis = now;
            float target = active ? 1.0F : 0.0F;
            value = Mth.lerp(1.0F - (float) Math.exp(-dt / Math.max(tau, 1.0E-4F)), value, target);
        }

        /** 二阶收敛，阻尼比 0.62 带来轻微回弹。 */
        public void tickSpring(boolean active, float tau) {
            long now = Util.getMillis();
            float dt = lastMillis < 0L ? 0.0F : Math.min((now - lastMillis) / 1000.0F, 0.1F);
            lastMillis = now;
            float target = active ? 1.0F : 0.0F;
            float omega = 1.0F / Math.max(tau, 1.0E-4F);
            float damping = 0.62F * 2.0F * omega;
            velocity += (omega * omega * (target - value) - damping * velocity) * dt;
            value += velocity * dt;
        }

        /** 直接跳到目标值，用于初始化。 */
        public void snap(float target) {
            value = target;
            velocity = 0.0F;
        }

        public float value() {
            return value;
        }

        /** 当前速度，用于跟随运动的形变。 */
        public float velocity() {
            return velocity;
        }

        /** 缓出后的进度，用于亮度。 */
        public float eased() {
            return Mth.smoothstep(clamp01(value));
        }
    }

    /**
     * 画控件玻璃底：纯折射玻璃 + 统一的背景色叠加层。
     *
     * <p>染色不烘焙进管线（那会每次改透明度都重编译），而是叠一层顶点色圆角填充，
     * 所以透明度滑块能连续生效且不卡顿。</p>
     *
     * @param lift  hover 提亮，{@code press} 是按下压暗；只改几何与逐 draw 光学参数
     * @return 是否画成功，失败时调用方应退回 {@link #roundedFill}
     */
    public static boolean glassBackground(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                          GlassStyle style, float lift, float press) {
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        // 按下时轻微收缩，命中区不变。和 roundedFill 一样，收缩量取整而不是反推中心，
        // 否则滑块的玻璃圆珠也会随宽度变化在左右各抖 1px。
        float scale = 1.0F - 0.03F * clamp01(press);
        int insetX = Math.round((x1 - x0) * (1.0F - scale) * 0.5F);
        int insetY = Math.round((y1 - y0) * (1.0F - scale) * 0.5F);
        int left = x0 + insetX;
        int top = y0 + insetY;
        int right = Math.max(left + 1, x1 - insetX);
        int bottom = Math.max(top + 1, y1 - insetY);

        // hover 时边缘折射更明显，按下时收敛，像真被压下去
        float refraction = style.refraction() * (1.0F + 0.45F * lift) * (1.0F - 0.35F * press);
        float dispersion = style.dispersion() * (1.0F + 0.30F * lift);
        float center = style.centerDispersion() * (1.0F + 0.40F * lift);
        // 按下时 a 稍微内收，观感上像边缘被压厚了一点
        float aDistance = GlassDebug.aDistance(style) * (1.0F + 0.15F * press);
        boolean drawn = GlassPanel.drawTuned(graphics, left, top, right, bottom,
                style, refraction, dispersion, center,
                aDistance, GlassDebug.contraRatio(style), GlassDebug.shrink(style));

        // hover / 按下时叠一层很淡的提亮。
        //
        // 圆角半径必须和玻璃的 SDF 用<b>同一套规则</b>：着色器取
        // min(材质半径, 半宽, 半高)，所以圆形圆珠、胶囊开关的玻璃会自己收成正圆/胶囊。
        // 叠加层若用固定的小圆角（曾经的 Sizes.RADIUS = 5），形状就对不上，
        // 圆控件外面会多出一层圆角矩形，看起来像"包了一层"。
        // 这里按同样的规则算半径，两者轮廓严格重合。
        float radius = Math.min(style.radius(), Math.min((right - left) * 0.5F, (bottom - top) * 0.5F));
        float alpha = 0.10F * lift - 0.05F * press;
        if (drawn && alpha > 0.01F) {
            overlayFill(graphics, left, top, right, bottom, radius, alpha);
        }
        return drawn;
    }

    /** 窗口的叠加层。 */
    public static void windowOverlay(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                     float radius) {
        overlayFill(graphics, x0, y0, x1, y1, radius, GlassTheme.overlayAlpha());
    }

    /** 叠加层：纯色圆角填充，描边与柔光都关掉。 */
    private static void overlayFill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                    float radius, float alpha) {
        int rgb = GlassTheme.overlayColor() & 0xFFFFFF;
        roundedFill(graphics, x0, y0, x1, y1, rgb, rgb, radius, alpha, 1.0F);
    }
    /** 圆角填充，玻璃不可用时的退化路径。 */
    public static void roundedFill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                   int fill, int border, float radius, float alpha, float scale) {
        if (x1 <= x0 || y1 <= y0 || alpha <= 0.02F) {
            return;
        }
        // 缩放要围绕中心对称，但<b>必须保留 x0/x1 这个原始边界</b>。
        // 早先的写法是先算中心、再按半宽反推左右边界：cx = (x0+x1)/2 是整数除法，
        // 半宽又要四舍五入，两者一凑，左边界会随宽度奇偶在 15/16 之间来回跳 1px。
        // 滑块的已填充段右端随拖动逐像素变化，于是它的<b>左端一直在抖</b>。
        // 改成只把"收缩量"取整，左右各自向内缩同样的量，边界就精确不变了。
        int insetX = Math.round((x1 - x0) * (1.0F - scale) * 0.5F);
        int insetY = Math.round((y1 - y0) * (1.0F - scale) * 0.5F);
        int left = x0 + insetX;
        int top = y0 + insetY;
        int right = Math.max(left + 1, x1 - insetX);
        int bottom = Math.max(top + 1, y1 - insetY);

        int key = java.util.Objects.hash(fill & 0xFFFFFF, border & 0xFFFFFF, radius);
        String keyString = Integer.toHexString(key);
        int color = ((int) (clamp01(alpha) * 255.0F)) << 24 | 0xFFFFFF;

        // 着色器不可用时不能退回 graphics.fill：那是方角矩形，贴在圆形/胶囊控件上
        // 会露出一块方色块（正是 hover 时"外面包一层圆角矩形"的同类问题，且更难看）。
        // 圆角足够大说明这是圆/胶囊控件，宁可不画这层很淡的叠加。
        if (FAILED.contains(keyString)) {
            if (radius < Math.min(x1 - x0, y1 - y0) * 0.5F - 0.5F) {
                graphics.fill(left, top, right, bottom, (color & 0xFF000000) | (fill & 0xFFFFFF));
            }
            return;
        }

        RenderPipeline pipeline = GlassWidgetPipelines.roundedFill(
                fill & 0xFFFFFF, border & 0xFFFFFF, radius, 0.16F, 0.10F);
        if (!CHECKED.contains(keyString)) {
            CHECKED.add(keyString);
            if (!GlassWidgetPipelines.prepare(pipeline)) {
                FAILED.add(keyString);
                if (radius < Math.min(x1 - x0, y1 - y0) * 0.5F - 0.5F) {
                    graphics.fill(left, top, right, bottom, (color & 0xFF000000) | (fill & 0xFFFFFF));
                }
                return;
            }
        }
        submit(graphics, pipeline, left, top, right, bottom, color);
    }

    /** 叠加文字缩放，调用方必须成对使用 push/pop。 */
    public static void pushScaledText(GuiGraphicsExtractor graphics, int anchorX, int anchorY, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(anchorX, anchorY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-anchorX, -anchorY);
    }

    public static void popText(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
    }

    /** 左对齐文字。 */
    public static void text(GuiGraphicsExtractor graphics, Font font, Component text,
                            int x, int y, int color, float scale) {
        pushScaledText(graphics, x, y, scale);
        graphics.text(font, text, x, y, color);
        popText(graphics);
    }

    /** 居中文字。 */
    public static void centeredText(GuiGraphicsExtractor graphics, Font font, Component text,
                                    int centerX, int y, int color, float scale) {
        pushScaledText(graphics, centerX, y, scale);
        graphics.centeredText(font, text, centerX, y, color);
        popText(graphics);
    }

    /** 右对齐文字，宽度按缩放后的占位算。 */
    public static void rightText(GuiGraphicsExtractor graphics, Font font, Component text,
                                 int rightX, int y, int color, float scale) {
        text(graphics, font, text, Math.round(rightX - font.width(text) * scale), y, color, scale);
    }

    /** 按比例压暗，用于按下与禁用反馈。 */
    public static int dim(int argb, float amount) {
        float keep = 1.0F - Math.min(Math.max(amount, 0.0F), 0.8F);
        int r = (int) (((argb >> 16) & 0xFF) * keep);
        int g = (int) (((argb >> 8) & 0xFF) * keep);
        int b = (int) ((argb & 0xFF) * keep);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /**
     * 开始一段裁剪：<b>同时</b>作用于原版绘制和玻璃方块。
     *
     * <p>为什么不能只用 {@code graphics.enableScissor}：玻璃方块不是原版绘制，而是本类自己
     * 组 {@code BlitRenderState} 提交的。原版的 scissor 栈只挂到它自己产出的渲染状态上，
     * 手写提交的那些方块完全看不到它——表现就是"文字被切掉了，玻璃还在外面画"（徽标、
     * 播放按钮、行底会在滚动区外露出来）。</p>
     *
     * <p>这里维护一份自己的裁剪区栈，提交玻璃时作为 {@code scissorArea} 传给渲染状态，
     * 由渲染器真正裁剪。这样滚动区里的<b>所有</b>元素（文字、图标、玻璃、圆角填充）都由
     * 同一道边界遮挡，不需要每个元素自己算可见区。</p>
     *
     * <p>必须与 {@link #endClip} 成对使用；嵌套时取交集，与 {@code enableScissor} 一致。</p>
     */
    public static void beginClip(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
        ScreenRectangle rect = new ScreenRectangle(x0, y0,
                Math.max(0, x1 - x0), Math.max(0, y1 - y0));
        ScreenRectangle parent = CLIP_STACK.peek();
        CLIP_STACK.push(parent == null ? rect : parent.intersection(rect));
    }

    /** 清空裁剪区。每帧开头调一次，防止某个控件漏掉 {@link #endClip} 之后一直影响后续绘制。 */
    public static void resetClip() {
        CLIP_STACK.clear();
    }

    /** 结束一段裁剪，与 {@link #beginClip} 成对使用。 */
    public static void endClip(GuiGraphicsExtractor graphics) {
        if (!CLIP_STACK.isEmpty()) {
            CLIP_STACK.pop();
        }
        graphics.disableScissor();
    }

    /**
     * 当前玻璃裁剪区；没有裁剪时为 {@code null}。
     *
     * <p>公开给 {@link com.junhsiun.musicplayer.client.gui.glass.GlassPanel}：玻璃面板也是
     * 手写 {@code BlitRenderState} 提交的，必须拿同一个裁剪区，否则面板（行底、徽标、
     * 播放按钮）会绕过滚动区的边界画到外面去。</p>
     */
    public static ScreenRectangle clipRectangle() {
        return CLIP_STACK.peek();
    }

    private static void submit(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
                               int x0, int y0, int x1, int y1, int color) {
        ScreenRectangle clip = clipRectangle();
        if (clip != null
                && (x1 <= clip.left() || x0 >= clip.right() || y1 <= clip.top() || y0 >= clip.bottom())) {
            // 完全在裁剪区外：连渲染状态都不用提交
            return;
        }
        // 第十三个参数是 scissorArea，渲染器据此真正裁剪；完全靠它遮挡，
        // 不在这里改坐标——改坐标会把圆角/边缘畸变的形状弄歪
        graphics.guiRenderState.addGuiElement(new BlitRenderState(
                GlassPipelines.gui(pipeline),
                TextureSetup.noTexture(),
                new Matrix3x2f(graphics.pose()),
                x0, y0, x1, y1,
                0.0F, 1.0F,
                0.0F, 1.0F,
                color,
                clip));
    }
}
