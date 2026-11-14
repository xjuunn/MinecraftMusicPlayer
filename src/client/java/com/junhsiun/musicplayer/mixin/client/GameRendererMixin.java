package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.gui.glass.GlassPanel;
import com.junhsiun.musicplayer.client.idle.IdleRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在界面真正绘制之前抓取画面，供液态玻璃做背景模糊、纯享模式画全屏特效。
 *
 * <p>1.21.1 的 {@code GameRenderer.render(DeltaTracker, boolean)} 内部顺序是：</p>
 * <pre>
 * renderLevel()   ← 世界渲染
 * Gui.render()    ← 界面绘制（HUD / Screen）
 * </pre>
 *
 * <p>所以在 {@code Gui.render} 调用前注入：此时主渲染目标里正好是本帧的世界、
 * 且还没有画上界面，是抓取背景的唯一正确时机。</p>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V")
    )
    private void musicplayer$captureGlassBackdrop(CallbackInfo callbackInfo) {
        GlassPanel.beforeGui();
        IdleRenderer.beforeGui();
    }
}
