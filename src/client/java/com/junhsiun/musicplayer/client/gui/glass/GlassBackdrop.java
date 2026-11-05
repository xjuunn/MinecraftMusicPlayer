package com.junhsiun.musicplayer.client.gui.glass;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPassLayouts;
import net.minecraft.util.Util;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * 液态玻璃背后的模糊背景：把当前画面逐级降采样成两级，供玻璃着色器按比例混合。
 *
 * <pre>
 * 当前画面 ──bilerp──→ softA(1/2) ⇄ softB    1 次可分离模糊, spread 1.0   ≈ 4.6px
 *                        │
 *                        └─bilerp──→ frostA(1/4) ⇄ frostB   2 次, spread 1.2  ≈ 15.7px
 * </pre>
 *
 * <p>逐级 2× 降采样用双线性过滤得到 2×2 平均，画面移动时不会闪烁；<b>soft 采样 softA、
 * frost 采样 frostA</b>。两级都停在较高分辨率上，是为了让模糊只做柔化、不抹掉结构——
 * iOS Liquid Glass 的底色始终能看清背后是什么，一旦出现大片平滑色块就不再像玻璃了。</p>
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
    /** 清透层高斯扩散（texel）。 */
    private static final float SOFT_SPREAD = 1.0F;
    /**
     * 磨砂层高斯扩散（texel）。
     *
     * <p>参考实现用的是 1/8 分辨率 + spread 1.6 + 3 次迭代，等效模糊半径约 51px；
     * 1/8 分辨率意味着每个像素对应屏幕 8×8 的方块，无论怎么过滤都只剩大色块。
     * 这里收敛到 1/4 分辨率 + 1.2 + 2 次（约 15.7px），保留磨砂感的同时不丢结构。</p>
     */
    private static final float FROST_SPREAD = 1.2F;
    /** 重度模糊层扩散（texel），配合 1/8 分辨率得到大半径柔化。 */
    private static final float HEAVY_SPREAD = 1.4F;
    /** 清透层迭代次数。 */
    private static final int SOFT_ITERATIONS = 1;
    /** 磨砂层迭代次数，取值理由见 {@link #FROST_SPREAD}。 */
    private static final int FROST_ITERATIONS = 2;
    /** 重度模糊层迭代次数。 */
    private static final int HEAVY_ITERATIONS = 3;

    private static final GlassBackdrop INSTANCE = new GlassBackdrop();

    private Target softA;
    private Target softB;
    private Target frostA;
    private Target frostB;
    private Target heavyA;
    private Target heavyB;
    /** 原始分辨率拷贝，模糊度为 0 时使用。 */
    private Target sharp;

    private GpuBuffer quadBuffer;
    private int sceneWidth;
    private int sceneHeight;
    private boolean ready;
    private boolean failed;

    private GlassBackdrop() {
    }

    public static GlassBackdrop getInstance() {
        return INSTANCE;
    }

    /** 清透层（1/2）贴图，供玻璃着色器采样。 */
    public GpuTextureView softView() {
        return softA == null ? null : softA.view;
    }

    /** 磨砂层（1/4）贴图，供玻璃着色器采样。 */
    public GpuTextureView frostView() {
        return frostA == null ? null : frostA.view;
    }

    /** 重度模糊层（1/8）贴图，模糊度拉满时才真正起作用。 */
    public GpuTextureView heavyView() {
        return heavyA == null ? null : heavyA.view;
    }

    /** 原始分辨率拷贝，模糊度为 0 时使用。 */
    public GpuTextureView sharpView() {
        return sharp == null ? null : sharp.view;
    }

    public GpuSampler sampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public boolean isReady() {
        return ready && !failed && frostA != null && heavyA != null && sharp != null;
    }

    /** 是否已经永久失败（不再重试）。失败时调用方应退回简易面板，而不是一直空等。 */
    public boolean hasFailed() {
        return failed;
    }

    /**
     * 让缓存内容失效：贴图保留，但标记为"内容不可信"。
     *
     * <p>下一帧 {@code draw()} 会因此跳过绘制，等本轮 {@code beforeGui()} 重新抓好再恢复。</p>
     *
     * <p>为什么需要它：{@link #isReady()} 原本只表示"资源已创建"，但界面关闭后抓取就停了，
     * 期间贴图内容要么是关闭前的旧画面、要么在尺寸变化重建后是未初始化的（读出来全 0，即黑色）。
     * 重新打开界面的第一帧如果照常绘制，就会闪一帧黑。界面初始化、贴图重建都必须调用这里，
     * 让"资源存在"和"内容可信"两个状态分离。</p>
     */
    public void invalidate() {
        ready = false;
    }

    /**
     * 只把离屏贴图准备好（尺寸变化时重建），<b>不提交任何 GPU 命令</b>。
     *
     * <p>给 {@link GlassPanel#prewarm(GlassStyle)} 用：打开界面之前先把显存分配做掉，
     * 免得界面首帧在渲染中途去建 8 张全屏级别的贴图。但<b>绝对不能</b>在这里顺手抓取——
     * 抓取要 {@code createCommandEncoder()} + {@code submit()}，而"打开界面"发生在客户端 tick
     * 阶段，那时上一帧刚提交完、本帧的命令还没开始，插一条提交进去会让驱动把当前帧当成
     * 一次不完整的呈现，表现就是打开窗口时闪一帧黑。</p>
     *
     * <p>真正的抓取只允许出现在 {@link #captureFrame()}，由 {@code GameRendererMixin} 挂在
     * {@code GuiRenderer.render()} 之前调用——那里世界已经画好、界面还没画，且处在帧内。</p>
     */
    public void prepare() {
        if (failed) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
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
     * 抓取当前画面并重新计算两级模糊。必须在当前帧的界面绘制之前调用一次。
     */
    public void captureFrame() {
        if (failed) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        try {
            ensureResources(main.width, main.height);
            GpuSampler linear = sampler();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            // 原图逐像素拷贝（1:1），模糊度为 0 时用它，保证"完全没有模糊"
            downsample(encoder, sharp, main.getColorTextureView(), linear);
            // 降采样到 1/2，双线性即 2×2 平均
            downsample(encoder, softA, main.getColorTextureView(), linear);
            // 清透层：1 次可分离模糊，结果留在 softA
            blur(encoder, softA, softB, SOFT_SPREAD, SOFT_ITERATIONS, linear);
            // 继续降采样到 1/4，再模糊，结果留在 frostA
            downsample(encoder, frostA, softA.view, linear);
            blur(encoder, frostA, frostB, FROST_SPREAD, FROST_ITERATIONS, linear);
            // 再降到 1/8 并重度模糊，供模糊度滑块的高端使用
            downsample(encoder, heavyA, frostA.view, linear);
            blur(encoder, heavyA, heavyB, HEAVY_SPREAD, HEAVY_ITERATIONS, linear);

            encoder.submit();
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
        if (quadBuffer != null) {
            quadBuffer.close();
            quadBuffer = null;
        }
        sceneWidth = 0;
        sceneHeight = 0;
        ready = false;
        failed = false;
    }

    private void closeTargets() {
        for (Target target : targets()) {
            if (target != null) {
                target.close();
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
        if (quadBuffer == null) {
            quadBuffer = createQuadBuffer(RenderSystem.getDevice());
        }
        if (heavyA != null && sceneWidth == width && sceneHeight == height) {
            return;
        }
        closeTargets();
        softA = new Target(width / SOFT_DIVISOR, height / SOFT_DIVISOR, "soft a");
        softB = new Target(width / SOFT_DIVISOR, height / SOFT_DIVISOR, "soft b");
        frostA = new Target(width / FROST_DIVISOR, height / FROST_DIVISOR, "frost a");
        frostB = new Target(width / FROST_DIVISOR, height / FROST_DIVISOR, "frost b");
        heavyA = new Target(width / HEAVY_DIVISOR, height / HEAVY_DIVISOR, "heavy a");
        heavyB = new Target(width / HEAVY_DIVISOR, height / HEAVY_DIVISOR, "heavy b");
        sharp = new Target(width, height, "sharp");
        sceneWidth = width;
        sceneHeight = height;
        // 新建的贴图是未初始化的，内容不可信，必须等本轮真正抓好再允许绘制。
        // 不置回 false 的话 isReady() 会继续返回 true，draw() 就会拿一张空白贴图去画，
        // 读出来是 RGBA 全 0，也就是一帧纯黑。
        ready = false;
    }

    /** 逐级降采样：用 spread = 0 复用模糊着色器，权重和为 1，等价于一次双线性采样。 */
    private void downsample(CommandEncoder encoder, Target destination, GpuTextureView source,
                            GpuSampler linear) {
        pass(encoder, destination, source, 0.0F, false, linear);
    }

    /** 可分离高斯：a → b（横向）→ a（纵向），结果留在 a，与参考实现一致。 */
    private void blur(CommandEncoder encoder, Target a, Target b, float spread, int iterations,
                      GpuSampler linear) {
        for (int i = 0; i < iterations; i++) {
            pass(encoder, b, a.view, spread, false, linear);
            pass(encoder, a, b.view, spread, true, linear);
        }
    }

    private void pass(CommandEncoder encoder, Target destination, GpuTextureView source,
                      float spread, boolean vertical, GpuSampler linear) {
        RenderPass pass = encoder.createRenderPass(
                () -> "musicplayer glass blur " + destination.name, RenderPassLayouts.COLOR_ONLY,
                destination.view, Optional.empty());
        try {
            pass.setPipeline(RenderSystem.getCompiledPipeline(GlassPipelines.blur(spread, vertical),
                    pass.activeSubpass()));
            pass.setUniform("Sampler0", source, linear);
            pass.setVertexBuffer(0, quadBuffer.slice());
            pass.draw(6, 1, 0, 0);
        } finally {
            pass.close();
        }
    }

    private Target[] targets() {
        return new Target[]{softA, softB, frostA, frostB, heavyA, heavyB, sharp};
    }

    private static GpuBuffer createQuadBuffer(GpuDevice device) {
        // 两个三角形覆盖整个屏幕，位置直接是 NDC（glass_blur.vsh 不做投影）
        float[][] vertices = {
                {-1.0F, -1.0F, 0.0F, 0.0F, 0.0F},
                {1.0F, -1.0F, 0.0F, 1.0F, 0.0F},
                {1.0F, 1.0F, 0.0F, 1.0F, 1.0F},
                {-1.0F, -1.0F, 0.0F, 0.0F, 0.0F},
                {1.0F, 1.0F, 0.0F, 1.0F, 1.0F},
                {-1.0F, 1.0F, 0.0F, 0.0F, 1.0F}
        };
        ByteBuffer buffer = BufferUtils.createByteBuffer(vertices.length * 5 * Float.BYTES);
        for (float[] vertex : vertices) {
            for (float value : vertex) {
                buffer.putFloat(value);
            }
        }
        buffer.flip();
        return device.createBuffer(() -> "musicplayer glass quad",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, buffer);
    }

    /** 一张离屏颜色目标：线性过滤 + 边缘钳制（RenderTarget 默认 NEAREST，不适合降采样）。 */
    private static final class Target {
        private final GpuTexture texture;
        private final GpuTextureView view;
        private final String name;

        private Target(int width, int height, String name) {
            this.name = name;
            GpuDevice device = RenderSystem.getDevice();
            this.texture = device.createTexture(
                    () -> "musicplayer glass " + name,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT
                            | GpuTexture.USAGE_COPY_DST,
                    GpuFormat.RGBA8_UNORM,
                    Math.max(1, width),
                    Math.max(1, height),
                    1,
                    1);
            this.view = device.createTextureView(texture);
        }

        private void close() {
            // view 是独立的 AutoCloseable，只关 texture 会泄漏视图对象
            if (!view.isClosed()) {
                view.close();
            }
            if (!texture.isClosed()) {
                texture.close();
            }
        }
    }
}
