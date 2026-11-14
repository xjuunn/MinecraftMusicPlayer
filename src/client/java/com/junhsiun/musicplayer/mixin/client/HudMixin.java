package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.idle.IdleController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 纯享模式期间隐藏 HUD，效果等同于按住 F1。
 *
 * <p>必须在 {@code Hud.extractRenderState} 真正读取 {@code isHidden} 之前把值改掉，
 * 并在方法返回后恢复用户原本的 F1 状态，避免永久改变玩家的设置。</p>
 */
@Mixin(Hud.class)
public abstract class HudMixin {

    @Unique
    private boolean musicplayer$restoreHud;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void musicplayer$hideHudForIdle(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                            CallbackInfo callbackInfo) {
        HudAccessor accessor = (HudAccessor) (Object) this;
        // 上一帧如果在 RETURN 之前抛了异常（界面代码崩溃），标记会残留。
        // 这里先把 HUD 恢复回去，避免一次崩溃让 HUD 永久消失、还带进下一局。
        if (musicplayer$restoreHud) {
            accessor.musicplayer$setHidden(false);
            musicplayer$restoreHud = false;
        }
        // 从特效出现的第一帧到淡出结束都保持隐藏，避免 HUD 叠在半透明特效上。
        if (!IdleController.getInstance().shouldHideHud(Util.getMillis())) {
            return;
        }
        if (!accessor.musicplayer$isHidden()) {
            accessor.musicplayer$setHidden(true);
            musicplayer$restoreHud = true;
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void musicplayer$restoreHudAfterIdle(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                                 CallbackInfo callbackInfo) {
        if (musicplayer$restoreHud) {
            ((HudAccessor) (Object) this).musicplayer$setHidden(false);
            musicplayer$restoreHud = false;
        }
    }
}
