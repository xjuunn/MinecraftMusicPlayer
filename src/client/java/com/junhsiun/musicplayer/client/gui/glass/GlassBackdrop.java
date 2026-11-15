package com.junhsiun.musicplayer.client.gui.glass;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * 液态玻璃背后的模糊背景：把当前画面逐级降采样成多级（1.21.1 老 {@code RenderTarget} 体系）。
 *
 * <p>26.x 的 CommandEncoder/RenderPass 在 1.21.1 不存在，改用 {@link TextureTarget} 离屏目标 +
 * 自绘全屏三角形跑模糊着色器。每级用横向/纵向两次采样做可分离高斯模糊，链路与 26.x 一致。</p>
 *
 * <p>每帧必须在界面绘制<b>之前</b>调用一次 {@link #captureFrame()}，这样玻璃不会把菜单
 * 自己也采样进去。所有 GPU 资源都是懒创建、按窗口尺寸重建。</p>
 */
public final class GlassBackdrop {

    /** 清透层缩放倍数（1/2）。 */
    private static final int SOFT_DIVISOR = 2;
    /** 磨砂层缩放倍数（1/4）。 */
    private static final int FROST_DIVISOR = 4;
    /** 重度模糊层缩放倍数（1/8）。 */
    private static final int HEAVY_DIVISOR = 8;
    private static final float SOFT_SPREAD = 1.0F;
    private static final float FROST_SPREAD = 1.2F;
    private static final float HEAVY_SPREAD = 1.4F;
    private static final int SOFT_ITERATIONS = 1;
    private static final int FROST_ITERATIONS = 2;
    private static final int HEAVY_ITERATIONS = 3;

    private static final GlassBackdrop INSTANCE = new GlassBackdrop();

    private TextureTarget softA;
    private TextureTarget softB;
    private TextureTarget frostA;
    private TextureTarget frostB;
    private TextureTarget heavyA;
    private TextureTarget heavyB;
    private TextureTarget sharp;

    private int sceneWidth;
    private int sceneHeight;
    private boolean ready;
    private boolean failed;

    private GlassBackdrop() {
    }

    public static GlassBackdrop getInstance() {
        return INSTANCE;
    }

    /** 清透层（1/2）贴图的纹理 ID（gl 纹理对象），供玻璃着色器采样。 */
    public int softTextureId() {
        return softA == null ? -1 : softA.getColorTextureId();
    }

    public int frostTextureId() {
        return frostA == null ? -1 : frostA.getColorTextureId();
    }

    public int heavyTextureId() {
        return heavyA == null ? -1 : heavyA.getColorTextureId();
    }

    public int sharpTextureId() {
        return sharp == null ? -1 : sharp.getColorTextureId();
    }

    public int softWidth() {
        return softA == null ? 1 : softA.width;
    }

    public int softHeight() {
        return softA == null ? 1 : softA.height;
    }

    public int frostWidth() {
        return frostA == null ? 1 : frostA.width;
    }

    public int frostHeight() {
        return frostA == null ? 1 : frostA.height;
    }

    public int heavyWidth() {
        return heavyA == null ? 1 : heavyA.width;
    }

    public int heavyHeight() {
        return heavyA == null ? 1 : heavyA.height;
    }

    public int sharpWidth() {
        return sharp == null ? 1 : sharp.width;
    }

    public int sharpHeight() {
        return sharp == null ? 1 : sharp.height;
    }

    public boolean isReady() {
        return ready && !failed && frostA != null && heavyA != null && sharp != null;
    }

    /** 是否已经永久失败（不再重试）。失败时调用方应退回简易面板，而不是一直空等。 */
    public boolean hasFailed() {
        return failed;
    }

    /** 让缓存内容失效：贴图保留，但标记为「内容不可信」。 */
    public void invalidate() {
        ready = false;
    }

    /**
     * 只把离屏贴图准备好（尺寸变化时重建），<b>不提交任何 GPU 命令</b>。
     */
    public void prepare() {
        if (failed) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        try {
            ensureResources(main.width, main.height);
        } catch (Throwable throwable) {
            failed = true;
            ready = false;
            MusicPlayerMod.LOGGER.warn("液态玻璃背景贴图创建失败，改用简易面板", throwable);
        }
    }

    /**
     * 抓取当前画面并重新计算各级模糊。必须在当前帧的界面绘制之前调用一次。
     */
    public void captureFrame() {
        if (failed) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        try {
            ensureResources(main.width, main.height);
            // 原图 1:1 拷贝（模糊度为 0 时用）
            copyTo(sharp, main);
            // 降采样到 1/2
            copyTo(softA, main);
            blur(softA, softB, SOFT_SPREAD, SOFT_ITERATIONS);
            // 继续降采样到 1/4，再模糊
            copyTo(frostA, softA);
            blur(frostA, frostB, FROST_SPREAD, FROST_ITERATIONS);
            // 再降到 1/8 并重度模糊
            copyTo(heavyA, frostA);
            blur(heavyA, heavyB, HEAVY_SPREAD, HEAVY_ITERATIONS);
            ready = true;
        } catch (Throwable throwable) {
            failed = true;
            ready = false;
            MusicPlayerMod.LOGGER.warn("液态玻璃背景模糊初始化失败，改用简易面板", throwable);
        }
    }

    /** 释放全部 GPU 资源，下次绘制时重建。 */
    public void reset() {
        closeTargets();
        sceneWidth = 0;
        sceneHeight = 0;
        ready = false;
        failed = false;
    }

    private void closeTargets() {
        for (TextureTarget target : targets()) {
            if (target != null) {
                target.destroyBuffers();
            }
        }
        softA = null;
        softB = null;
        frostA = null;
        frostB = null;
        heavyA = null;
        heavyB = null;
        sharp = null;
    }

    private void ensureResources(int width, int height) {
        if (heavyA != null && sceneWidth == width && sceneHeight == height) {
            return;
        }
        closeTargets();
        RenderSystem.assertOnRenderThreadOrInit();
        softA = createTarget(width / SOFT_DIVISOR, height / SOFT_DIVISOR);
        softB = createTarget(width / SOFT_DIVISOR, height / SOFT_DIVISOR);
        frostA = createTarget(width / FROST_DIVISOR, height / FROST_DIVISOR);
        frostB = createTarget(width / FROST_DIVISOR, height / FROST_DIVISOR);
        heavyA = createTarget(width / HEAVY_DIVISOR, height / HEAVY_DIVISOR);
        heavyB = createTarget(width / HEAVY_DIVISOR, height / HEAVY_DIVISOR);
        sharp = createTarget(width, height);
        sceneWidth = width;
        sceneHeight = height;
        ready = false;
    }

    private static TextureTarget createTarget(int width, int height) {
        TextureTarget target = new TextureTarget(Math.max(1, width), Math.max(1, height), true, true);
        // RenderTarget 默认最近邻过滤，降采样必须用双线性才平滑
        target.setFilterMode(com.mojang.blaze3d.platform.GlConst.GL_LINEAR);
        return target;
    }

    private TextureTarget[] targets() {
        return new TextureTarget[]{softA, softB, frostA, frostB, heavyA, heavyB, sharp};
    }

    /** 把 source 的颜色附件整个画进 destination（一次双线性采样，等价降采样）。 */
    private void copyTo(TextureTarget destination, RenderTarget source) {
        drawFullscreen(destination, source, null, 0.0F, false);
    }

    /** 可分离高斯：a → b（横向）→ a（纵向），结果留在 a。 */
    private void blur(TextureTarget a, TextureTarget b, float spread, int iterations) {
        for (int i = 0; i < iterations; i++) {
            drawFullscreen(b, a, null, spread, false);
            drawFullscreen(a, b, null, spread, true);
        }
    }

    /**
     * 用玻璃模糊着色器把 source 画满 destination。
     *
     * @param spread   高斯扩散（texel），0 表示纯采样
     * @param vertical {@code true} 纵向模糊
     */
    private void drawFullscreen(TextureTarget destination, RenderTarget source,
                                Object unused, float spread, boolean vertical) {
        Minecraft minecraft = Minecraft.getInstance();
        com.mojang.blaze3d.vertex.VertexBuffer quad = GlassBlurQuads.get();
        net.minecraft.client.renderer.ShaderInstance shader = GlassPipelines.glassBlur();
        if (shader == null || destination == null || source == null || quad == null) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        destination.clear(true);
        drawInto(destination, source, spread, vertical, quad, shader);
        minecraft.getMainRenderTarget().bindWrite(true);
        RenderSystem.viewport(0, 0, minecraft.getMainRenderTarget().width,
                minecraft.getMainRenderTarget().height);
        // 深度测试在 GuiGraphics.flush() 之后是开着的：这里只做「离屏拷贝」，
        // 不参与深度比较也不写深度，按 GUI 的语义关掉。不关的话后面的玻璃 quad
        // 会被上一次世界的深度值裁掉一大块。
        RenderSystem.disableDepthTest();
    }

    private void drawInto(RenderTarget destination, RenderTarget source, float spread, boolean vertical,
                          com.mojang.blaze3d.vertex.VertexBuffer quad,
                          net.minecraft.client.renderer.ShaderInstance shader) {
        destination.bindWrite(true);
        RenderSystem.viewport(0, 0, destination.width, destination.height);
        shader.safeGetUniform("SamplerSize").set((float) source.width, (float) source.height);
        shader.safeGetUniform("SpreadVertical").set(spread, vertical ? 1.0F : 0.0F);
        // drawWithShader 里会按 shaderTextures 重写 Sampler0，必须走 RenderSystem
        int saved0 = RenderSystem.getShaderTexture(0);
        RenderSystem.setShaderTexture(0, source.getColorTextureId());
        Matrix4f identity = new Matrix4f();
        // drawWithShader 只负责 apply() + draw()，不会绑定 VAO。
        // 共享 quad 在创建时已经 unbind 过，这里必须重新绑定，否则
        // glDrawElements 在没有活跃 VAO 时被调用（GL_INVALID_OPERATION），
        // 目标贴图只会留下 clear 之后的黑色 —— 玻璃采样出来整屏发黑。
        quad.bind();
        quad.drawWithShader(identity, identity, shader);
        com.mojang.blaze3d.vertex.VertexBuffer.unbind();
        RenderSystem.setShaderTexture(0, saved0);
    }
}
