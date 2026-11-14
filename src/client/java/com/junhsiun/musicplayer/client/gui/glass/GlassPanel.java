package com.junhsiun.musicplayer.client.gui.glass;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

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
     * 在界面绘制之前由 {@code GameRendererMixin} 调用：此时主渲染目标里是
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
        shader.safeGetUniform("ShadowMode").set(shadow ? 1.0F : 0.0F);
        shader.safeGetUniform("GuiSize").set(
                (float) graphics.guiWidth(), (float) graphics.guiHeight());

        // 三层背景贴图
        shader.setSampler("Sampler0", shadow ? 0
                : (useLook.quantizedBlur() <= 0.0F ? backdrop.sharpTextureId()
                : backdrop.softTextureId()));
        if (!shadow) {
            shader.setSampler("Sampler1", backdrop.frostTextureId());
            shader.setSampler("Sampler2", backdrop.heavyTextureId());
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

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        BufferBuilder builder = Tesselator.getInstance().begin(Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(pose, left, top, 0.0F).setUv(0.0F, 0.0F).setColor(color);
        builder.addVertex(pose, left, bottom, 0.0F).setUv(0.0F, 1.0F).setColor(color);
        builder.addVertex(pose, right, bottom, 0.0F).setUv(1.0F, 1.0F).setColor(color);
        builder.addVertex(pose, right, top, 0.0F).setUv(1.0F, 0.0F).setColor(color);
        MeshData mesh = builder.build();
        if (mesh != null) {
            // POSITION_TEX_COLOR 是 indexed 格式：显式建索引再上传
            try (VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC)) {
                vb.bind();
                vb.upload(mesh);
                vb.drawWithShader(modelView, projection, shader);
            }
            VertexBuffer.unbind();
        }
        RenderSystem.disableBlend();
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
        shader.safeGetUniform("GlassWindowTintColor").set(tint[0], tint[1], tint[2], 0.0F);
        shader.safeGetUniform("GlassShadowMode").set(0.0F);
        shader.safeGetUniform("GlassShadowSize").set(shadow.size());
        shader.safeGetUniform("GlassShadowAlpha").set(shadow.alpha());
        shader.safeGetUniform("GlassShadowOffset").set(shadow.offsetX(), shadow.effectiveOffsetY());
        shader.safeGetUniform("GlassShadowPadding").set((float) shadow.padding());
    }
}
