package com.junhsiun.musicplayer.client.gui.glass;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.vertex.VertexFormat.Mode;

/**
 * 把一块液态玻璃面板画进当前界面（1.21.1 老 {@code ShaderInstance} 体系）。
 *
 * <p>26.x 的「先收集后绘制」在 1.21.1 不存在，{@code GuiGraphics} 是立即渲染。
 * 这里通过 {@link GuiGraphics#drawSpecial} 在当前 GUI 的位姿下跑自定义着色器：
 * 几何永远是矩形 quad，圆角、折射、高光、投影全部在片元着色器里按 SDF 计算。</p>
 *
 * <p>26.x 用 shader define 编译期注入的材质常量（半径、染色、模糊档位等），
 * 1.21.1 没有对应机制，改走 shader json 的 {@code uniforms}，在绘制前逐项写入。
 * 随顶点色传参的 32 位调参位域（{@link GlassDebug#encode}）保持不变。</p>
 */
public final class GlassPanel {

    /** 着色器已确认可用。 */
    private static boolean prepared;
    /** 着色器或贴图彻底不可用，本进程内不再尝试。 */
    private static boolean unavailable;
    /** 本帧是否有界面请求了玻璃（由提取阶段登记，绘制前消费）。 */
    private static boolean requested;

    /**
     * 控件用的固定材质，供 prewarm 校验。
     */
    private static final GlassStyle[] CONTROL_STYLES = {
            GlassTheme.BUTTON, GlassTheme.FIELD, GlassTheme.TOGGLE, GlassTheme.KNOB,
            GlassTheme.CARD, GlassTheme.TRACK, GlassTheme.PILL, GlassTheme.ROW, GlassTheme.BADGE
    };

    private GlassPanel() {
    }

    /**
     * 画一块玻璃面板。
     */
    public static boolean draw(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                               GlassStyle style) {
        return draw(graphics, x0, y0, x1, y1, style, true);
    }

    /** 当前这一帧的外观（模糊度），由界面在打开时设置。 */
    private static GlassLook look = new GlassLook(1.0F);

    /**
     * 子控件用的外观：在 {@link #look} 的基础上带上窗口叠加层的浓度。
     */
    private static GlassLook childLook = new GlassLook(1.0F);

    public static void setLook(GlassLook value) {
        look = value == null ? new GlassLook(1.0F) : value;
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
     * 提前把玻璃要用的一切重资源准备好。
     *
     * <p>1.21.1 的着色器由 Fabric 的 {@code CoreShaderRegistrationCallback} 在资源重载时
     * 统一编译，这里只校验实例已就位、离屏贴图已分配。第一份背景模糊留给
     * {@link #beforeGui()} 在帧内抓。</p>
     */
    public static void prewarm(GlassStyle style) {
        GlassBackdrop backdrop = GlassBackdrop.getInstance();
        setLook(GlassTheme.look());
        backdrop.invalidate();
        if (unavailable) {
            return;
        }
        if (!GlassPipelines.shadersReady()) {
            unavailable = true;
            MusicPlayerMod.LOGGER.warn("液态玻璃着色器未注册，改用简易面板");
            return;
        }
        prepared = true;
        backdrop.prepare();
    }

    /**
     * 界面打开阶段调用一次：登记「本帧要画玻璃」。
     */
    public static void beginFrame(GlassStyle style) {
        requested = true;
        if (unavailable) {
            return;
        }
        if (!prepared && GlassPipelines.shadersReady()) {
            prepared = true;
        }
    }


    /**
     * 界面绘制之前由 {@code GameRendererMixin} 调用：此时主渲染目标里是本帧已经画好的
     * 世界、且界面尚未绘制，正是抓取玻璃背景的唯一正确时机。
     */
    public static void beforeGui() {
        // 只有本帧确实要画玻璃的界面才需要抓；纯享模式、普通界面都不碰这里
        if (!requested) {
            return;
        }
        requested = false;
        GlassBackdrop.getInstance().captureFrame();
    }

    /** 释放玻璃背景的离屏资源（退世界 / 关客户端）。 */
    public static void resetScene() {
        GlassBackdrop.getInstance().reset();
    }

    /**
     * 把模糊背景铺在整块面板下面（不画玻璃本身，也不投影）。
     *
     * <p>玻璃只是调色层：{@code glass.fsh} 输出的 alpha 由材质决定，按钮、开关这类
     * 半透明控件在任意画面上都清楚可读，靠的就是底下先垫一层不透明的模糊背景。
     * 窗口本身不垫（它自己已经够浑厚），窗口内的子控件靠这层定住对比度。</p>
     *
     * @return 是否真的铺了
     */
    public static boolean drawBackdrop(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                       GlassStyle style) {
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        GlassBackdrop backdrop = GlassBackdrop.getInstance();
        if (unavailable || !prepared || backdrop.hasFailed() || !backdrop.isReady()) {
            return false;
        }
        drawBackdropQuad(graphics, x0, y0, x1, y1);
        return true;
    }

    /**
     * 画一块玻璃面板。
     *
     * @param withShadow 是否绘制底部投影
     * @return 是否真的画了玻璃
     */
    public static boolean draw(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                               GlassStyle style, boolean withShadow) {
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        GlassBackdrop backdrop = GlassBackdrop.getInstance();
        if (unavailable || !prepared || backdrop.hasFailed()) {
            graphics.fill(x0, y0, x1, y1, style.fallback());
            return false;
        }
        if (!backdrop.isReady()) {
            // 只是背景贴图这一帧还没抓好（界面刚打开的第一帧）：直接跳过，什么都不画。
            return false;
        }

        GlassShadow shadow = style.shadow();
        if (withShadow && shadow.visible() && GlassTheme.shadowEnabled()) {
            int padding = shadow.padding();
            drawQuad(graphics, x0 - padding, y0 - padding, x1 + padding, y1 + padding,
                    true, style, 0xFFFFFFFF);
        }
        drawQuad(graphics, x0, y0, x1, y1, false, style, GlassDebug.encode(style));
        return true;
    }

    /**
     * 画一块玻璃控件（无投影），边缘折射与色散可逐帧覆盖，其余参数取自材质。
     *
     * @return 是否画成功；背景未就绪或着色器不可用时返回 {@code false}
     */
    public static boolean drawTuned(GuiGraphics graphics, int x0, int y0, int x1, int y1,
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
    public static boolean drawTuned(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                    GlassStyle style, float refraction, float dispersion,
                                    float centerDispersion, float aDistance,
                                    float contraRatio, float shrink) {
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        GlassBackdrop backdrop = GlassBackdrop.getInstance();
        if (unavailable || !prepared || backdrop.hasFailed() || !backdrop.isReady()) {
            return false;
        }
        drawQuad(graphics, x0, y0, x1, y1, false, style,
                GlassDebug.pack(refraction, dispersion, centerDispersion,
                        aDistance, contraRatio, shrink));
        return true;
    }

    /**
     * 用自定义 glass 着色器画一个矩形 quad（立即渲染路径）。
     *
     * @param shadow {@code true} 时把着色器切到投影分支（ShadowMode uniform）
     * @param color  32 位调参位域，随顶点色传入
     */
    private static void drawQuad(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                 boolean shadow, GlassStyle style, int color) {
        drawGlassQuad(graphics, x0, y0, x1, y1, shadow, style, color, false);
    }

    /** 铺一块不透明的模糊背景：完整走一遍玻璃着色器，只跳过边缘高光与顶部柔光。 */
    private static void drawBackdropQuad(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        drawGlassQuad(graphics, x0, y0, x1, y1, false, GlassTheme.BADGE, 0, true);
    }

    private static void drawGlassQuad(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                      boolean shadow, GlassStyle style, int color,
                                      boolean backdropOnly) {
        ShaderInstance shader = GlassPipelines.glass();
        if (shader == null) {
            return;
        }
        GlassBackdrop backdrop = GlassBackdrop.getInstance();
        GlassLook useLook = shadow ? look : childLook;
        Matrix4f pose = graphics.pose().last().pose();
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        Matrix4f modelView = RenderSystem.getModelViewMatrix();

        // 材质常量 → uniforms（26.x 用 shader define，这里逐帧写入，成本可忽略）
        setStyleUniforms(shader, style, useLook);
        shader.safeGetUniform("GlassBackdropOnly").set(backdropOnly ? 1.0F : 0.0F);
        // 片元着色器里这个开关叫 GlassShadowMode；写 ShadowMode 会命中空 uniform，
        // 投影就会被当成普通玻璃再画一遍
        shader.safeGetUniform("GlassShadowMode").set(shadow ? 1.0F : 0.0F);
        shader.safeGetUniform("GuiSize").set(
                (float) graphics.guiWidth(), (float) graphics.guiHeight());

        // 三层背景贴图。
        // 注意：`drawWithShader()` 内部会先按 `RenderSystem.getShaderTexture(i)` 重写
        // Sampler0..11，因此必须写 `RenderSystem.setShaderTexture`；用 `shader.setSampler`
        // 会在下一步被覆盖，采到纹理 0（黑）。
        int saved0 = RenderSystem.getShaderTexture(0);
        int saved1 = RenderSystem.getShaderTexture(1);
        int saved2 = RenderSystem.getShaderTexture(2);
        RenderSystem.setShaderTexture(0, shadow ? 0
                : (useLook.quantizedBlur() <= 0.0F ? backdrop.sharpTextureId()
                : backdrop.softTextureId()));
        if (!shadow) {
            RenderSystem.setShaderTexture(1, backdrop.frostTextureId());
            RenderSystem.setShaderTexture(2, backdrop.heavyTextureId());
            shader.safeGetUniform("SamplerSizes").set(
                    backdrop.softWidth(), backdrop.softHeight(),
                    backdrop.frostWidth(), backdrop.frostHeight());
            shader.safeGetUniform("Sampler2Size").set(
                    (float) backdrop.heavyWidth(), (float) backdrop.heavyHeight());
        }

        // 四个顶点的 GUI 坐标（着色器 vsh 里经 ProjMat * ModelViewMat 变换）
        float left = x0;
        float top = y0;
        float right = x1;
        float bottom = y1;

        // 深度测试必须是关的。glass.fsh 靠 `discard` 把圆角之外的片元丢掉，
        // 而 discard 不看深度：一旦开了深度测试、它自己也不写深度，面板就只是一层
        // 按颜色混合的半透明色块 —— 圆角会消失、半透明区域会把整个四边形染上色。
        //
        // 1.20.1 的 GuiGraphics.flush()（画完 vignette 之后）会把深度测试重新打开，
        // 而 1.21.1 没有这个行为，所以这里必须自己按玻璃的语义设置，并在结束后还原，
        // 免得污染后续 GUI 绘制。
        boolean depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(pose, left, top, 0.0F).uv(0.0F, 0.0F).color(color).endVertex();
        builder.vertex(pose, left, bottom, 0.0F).uv(0.0F, 1.0F).color(color).endVertex();
        builder.vertex(pose, right, bottom, 0.0F).uv(1.0F, 1.0F).color(color).endVertex();
        builder.vertex(pose, right, top, 0.0F).uv(1.0F, 0.0F).color(color).endVertex();
        BufferBuilder.RenderedBuffer mesh = builder.end();
        if (mesh != null) {
            // POSITION_TEX_COLOR 在 1.20.1 是非索引格式：QUADS 直接整块上传绘制
            try (VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC)) {
                vb.bind();
                vb.upload(mesh);
                vb.drawWithShader(modelView, projection, shader);
            }
            VertexBuffer.unbind();
        }
        // shaderTextures 是全局状态，画完立刻还原，避免污染后续 GUI 绘制
        RenderSystem.setShaderTexture(0, saved0);
        RenderSystem.setShaderTexture(1, saved1);
        RenderSystem.setShaderTexture(2, saved2);
        if (depthTestWasEnabled) {
            RenderSystem.enableDepthTest();
        }
        // 混合状态必须还原。
        //
        // 1.20.1 的 BlendMode.apply() 有两层提前返回：lastApplied 相同就直接 return，
        // 且只有 opaque 标志发生变化时才去 enable/disable 混合。玻璃着色器的 blend 节点
        // 与原版 GUI 用的完全相同（src_alpha / 1-src_alpha），于是它已把 lastApplied
        // 记成「非 opaque」；这里若把混合关掉，后续原版绘制 apply() 会认为状态没变、
        // 不再打开混合，文字与半透明填充全部变成不透明色块。
        // 1.21.1 的 GuiGraphics 是立即渲染、逐 draw 设置状态，不存在这个缓存，所以必须
        // 在本方法内归还一个「混合开着、且与 lastApplied 一致」的状态。
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** 把材质常量写进着色器 uniforms（每次 draw 前调用）。 */
    private static void setStyleUniforms(ShaderInstance shader, GlassStyle style, GlassLook useLook) {
        GlassShadow shadow = style.shadow();
        shader.safeGetUniform("GlassRadius").set(style.radius());
        shader.safeGetUniform("GlassTint").set(
                style.tintR(), style.tintG(), style.tintB(), style.tintA());
        shader.safeGetUniform("GlassMaxLuma").set(style.maxLuma());
        shader.safeGetUniform("GlassSaturation").set(style.saturation());
        shader.safeGetUniform("GlassHighlight").set(style.highlight());
        shader.safeGetUniform("GlassEdgeLight").set(style.edgeLight());
        shader.safeGetUniform("GlassOpacity").set(style.opacity());
        shader.safeGetUniform("GlassBlur").set(useLook.quantizedBlur());
        shader.safeGetUniform("GlassWindowTint").set(useLook.quantizedWindowTint());
        float[] tint = GlassTheme.overlayColorRgb();
        // 着色器里是 vec3，json 也声明 count=3；多传一个分量会写爆 Uniform 的 FloatBuffer
        shader.safeGetUniform("GlassWindowTintColor").set(tint[0], tint[1], tint[2]);
        shader.safeGetUniform("GlassShadowMode").set(0.0F);
        shader.safeGetUniform("GlassShadowSize").set(shadow.size());
        shader.safeGetUniform("GlassShadowAlpha").set(shadow.alpha());
        shader.safeGetUniform("GlassShadowOffset").set(shadow.offsetX(), shadow.effectiveOffsetY());
        shader.safeGetUniform("GlassShadowPadding").set((float) shadow.padding());
    }
}
