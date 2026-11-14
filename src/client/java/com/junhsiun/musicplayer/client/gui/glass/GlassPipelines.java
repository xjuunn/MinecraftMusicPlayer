package com.junhsiun.musicplayer.client.gui.glass;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 液态玻璃用到的 GPU 管线。
 *
 * <p>26.x 的渲染全部走 {@code RenderPipeline}：顶点/片元着色器按 {@code assets/<命名空间>/shaders/core/}
 * 下的文件加载，编译期的常量通过 shader define 注入。管线第一次被真正绘制时由
 * {@code PipelineCache} 惰性编译，所以这里可以放心地在运行时按需构造并缓存。</p>
 *
 * <p>本类返回的管线分三种：</p>
 * <ul>
 *   <li>{@link #panel(GlassStyle, GlassLook)}：玻璃本体（折射 + 色散 + 高光 + 染色）；</li>
 *   <li>{@link #shadow(GlassStyle)}：面板投影，和本体共用一份着色器，用 define 切分支；</li>
 *   <li>{@link #blur(boolean)}：把画面逐级降采样并做可分离高斯模糊。</li>
 * </ul>
 */
public final class GlassPipelines {

    private static final String NAMESPACE = MusicPlayerMod.MOD_ID;
    private static final Identifier GLASS_VERTEX_SHADER =
            Identifier.fromNamespaceAndPath(NAMESPACE, "core/glass");
    private static final Identifier GLASS_FRAGMENT_SHADER =
            Identifier.fromNamespaceAndPath(NAMESPACE, "core/glass");
    private static final Identifier BLUR_VERTEX_SHADER =
            Identifier.fromNamespaceAndPath(NAMESPACE, "core/glass_blur");
    private static final Identifier BLUR_FRAGMENT_SHADER =
            Identifier.fromNamespaceAndPath(NAMESPACE, "core/glass_blur");

    private static final Map<String, RenderPipeline> PANEL_PIPELINES = new HashMap<>();
    private static final Map<String, RenderPipeline> SHADOW_PIPELINES = new HashMap<>();
    private static final Map<String, RenderPipeline> BLUR_PIPELINES = new HashMap<>();

    private GlassPipelines() {
    }

    /**
     * 玻璃本体管线。
     *
     * <p>注意折射、斜面、色散、中心色散<b>不在这里</b>：它们逐 draw 由顶点色传入
     * （见 {@link GlassDebug#encode(GlassStyle)}），所以不需要为它们各编译一份管线。</p>
     *
     * <p>模糊度是编译期常量（顶点格式只有一路 color，已被六项光学参数占满），
     * 因此它被量化成有限档位，见 {@link GlassLook}。同一档位共用一份管线。</p>
     */
    public static synchronized RenderPipeline panel(GlassStyle style, GlassLook look) {
        String cacheKey = style.pipelineKey() + "/" + look.key();
        return PANEL_PIPELINES.computeIfAbsent(cacheKey, key -> {
            float[] tintColor = GlassTheme.overlayColorRgb();
            RenderPipeline.Builder builder = baseGuiPipeline("panel", key)
                    .withFragmentShader(GLASS_FRAGMENT_SHADER)
                    .withShaderDefine("GLASS_RADIUS", style.radius())
                    .withShaderDefine("GLASS_TINT_R", style.tintR())
                    .withShaderDefine("GLASS_TINT_G", style.tintG())
                    .withShaderDefine("GLASS_TINT_B", style.tintB())
                    .withShaderDefine("GLASS_TINT_A", style.tintA())
                    .withShaderDefine("GLASS_MAX_LUMA", style.maxLuma())
                    .withShaderDefine("GLASS_SATURATION", style.saturation())
                    .withShaderDefine("GLASS_HIGHLIGHT", style.highlight())
                    .withShaderDefine("GLASS_EDGE_LIGHT", style.edgeLight())
                    .withShaderDefine("GLASS_OPACITY", style.opacity())
                    .withShaderDefine("GLASS_BLUR", look.quantizedBlur())
                    // 模糊层级：0 = 只用原图（1 次采样），1 = 两层（2 次），2 = 三层（3 次）。
                    // 着色器据此做编译期静态分支，把色散路径的采样次数从 9 次压到 3~9 次中的最小值。
                    .withShaderDefine("GLASS_BLUR_LEVEL", blurLevel(look.quantizedBlur()))
                    // 子控件用：把"世界背景 + 窗口叠加层"合成为底色，
                    // 等价于把所在的窗口玻璃当成背景。窗口自身这一项是 0。
                    .withShaderDefine("GLASS_WINDOW_TINT", look.quantizedWindowTint())
                    // 叠加层颜色由 GlassTheme 单一来源提供，避免着色器里再抄一份常量而失配
                    .withShaderDefine("GLASS_WINDOW_TINT_R", tintColor[0])
                    .withShaderDefine("GLASS_WINDOW_TINT_G", tintColor[1])
                    .withShaderDefine("GLASS_WINDOW_TINT_B", tintColor[2]);
            // 三张背景层必须在这里声明。26.1 的 GlProgram.setupUniforms 只给管线声明过的
            // sampler 建立名字→纹理单元的映射，未声明的 sampler 在 draw 时找不到对应 Uniform，
            // 纹理单元永远不会被真正绑定，texture() 恒读到 0（背景就是纯黑）。
            //
            // 只声明该模糊档位真正采样的那几张：GLASS_BLUR_LEVEL 是编译期常量，着色器里
            // 多余的 sampler 会被整段折掉，声明了反而触发 "does not use sampler" 告警。
            int level = blurLevel(look.quantizedBlur());
            builder.withSampler("Sampler0");
            if (level >= 1) {
                builder.withSampler("Sampler1");
            }
            if (level >= 2) {
                builder.withSampler("Sampler2");
            }
            return builder.build();
        });
    }

    /**
     * 把模糊度映射到"需要几层贴图"，供着色器做静态分支。
     *
     * <p>{@code blur = 0} 时着色器只用 Sampler0（原图），完全不需要另两张，
     * 直接省掉 2/3 的采样；{@code blur <= 0.5} 时第三层的混合权重为 0，
     * 也一并省掉。其余情况三层都要。</p>
     */
    private static int blurLevel(float quantizedBlur) {
        if (quantizedBlur <= 0.0F) {
            return 0;
        }
        return quantizedBlur <= 0.5F ? 1 : 2;
    }

    /** 面板投影管线。 */
    public static synchronized RenderPipeline shadow(GlassStyle style) {
        GlassShadow shadow = style.shadow();
        String key = style.pipelineKey() + "/shadow";
        return SHADOW_PIPELINES.computeIfAbsent(key, ignored -> baseGuiPipeline("shadow", key)
                .withFragmentShader(GLASS_FRAGMENT_SHADER)
                .withShaderDefine("GLASS_SHADOW")
                .withShaderDefine("GLASS_RADIUS", style.radius())
                .withShaderDefine("GLASS_SHADOW_SIZE", shadow.size())
                .withShaderDefine("GLASS_SHADOW_ALPHA", shadow.alpha())
                .withShaderDefine("GLASS_SHADOW_OFFSET_X", shadow.offsetX())
                .withShaderDefine("GLASS_SHADOW_OFFSET_Y", shadow.effectiveOffsetY())
                // 投影四边形向外扩张的像素数。着色器要用它把四边形尺寸还原成面板尺寸，
                // 否则 SDF 会描述扩张后的框，投影变成一圈离开面板的圆角暗环。
                .withShaderDefine("GLASS_SHADOW_PADDING", shadow.padding())
                .build());
    }

    /**
     * 可分离高斯的单向管线。
     *
     * @param spread   扩散量（源贴图的 texel 数），0 表示纯降采样
     * @param vertical {@code true} 纵向、{@code false} 横向
     */
    public static synchronized RenderPipeline blur(float spread, boolean vertical) {
        String key = spread + "/" + vertical;
        return BLUR_PIPELINES.computeIfAbsent(key, ignored -> {
            boolean verticalBlur = vertical;
            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE,
                            "pipeline/glass_blur_" + Float.floatToIntBits(spread) + (verticalBlur ? "_v" : "_h")))
                    .withVertexShader(BLUR_VERTEX_SHADER)
                    .withFragmentShader(BLUR_FRAGMENT_SHADER)
                    .withSampler("Sampler0")
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES)
                    .withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL))
                    // 离屏颜色目标没有深度附件，管线必须声明"不需要深度"
                    .withDepthStencilState(Optional.<DepthStencilState>empty())
                    .withCull(false)
                    .withShaderDefine("GLASS_BLUR_SPREAD", spread);
            if (verticalBlur) {
                builder.withShaderDefine("GLASS_BLUR_VERTICAL");
            }
            return builder.build();
        });
    }

    private static RenderPipeline.Builder baseGuiPipeline(String kind, String key) {
        String hash = Integer.toHexString(key.hashCode());
        return RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE,
                        "pipeline/glass_" + kind + "_" + hash.toLowerCase(Locale.ROOT)))
                .withVertexShader(GLASS_VERTEX_SHADER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(Optional.<DepthStencilState>empty())
                .withCull(false);
    }
}
