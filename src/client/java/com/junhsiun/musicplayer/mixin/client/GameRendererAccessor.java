package com.junhsiun.musicplayer.mixin.client;

import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 的 {@code GameRenderer.guiRenderState} 是 package-private 字段、没有 getter，
 * 而纯享模式要把全屏特效提交进这一份渲染状态，需要先通过 accessor 拿到它。
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor("guiRenderState")
    GuiRenderState musicplayer$guiRenderState();
}
