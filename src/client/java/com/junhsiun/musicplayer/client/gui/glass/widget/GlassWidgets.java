package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.GlassDebug;
import com.junhsiun.musicplayer.client.gui.glass.GlassPanel;
import com.junhsiun.musicplayer.client.gui.glass.GlassPipelines;
import com.junhsiun.musicplayer.client.gui.glass.GlassStyle;
import com.junhsiun.musicplayer.client.gui.glass.GlassWidgetPipelines;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;

/** 控件公共绘制：玻璃底、圆角填充、缩放文字、交互弹簧。 */
public final class GlassWidgets {

    /**
     * 裁剪区栈。
     *
     * <p>GUI 渲染是单线程的，静态字段在这里是安全的；用栈是为了支持嵌套（列表里再套一层
     * 滚动区），语义与 {@code GuiGraphics} 的 scissor 栈一致。</p>
     *
     * <p>1.21.1 没有手写 BlitRenderState 提交路径，玻璃与填充都走立即渲染，
     * 由原版 scissor（{@code graphics.enableScissor}）统一裁剪。</p>
     */
    private static final Deque<Clip> CLIP_STACK = new ArrayDeque<>();

    /**
     * 一层裁剪的信息。
     *
     * @param glass       给玻璃方块用的裁剪区，退化时跳过绘制
     * @param scissorPushed 这一层是否真的调用过 {@code enableScissor}；
     *                      {@link #endClip} 必须按它决定要不要 {@code disableScissor}
     */
    private record Clip(ScreenRectangle glass, boolean scissorPushed) {
    }

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
            value = net.minecraft.util.Mth.lerp(1.0F - (float) Math.exp(-dt / Math.max(tau, 1.0E-4F)), value, target);
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
            lastMillis = -1L;
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
            float t = clamp01(value);
            return t * t * (3.0F - 2.0F * t);
        }
    }

    /**
     * 画控件玻璃底：纯折射玻璃 + 统一的背景色叠加层。
     *
     * <p>染色不烘焙进管线，绘制前写 uniforms，所以透明度滑块能连续生效且不卡顿。</p>
     *
     * @param lift  hover 提亮，{@code press} 是按下压暗
     * @return 是否画成功，失败时调用方应退回 {@link #roundedFill}
     */
    public static boolean glassBackground(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                          GlassStyle style, float lift, float press) {
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        // 按下时轻微收缩，命中区不变
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
        float aDistance = GlassDebug.aDistance(style) * (1.0F + 0.15F * press);
        boolean drawn = GlassPanel.drawTuned(graphics, left, top, right, bottom,
                style, refraction, dispersion, center,
                aDistance, GlassDebug.contraRatio(style), GlassDebug.shrink(style));

        // hover / 按下时叠一层很淡的提亮。圆角半径与玻璃 SDF 用同一套规则，
        // 圆形圆珠、胶囊开关的叠加层轮廓与玻璃严格重合。
        float radius = Math.min(style.radius(), Math.min((right - left) * 0.5F, (bottom - top) * 0.5F));
        float alpha = 0.10F * lift - 0.05F * press;
        if (drawn && alpha > 0.01F) {
            overlayFill(graphics, left, top, right, bottom, radius, alpha);
        }
        return drawn;
    }

    /** 窗口的叠加层。 */
    public static void windowOverlay(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                     float radius) {
        overlayFill(graphics, x0, y0, x1, y1, radius, GlassTheme.overlayAlpha());
    }

    /** 叠加层：纯色圆角填充。 */
    private static void overlayFill(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                    float radius, float alpha) {
        int rgb = GlassTheme.overlayColor() & 0xFFFFFF;
        roundedFill(graphics, x0, y0, x1, y1, rgb, rgb, radius, alpha, 1.0F);
    }

    /**
     * 圆角填充，玻璃不可用时的退化路径。
     *
     * <p>1.21.1 走 {@code glass_widget} 着色器（立即渲染）；着色器不可用时退化为
     * 方角 {@code graphics.fill}——只在圆角小于短边一半时退化，圆/胶囊控件宁可
     * 不画这层很淡的叠加，避免露出一块方色块。</p>
     */
    public static void roundedFill(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                   int fill, int border, float radius, float alpha, float scale) {
        if (x1 <= x0 || y1 <= y0 || alpha <= 0.02F) {
            return;
        }
        // 缩放要围绕中心对称，但<b>必须保留 x0/x1 这个原始边界</b>（只把收缩量取整，
        // 左右各自向内缩同样的量，边界就精确不变，滑块已填充段的左端不会随宽度奇偶抖动）。
        int insetX = Math.round((x1 - x0) * (1.0F - scale) * 0.5F);
        int insetY = Math.round((y1 - y0) * (1.0F - scale) * 0.5F);
        int left = x0 + insetX;
        int top = y0 + insetY;
        int right = Math.max(left + 1, x1 - insetX);
        int bottom = Math.max(top + 1, y1 - insetY);

        ShaderInstance shader = GlassWidgetPipelines.roundedFill();
        boolean round = radius >= Math.min(right - left, bottom - top) * 0.5F - 0.5F;
        if (shader == null) {
            // 着色器不可用：方角矩形会破坏圆/胶囊轮廓，只有非全圆角时才退化
            if (!round) {
                graphics.fill(left, top, right, bottom,
                        (((int) (clamp01(alpha) * 255.0F)) << 24) | (fill & 0xFFFFFF));
            }
            return;
        }

        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        writeWidgetUniforms(shader, fill, border, radius, alpha);
        shader.safeGetUniform("GuiSize").set(
                (float) graphics.guiWidth(), (float) graphics.guiHeight());

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(pose, left, top, 0.0F).uv(0.0F, 0.0F).color(0xFFFFFFFF).endVertex();
        builder.vertex(pose, left, bottom, 0.0F).uv(0.0F, 1.0F).color(0xFFFFFFFF).endVertex();
        builder.vertex(pose, right, bottom, 0.0F).uv(1.0F, 1.0F).color(0xFFFFFFFF).endVertex();
        builder.vertex(pose, right, top, 0.0F).uv(1.0F, 0.0F).color(0xFFFFFFFF).endVertex();
        BufferBuilder.RenderedBuffer mesh = builder.end();
        if (mesh != null) {
            try (VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC)) {
                vb.bind();
                vb.upload(mesh);
                vb.drawWithShader(RenderSystem.getModelViewMatrix(),
                        RenderSystem.getProjectionMatrix(), shader);
            }
            VertexBuffer.unbind();
        }
        // 见 GlassPanel#drawGlassQuad：1.20.1 的 BlendMode 会缓存「是否非 opaque」，
        // 这里关掉混合而不改写缓存，后续原版绘制会因为状态未变而跳过 enableBlend，
        // 半透明内容全变成不透明色块。所以结束时要把混合还原成开着的样子。
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** 把圆角填充参数写进 {@code glass_widget} 着色器的 uniforms。 */
    private static void writeWidgetUniforms(ShaderInstance shader, int fill, int border,
                                            float radius, float alpha) {
        shader.safeGetUniform("WidgetFill").set(
                ((fill >> 16) & 0xFF) / 255.0F,
                ((fill >> 8) & 0xFF) / 255.0F,
                (fill & 0xFF) / 255.0F,
                clamp01(alpha));
        shader.safeGetUniform("WidgetBorder").set(
                ((border >> 16) & 0xFF) / 255.0F,
                ((border >> 8) & 0xFF) / 255.0F,
                (border & 0xFF) / 255.0F,
                0.16F);
        shader.safeGetUniform("WidgetRadius").set(radius);
        shader.safeGetUniform("WidgetGloss").set(0.10F);
    }

    /** 叠加文字缩放，调用方必须成对使用 push/pop。 */
    public static void pushScaledText(GuiGraphics graphics, int anchorX, int anchorY, float scale) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(anchorX, anchorY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-anchorX, -anchorY, 0.0F);
    }

    public static void popText(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    /** 左对齐文字。 */
    public static void text(GuiGraphics graphics, Font font, Component text,
                            int x, int y, int color, float scale) {
        pushScaledText(graphics, x, y, scale);
        graphics.drawString(font, text, x, y, color);
        popText(graphics);
    }

    /** 居中文字。 */
    public static void centeredText(GuiGraphics graphics, Font font, Component text,
                                    int centerX, int y, int color, float scale) {
        pushScaledText(graphics, centerX, y, scale);
        graphics.drawCenteredString(font, text, centerX, y, color);
        popText(graphics);
    }

    /** 右对齐文字，宽度按缩放后的占位算。 */
    public static void rightText(GuiGraphics graphics, Font font, Component text,
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
     * 开始一段裁剪：交给原版 scissor 统一遮挡。
     *
     * <p>必须与 {@link #endClip} 成对使用；嵌套时原版栈自动取交集。</p>
     */
    public static void beginClip(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        if (x1 <= x0 || y1 <= y0) {
            // 退化矩形（宽或高为 0）：内容本来就不存在。压一个空层，
            // 让 endClip 保持配对，同时不往原版剪刀栈里塞任何东西。
            CLIP_STACK.push(new Clip(ScreenRectangle.empty(), false));
            return;
        }
        int screenWidth = Math.max(1, graphics.guiWidth());
        int screenHeight = Math.max(1, graphics.guiHeight());
        ScreenRectangle screen = new ScreenRectangle(0, 0, screenWidth, screenHeight);
        ScreenRectangle raw = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
        ScreenRectangle onScreen = raw.intersection(screen);
        ScreenRectangle glass = onScreen == null || isDegenerate(onScreen)
                ? ScreenRectangle.empty() : onScreen;

        // 开场/关闭动画会把整窗控件平移到屏幕外，裁剪框整体落出屏幕时高被夹成 0，
        // 此时绝不能调 enableScissor（渲染器会抛 "Scissor size must be >0"）。
        // 跳过是安全的：不可见区域里本来就画不出东西，父级剪刀框同样挡着它。
        boolean visible = onScreen != null && onScreen.width() > 0 && onScreen.height() > 0
                && !isDegenerate(glass);
        if (visible) {
            graphics.enableScissor(x0, y0, x1, y1);
            CLIP_STACK.push(new Clip(glass, true));
        } else {
            CLIP_STACK.push(new Clip(ScreenRectangle.empty(), false));
        }
    }

    private static boolean isDegenerate(ScreenRectangle rect) {
        return rect == null || rect.width() <= 0 || rect.height() <= 0;
    }

    /** 清空裁剪区。每帧开头调一次，防止某个控件漏掉 {@link #endClip} 之后一直影响后续绘制。 */
    public static void resetClip() {
        CLIP_STACK.clear();
    }

    /** 结束一段裁剪，与 {@link #beginClip} 成对使用。 */
    public static void endClip(GuiGraphics graphics) {
        Clip clip = CLIP_STACK.poll();
        // 只有真的往原版栈里压过，才需要弹一次；否则会破坏原版 scissor 栈的配对。
        if (clip != null && clip.scissorPushed()) {
            graphics.disableScissor();
        }
    }

    /**
     * 当前玻璃裁剪区；没有裁剪时为 {@code null}。
     *
     * <p>公开给 {@link GlassPanel}：立即渲染路径由原版 scissor 统一裁剪，
     * 玻璃面板画的时候只拿它做「完全在裁剪区外就跳过」的剔除。</p>
     */
    public static ScreenRectangle clipRectangle() {
        Clip clip = CLIP_STACK.peek();
        return clip == null ? null : clip.glass();
    }
}
