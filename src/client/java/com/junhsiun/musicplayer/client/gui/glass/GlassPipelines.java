package com.junhsiun.musicplayer.client.gui.glass;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

/**
 * 液态玻璃用到的 GPU 着色器（1.21.1 老 {@code ShaderInstance} 体系）。
 *
 * <p>26.x 的 {@code RenderPipeline} 在 1.21.1 不存在。这里改用 Fabric 的
 * {@link CoreShaderRegistrationCallback} 把自定义 core shader 注册进原版着色器池，
 * 绘制时通过 {@code RenderSystem.setShader()} + {@code VertexBuffer.drawWithShader()}
 * 走老式立即渲染。</p>
 *
 * <p>26.x 管线里的 shader define 在 1.21.1 没有对应机制，调参（半径、染色、模糊档位等）
 * 全部改走 shader json 的 {@code uniforms}；着色器文件里的对应常量也已改写为
 * 从 uniform 读入（见 {@code assets/musicplayer/shaders/core/} 下 1.21.1 版本的 glsl）。</p>
 */
public final class GlassPipelines {

    private static final String NAMESPACE = MusicPlayerMod.MOD_ID;

    static net.minecraft.client.renderer.ShaderInstance glassShader;
    static net.minecraft.client.renderer.ShaderInstance glassBlurShader;
    static net.minecraft.client.renderer.ShaderInstance glassWidgetShader;
    static net.minecraft.client.renderer.ShaderInstance idleShader;

    private static volatile boolean registered;

    private GlassPipelines() {
    }

    /** 在客户端初始化时注册全部自定义 core shader（幂等）。 */
    public static synchronized void registerShaders() {
        if (registered) {
            return;
        }
        registered = true;
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(id(NAMESPACE, "glass"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    shader -> glassShader = shader);
            context.register(id(NAMESPACE, "glass_blur"), DefaultVertexFormat.POSITION_TEX,
                    shader -> glassBlurShader = shader);
            context.register(id(NAMESPACE, "glass_widget"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    shader -> glassWidgetShader = shader);
            context.register(id(NAMESPACE, "idle"), DefaultVertexFormat.POSITION_TEX,
                    shader -> idleShader = shader);
        });
    }

    private static ResourceLocation id(String namespace, String name) {
        return new ResourceLocation(namespace, name);
    }

    /** 玻璃本体着色器；资源重载后实例会被替换，必须每次经此获取。 */
    public static net.minecraft.client.renderer.ShaderInstance glass() {
        return glassShader;
    }

    /** 背景模糊着色器。 */
    public static net.minecraft.client.renderer.ShaderInstance glassBlur() {
        return glassBlurShader;
    }

    /** 圆角填充控件着色器。 */
    public static net.minecraft.client.renderer.ShaderInstance glassWidget() {
        return glassWidgetShader;
    }

    /** 纯享特效着色器。 */
    public static net.minecraft.client.renderer.ShaderInstance idle() {
        return idleShader;
    }

    /** 资源重载（退世界等）后着色器实例会失效，清空引用等重新注册。 */
    public static void clearShaders() {
        glassShader = null;
        glassBlurShader = null;
        glassWidgetShader = null;
        idleShader = null;
    }

    /** 仅供旧代码引用资源路径。 */
    public static ResourceLocation shaderPath(String name) {
        return id(NAMESPACE, name);
    }

    /** 供 GlassPanel 判断资源是否就绪。 */
    public static boolean shadersReady() {
        return glassShader != null && glassWidgetShader != null;
    }

    /** 保留原 API 形状（旧调用点已全部改造，这里留一个空实现兜底）。 */
    public static void noop(ResourceProvider provider) {
    }
}
