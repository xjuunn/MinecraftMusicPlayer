package com.junhsiun.musicplayer.client.gui.glass;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import org.joml.Matrix3x2f;

/**
 * 把一块液态玻璃面板提交到当前这一帧的界面渲染状态里。
 *
 * <p>26.x 的界面是"先收集、后绘制"：界面代码只往 {@code GuiRenderState} 里塞元素，
 * 真正提交到 GPU 由 {@code GuiRenderer} 在稍后统一完成。所以这里只负责构造
 * {@link BlitRenderState}，几何上永远是一个矩形四边形，圆角、折射、高光、投影全部在
 * {@code glass.fsh} 里按 SDF 计算。</p>
 *
 * <p>注意管线必须声明 {@link com.mojang.renderpearl.api.pipeline.PrimitiveTopology#QUADS}：
 * {@code BlitRenderState.buildVertices} 只写 4 个顶点，声明成 {@code TRIANGLES} 只会画出
 * 索引 0,1,2 对应的那一个三角形。</p>
 *
 * <p>折射、斜面、色散、中心色散四项调参不走 shader define，而是写进顶点色逐 draw 传入
 * （编码见 {@link GlassDebug#encode(GlassStyle)}）。原因是界面渲染路径拿不到 {@code RenderPass}，
 * 无法绑定逐 draw 的 uniform；而改 define 等于重新编译管线，没法连续调试。</p>
 */
public final class GlassPanel {

    /** 着色器已确认可用。 */
    private static boolean prepared;
    /** 着色器或贴图彻底不可用，本进程内不再尝试。 */
    private static boolean unavailable;
    /** 投影管线已为其编译成功的材质键，避免每帧重复比对。 */
    private static String compiledShadowKey;
    /** 投影管线编译失败的材质键，本进程内不再重试，避免每帧警告刷屏。 */
    private static String failedShadowKey;
    /** 本帧是否有界面请求了玻璃（由提取阶段登记，绘制前消费）。 */
    private static boolean requested;

    private GlassPanel() {
    }

    /**
     * 画一块玻璃面板。
     *
     * @param graphics 当前界面提取器
     * @param x0       左上角 x（GUI 像素）
     * @param y0       左上角 y
     * @param x1       右下角 x
     * @param y1       右下角 y
     * @param style    材质参数
     */
    public static void draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, GlassStyle style) {
        draw(graphics, x0, y0, x1, y1, style, true);
    }

    /**
     * 提前把玻璃要用的一切重资源准备好：编译全部管线、创建离屏贴图并抓好第一份背景。
     *
     * <p><b>必须在一帧的渲染流程之外调用</b>（例如打开界面前的交互回调 / 客户端 tick）。
     * 管线编译和纹理创建是耗时的 GPU 操作，如果放在界面那一帧的提取或绘制阶段里做，
     * 会打断正在进行的帧，驱动会先送出一帧黑屏，然后才出现界面。挪到渲染循环之外，
     * 它后面那一帧就是完整的正常帧。</p>
     *
     * <p>失败时退回简易面板，不影响界面可用性。</p>
     */
    public static void prewarm(GlassStyle style) {
        GlassBackdrop backdrop = GlassBackdrop.getInstance();
        // 先标记内容不可信：抓取成功后会重新置为可用；万一抓取抛错，也不会拿旧内容去画
        backdrop.invalidate();
        if (unavailable) {
            return;
        }
        try {
            RenderSystem.getCompiledPipeline(GlassPipelines.panel(style));
            prepared = true;
            compileShadowPipeline(style);
        } catch (Throwable throwable) {
            unavailable = true;
            MusicPlayerMod.LOGGER.warn("液态玻璃着色器编译失败，改用简易面板", throwable);
            return;
        }
        // 建好 4 张离屏贴图并算好第一份模糊，界面第一帧直接就有内容可用
        backdrop.captureFrame();
    }

    /**
     * 界面提取阶段（{@code Screen.extractRenderState}）调用一次：确认着色器能编译，并登记
     * "本帧要画玻璃"。
     *
     * <p>着色器是在管线第一次被使用时才编译的，而那时已经处在 {@code GuiRenderer} 的绘制流程里，
     * 出错会直接把游戏带崩。所以这里提前编译一次做校验，失败就永久退回简易面板。</p>
     *
     * <p>背景抓取<b>不在</b>这里做：界面提取发生在世界渲染之前，此时主渲染目标里还是上一帧
     * （包含上一帧的玻璃面板自身）。真正的抓取在 {@link #beforeGui()}，由
     * {@code GameRendererMixin} 挂在 {@code GuiRenderer.render()} 之前。</p>
     *
     * @param style 即将绘制的材质
     */
    public static void beginFrame(GlassStyle style) {
        requested = true;
        if (unavailable) {
            return;
        }
        if (!prepared) {
            try {
                RenderSystem.getCompiledPipeline(GlassPipelines.panel(style));
                prepared = true;
            } catch (Throwable throwable) {
                unavailable = true;
                MusicPlayerMod.LOGGER.warn("液态玻璃着色器编译失败，改用简易面板", throwable);
                return;
            }
        }

        // 注意：这里不能顺手调 captureFrame() 预热。抓取会 createCommandEncoder() + submit()，
        // 而本方法运行在提取阶段（世界都还没渲染完），此时提交 GPU 命令会打断当前帧，表现为闪屏。
        // 第一帧贴图没就绪的问题改由 draw() 直接跳过绘制来解决。
        if (style.shadow().visible() && !style.pipelineKey().equals(compiledShadowKey)
                && !style.pipelineKey().equals(failedShadowKey)) {
            compileShadowPipeline(style);
        }
    }

    /**
     * 编译投影管线并记录成败。成功与失败分开记，画的时候只信成功的那份；
     * 失败后不再重试，避免每帧警告刷屏。
     */
    private static void compileShadowPipeline(GlassStyle style) {
        String key = style.pipelineKey();
        try {
            RenderSystem.getCompiledPipeline(GlassPipelines.shadow(style));
            compiledShadowKey = key;
        } catch (Throwable throwable) {
            // 投影失败不至于让整个界面不可用，退化成不带投影的面板
            MusicPlayerMod.LOGGER.warn("液态玻璃投影着色器编译失败，本次不绘制投影", throwable);
            failedShadowKey = key;
        }
    }

    /**
     * 在 {@code GuiRenderer.render()} 之前由 {@code GameRendererMixin} 调用：此时主渲染目标里是
     * 本帧已经画好的世界、且界面尚未绘制，正是抓取背景的唯一正确时机。
     */
    public static void beforeGui() {
        if (!requested) {
            return;
        }
        requested = false;
        GlassBackdrop.getInstance().captureFrame();
    }

    /**
     * 画一块玻璃面板。
     *
     * @param withShadow 是否绘制底部投影
     */
    public static void draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                            GlassStyle style, boolean withShadow) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        GlassBackdrop backdrop = GlassBackdrop.getInstance();
        if (unavailable || !prepared || backdrop.hasFailed()) {
            // 着色器或背景永久不可用：退化成参考实现的 fallback 填充色，保证界面依旧可操作
            graphics.fill(x0, y0, x1, y1, style.fallback());
            return;
        }
        if (!backdrop.isReady() || backdrop.softView() == null || backdrop.frostView() == null) {
            // 只是背景贴图这一帧还没抓好（界面刚打开的第一帧）：直接跳过，什么都不画。
            // 这里绝对不能填 fallback——那是一块 88% 不透明的深色，正好盖住大半个屏幕，
            // 就是"打开窗口瞬间闪一下"的来源。少画一帧玻璃肉眼看不出，画深色块却很显眼。
            return;
        }

        GlassShadow shadow = style.shadow();
        if (withShadow && shadow.visible() && style.pipelineKey().equals(compiledShadowKey)) {
            int padding = shadow.padding();
            // 投影不读调参通道，给个不透明的白色即可
            submit(graphics, GlassPipelines.shadow(style), TextureSetup.noTexture(),
                    x0 - padding, y0 - padding, x1 + padding, y1 + padding, 0xFFFFFFFF);
        }

        TextureSetup setup = TextureSetup.doubleTexture(
                backdrop.softView(), backdrop.sampler(),
                backdrop.frostView(), backdrop.sampler());
        submit(graphics, GlassPipelines.panel(style), setup, x0, y0, x1, y1,
                GlassDebug.encode(style));
    }

    /**
     * 投影管线就绪时在面板下方提交投影四边形。
     *
     * @param color 顶点色。面板用它承载折射/色散等四项调参，编码见
     *              {@link GlassDebug#encode(GlassStyle)}
     */
    private static void submit(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
                               TextureSetup setup, int x0, int y0, int x1, int y1, int color) {
        graphics.guiRenderState.addGuiElement(new BlitRenderState(
                pipeline,
                setup,
                new Matrix3x2f(graphics.pose()),
                x0, y0, x1, y1,
                0.0F, 1.0F,
                0.0F, 1.0F,
                color,
                null,
                new ScreenRectangle(x0, y0, x1 - x0, y1 - y0)));
    }
}
