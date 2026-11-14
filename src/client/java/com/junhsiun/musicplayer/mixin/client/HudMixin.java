package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.idle.IdleController;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 纯享模式期间隐藏 HUD，效果等同于按住 F1。
 *
 * <p>26.1 的 HUD 开关在 {@code Options.hideGui}（{@code Hud.isHidden} 尚不存在），
 * 在 {@code Gui.extractRenderState} 读取前改掉并在返回后恢复，避免永久改变玩家设置。</p>
 */
@Mixin(net.minecraft.client.gui.Gui.class)
public abstract class HudMixin {

    @Unique
    private boolean musicplayer$restoreHud;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void musicplayer$hideHudForIdle(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                            net.minecraft.client.DeltaTracker deltaTracker,
                                            CallbackInfo callbackInfo) {
        var options = Minecraft.getInstance().options;
        // 上一帧如果在 RETURN 之前抛了异常（界面代码崩溃），标记会残留。
        // 这里先把 HUD 恢复回去，避免一次崩溃让 HUD 永久消失、还带进下一局。
        if (musicplayer$restoreHud) {
            options.hideGui = false;
            musicplayer$restoreHud = false;
        }
        // 从特效出现的第一帧到淡出结束都保持隐藏，避免 HUD 叠在半透明特效上。
        if (!IdleController.getInstance().shouldHideHud(Util.getMillis())) {
            return;
        }
        if (!options.hideGui) {
            options.hideGui = true;
            musicplayer$restoreHud = true;
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void musicplayer$restoreHudAfterIdle(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                                 net.minecraft.client.DeltaTracker deltaTracker,
                                                 CallbackInfo callbackInfo) {
        if (musicplayer$restoreHud) {
            Minecraft.getInstance().options.hideGui = false;
            musicplayer$restoreHud = false;
        }
    }
}
