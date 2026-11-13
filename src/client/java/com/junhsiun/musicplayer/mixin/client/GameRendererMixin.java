package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.gui.glass.GlassPanel;
import com.junhsiun.musicplayer.client.idle.IdleRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在界面真正绘制之前抓取画面，供液态玻璃做背景模糊。
 *
 * <p>26.x 的界面是"先提取、后绘制"，且 {@code GameRenderer} 里三件事的顺序是：</p>
 * <pre>
 * extract()  ──→ Gui.extractRenderState()   ← 界面提取（Screen.extractRenderState 在这里）
 * render()   ──→ renderLevel()              ← 世界渲染
 *            ──→ GuiRenderer.render()       ← 界面绘制
 * </pre>
 *
 * <p>所以提取阶段抓到的还是<b>上一帧合成完的画面</b>（其中已经包含上一帧画好的玻璃面板），
 * 玻璃会不断模糊自己并收敛成一片纯色。必须在 {@code GuiRenderer.render()} 之前抓，
 * 此时主渲染目标里正好是本帧的世界、且还没有画上界面。</p>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V")
    )
    private void musicplayer$captureGlassBackdrop(CallbackInfo callbackInfo) {
        GlassPanel.beforeGui();
        IdleRenderer.beforeGui(((GameRenderer) (Object) this).gameRenderState().guiRenderState);
    }
}
