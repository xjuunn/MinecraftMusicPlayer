package com.junhsiun.musicplayer.client.idle;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 纯享特效的 GPU 管线。
 *
 * <p>每种预设各编译一份；封面存在时再多一份带 {@code Sampler1} 的变体，
 * 这样着色器里可以彻底省掉"没有封面"的采样分支。所有逐帧参数都走
 * {@code IdleParams} uniform buffer，不触发重新编译。</p>
 */
public final class IdlePipelines {

    private static final String NAMESPACE = MusicPlayerMod.MOD_ID;
    private static final Identifier VERTEX_SHADER =
            Identifier.fromNamespaceAndPath(NAMESPACE, "core/idle");
    private static final Identifier FRAGMENT_SHADER =
            Identifier.fromNamespaceAndPath(NAMESPACE, "core/idle");

    private static final BindGroupLayout LAYOUT_PLAIN = BindGroupLayout.builder()
            .withUniform("IdleParams", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .build();

    private static final BindGroupLayout LAYOUT_COVER = BindGroupLayout.builder()
            .withUniform("IdleParams", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .build();

    private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();

    private IdlePipelines() {
    }

    public static synchronized RenderPipeline effect(IdleEffect effect, boolean hasCover) {
        String key = effect.key() + (hasCover ? "/cover" : "/plain");
        return PIPELINES.computeIfAbsent(key, ignored -> {
            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE,
                            "pipeline/idle_" + effect.key() + (hasCover ? "_cover" : "")))
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withBindGroupLayout(hasCover ? LAYOUT_COVER : LAYOUT_PLAIN)
                    .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM,
                            ColorTargetState.WRITE_ALL))
                    .withDepthStencilState(Optional.<DepthStencilState>empty())
                    .withCull(false)
                    .withShaderDefine("IDLE_MODE", effect.shaderMode());
            if (hasCover) {
                builder.withShaderDefine("IDLE_HAS_COVER");
            }
            return builder.build();
        });
    }
}
