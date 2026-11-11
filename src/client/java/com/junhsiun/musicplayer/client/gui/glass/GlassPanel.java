package com.junhsiun.musicplayer.client.gui.glass;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPassLayouts;
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

    /**
     * 控件用的固定材质。
     *
     * <p>它们全部在 {@link #prewarm(GlassStyle)} 里一次性编译：这些材质的参数不会变，
     * 每份只有两套（窗口背景 / 子控件背景），一次编译就能一直复用。</p>
     */
    private static final GlassStyle[] CONTROL_STYLES = {
            GlassTheme.BUTTON, GlassTheme.FIELD, GlassTheme.TOGGLE, GlassTheme.KNOB,
            GlassTheme.CARD, GlassTheme.TRACK, GlassTheme.PILL, GlassTheme.ROW, GlassTheme.BADGE
    };

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
     * @return 是否真的提交了玻璃；{@code false} 表示这一帧背景还没就绪（首帧）
     */
    public static boolean draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                               GlassStyle style) {
        return draw(graphics, x0, y0, x1, y1, style, true);
    }

    /** 当前这一帧的外观（模糊度），由界面在提取阶段设置。 */
    private static GlassLook look = new GlassLook(1.0F);

    /**
     * 子控件用的外观：在 {@link #look} 的基础上带上窗口叠加层的浓度。
     *
     * <p>按钮、滑块、开关都在窗口玻璃之内，它们的底色应该是"世界背景 + 窗口叠加"，
     * 否则会直接显示裸的世界颜色，看起来像浮在窗口外面。窗口自身仍用 {@link #look}。</p>
     */
    private static GlassLook childLook = new GlassLook(1.0F);

    public static void setLook(GlassLook value) {
        look = value == null ? new GlassLook(1.0F) : value;
        // 子控件沿用同一档模糊度，外加窗口叠加层的浓度
        childLook = new GlassLook(look.blur(), GlassTheme.overlayAlpha());
    }

    public static GlassLook look() {
        return look;
    }

    /** 子控件（按钮 / 滑块 / 开关）的外观：模糊度同 {@link #look}，另含窗口叠加层浓度。 */
    public static GlassLook childLook() {
        return childLook;
    }

    /**
     * 提前把玻璃要用的一切重资源准备好：编译全部管线、创建离屏贴图。
     *
     * <p><b>这个方法不会提交任何 GPU 命令。</b>它只做两件"一次性、可重复调用"的事：
     * 编译管线（CPU 侧）、按当前分辨率分配离屏贴图（显存分配）。第一份背景模糊留给
     * {@link #beforeGui()} 在帧内抓——原因见下。</p>
     *
     * <p>曾经这里会顺手 {@code captureFrame()}，想的是"界面第一帧就有内容可画"。但那会在
     * <b>客户端 tick 阶段</b>提交一条命令缓冲：此时上一帧刚呈现、本帧还没开始，驱动会把这次
     * 提交当成一次不完整的呈现，于是打开窗口时闪一帧黑。管线编译与贴图分配放在 tick 阶段是
     * 安全的（没有提交），所以两者要拆开。</p>
     *
     * <p>首帧背景没就绪时 {@link #draw} 会直接跳过绘制，窗口等下一帧再出现；
     * 开场动画本来就是从屏幕外升进来，少画一帧玻璃看不出来。</p>
     *
     * <p>失败时退回简易面板，不影响界面可用性。</p>
     */
    public static void prewarm(GlassStyle style) {
        GlassBackdrop backdrop = GlassBackdrop.getInstance();
        // 先把外观同步成配置里的值：管线是按 look 编译的。若这里用默认 look 编译、
        // 界面里再 setLook 成配置值，等于首帧又要重新编译一遍（正是要避免的那件事）。
        setLook(GlassTheme.look());
        // 先标记内容不可信：抓好之后会重新置为可用；万一这一帧抓取抛错，也不会拿旧内容去画
        backdrop.invalidate();
        if (unavailable) {
            return;
        }
        try {
            RenderSystem.getCompiledPipeline(GlassPipelines.panel(style, look), RenderPassLayouts.COLOR_ONLY.subpass(0));
            // 子控件用的是带窗口叠加的变体，一并编译，避免首次画按钮时才编译
            RenderSystem.getCompiledPipeline(GlassPipelines.panel(style, childLook), RenderPassLayouts.COLOR_ONLY.subpass(0));
            // 控件材质一次编译完：它们都是固定风格，一次性把 GPU 程序编译掉，
            // 免得界面首帧在渲染中途才开始编译（那会打断当前帧）
            for (GlassStyle control : CONTROL_STYLES) {
                RenderSystem.getCompiledPipeline(GlassPipelines.panel(control, look), RenderPassLayouts.COLOR_ONLY.subpass(0));
                RenderSystem.getCompiledPipeline(GlassPipelines.panel(control, childLook), RenderPassLayouts.COLOR_ONLY.subpass(0));
            }
            prepared = true;
            compileShadowPipeline(style);
        } catch (Throwable throwable) {
            unavailable = true;
            MusicPlayerMod.LOGGER.warn("液态玻璃着色器编译失败，改用简易面板", throwable);
            return;
        }
        // 只分配离屏贴图，不提交命令；第一份模糊由本帧的 beforeGui() 完成
        backdrop.prepare();
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
                RenderSystem.getCompiledPipeline(GlassPipelines.panel(style, look), RenderPassLayouts.COLOR_ONLY.subpass(0));
                RenderSystem.getCompiledPipeline(GlassPipelines.panel(style, childLook), RenderPassLayouts.COLOR_ONLY.subpass(0));
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
            RenderSystem.getCompiledPipeline(GlassPipelines.shadow(style), RenderPassLayouts.COLOR_ONLY.subpass(0));
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
     * @return 是否真的提交了玻璃
     */
    public static boolean draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                               GlassStyle style, boolean withShadow) {
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        GlassBackdrop backdrop = GlassBackdrop.getInstance();
        if (unavailable || !prepared || backdrop.hasFailed()) {
            // 着色器或背景永久不可用：退化成参考实现的 fallback 填充色，保证界面依旧可操作
            graphics.fill(x0, y0, x1, y1, style.fallback());
            return false;
        }
        if (!backdrop.isReady() || backdrop.softView() == null || backdrop.frostView() == null
                || backdrop.heavyView() == null) {
            // 只是背景贴图这一帧还没抓好（界面刚打开的第一帧）：直接跳过，什么都不画。
            // 这里绝对不能填 fallback——那是一块 88% 不透明的深色，正好盖住大半个屏幕，
            // 就是"打开窗口瞬间闪一下"的来源。少画一帧玻璃肉眼看不出，画深色块却很显眼。
            return false;
        }

        GlassShadow shadow = style.shadow();
        // 阴影开关关闭时整块投影都不提交
        if (withShadow && shadow.visible() && GlassTheme.shadowEnabled()
                && style.pipelineKey().equals(compiledShadowKey)) {
            int padding = shadow.padding();
            // 投影不读调参通道，给个不透明的白色即可
            submit(graphics, GlassPipelines.shadow(style), TextureSetup.noTexture(),
                    x0 - padding, y0 - padding, x1 + padding, y1 + padding, 0xFFFFFFFF);
        }

        TextureSetup setup = tripleTexture(backdrop);
        submit(graphics, GlassPipelines.panel(style, look), setup, x0, y0, x1, y1,
                GlassDebug.encode(style));
        return true;
    }

    /**
     * 三张背景层打包成 TextureSetup，供玻璃着色器按模糊度混合。
     *
     * <p>模糊度为 0 时把 Sampler0 换成原图（1:1 拷贝），这样"关掉模糊"是真的逐像素还原，
     * 而不是停在 1/2 降采样的柔化上；有模糊时才用 1/2 的清透层。</p>
     */
    private static TextureSetup tripleTexture(GlassBackdrop backdrop) {
        GpuSampler sampler = backdrop.sampler();
        GpuTextureView base = look.quantizedBlur() <= 0.0F
                ? backdrop.sharpView() : backdrop.softView();
        return new TextureSetup(base, backdrop.frostView(), backdrop.heavyView(),
                sampler, sampler, sampler);
    }

    /**
     * 画一块玻璃控件（无投影），边缘折射与色散可逐帧覆盖，其余参数取自材质。
     *
     * <p>这些参数走顶点色、不进管线定义，所以 hover / 按下的连续变化不会触发重编译。</p>
     *
     * @return 是否画成功；背景未就绪或着色器不可用时返回 {@code false}
     */
    public static boolean drawTuned(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                    GlassStyle style, float refraction, float dispersion,
                                    float centerDispersion) {
        return drawTuned(graphics, x0, y0, x1, y1, style, refraction, dispersion, centerDispersion,
                GlassDebug.aDistance(style),
                GlassDebug.contraRatio(style), GlassDebug.shrink(style));
    }

    /**
     * 画一块玻璃控件（无投影），六项光学参数均可逐帧覆盖。
     *
     * @return 是否画成功；背景未就绪或着色器不可用时返回 {@code false}
     */
    public static boolean drawTuned(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                    GlassStyle style, float refraction, float dispersion,
                                    float centerDispersion, float aDistance,
                                    float contraRatio, float shrink) {
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        GlassBackdrop backdrop = GlassBackdrop.getInstance();
        if (unavailable || !prepared || backdrop.hasFailed()
                || !backdrop.isReady() || backdrop.softView() == null || backdrop.frostView() == null
                || backdrop.heavyView() == null) {
            return false;
        }
        submit(graphics, GlassPipelines.panel(style, childLook), tripleTexture(backdrop), x0, y0, x1, y1,
                GlassDebug.pack(refraction, dispersion, centerDispersion,
                        aDistance, contraRatio, shrink));
        return true;
    }

    /**
     * 投影管线就绪时在面板下方提交投影四边形。
     *
     * @param color 顶点色。面板用它承载折射/色散等四项调参，编码见
     *              {@link GlassDebug#encode(GlassStyle)}
     */
    private static void submit(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
                               TextureSetup setup, int x0, int y0, int x1, int y1, int color) {
        // 带上当前裁剪区：玻璃面板是手写 BlitRenderState 提交的，原版 enableScissor 管不到，
        // 不传的话滚动区里的面板会画到列表外面去（行、徽标、播放按钮都会漏出来）
        ScreenRectangle clip = GlassWidgets.clipRectangle();
        if (clip != null && (clip.width() <= 0 || clip.height() <= 0)) {
            // 裁剪区退化（宽或高为 0）：scissorArea 为 0 会让渲染器 enableScissor 抛异常崩溃，
            // 这种区域本来就不可见，直接不提交。
            return;
        }
        if (clip != null
                && (x1 <= clip.left() || x0 >= clip.right() || y1 <= clip.top() || y0 >= clip.bottom())) {
            return;
        }
        graphics.guiRenderState.addGuiElement(new BlitRenderState(
                GlassPipelines.gui(pipeline),
                setup,
                new Matrix3x2f(graphics.pose()),
                x0, y0, x1, y1,
                0.0F, 1.0F,
                0.0F, 1.0F,
                color,
                clip));
    }
}
