package com.junhsiun.musicplayer.client.idle;

import com.junhsiun.musicplayer.client.gui.glass.GlassPipelines;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * 纯享特效的着色器入口（1.21.1 老 {@code ShaderInstance} 体系）。
 *
 * <p>26.x 每种预设各编译一份管线、封面有无再各一份；1.21.1 没有 shader define，
 * 预设与封面开关都改走 {@code idle} 着色器的 uniforms（{@code IdleMeta.z} 携带
 * 预设编号，无封面时 {@code IdleMeta.y} 置 0 让着色器跳过封面分支），
 * 由 {@link IdleRenderer} 在绘制前写入。这里只负责暴露着色器实例。</p>
 */
public final class IdlePipelines {

    private IdlePipelines() {
    }

    /** 纯享特效着色器；资源重载后实例会被替换，必须每次经此获取。 */
    public static ShaderInstance idle() {
        return GlassPipelines.idle();
    }
}
