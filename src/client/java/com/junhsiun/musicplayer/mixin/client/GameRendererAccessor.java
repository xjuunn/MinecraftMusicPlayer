package com.junhsiun.musicplayer.mixin.client;

import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 的 {@code GameRenderer.guiRenderState} 是 package-private 字段且没有 getter，
 * 纯享模式要往里提交歌词文字，需要通过 accessor 拿到。
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor("guiRenderState")
    GuiRenderState musicplayer$guiRenderState();
}
