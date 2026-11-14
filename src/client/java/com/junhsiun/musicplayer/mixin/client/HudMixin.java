package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.idle.IdleController;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 纯享模式期间隐藏 HUD，效果等同于按住 F1。
 *
 * <p>1.21.1 里 F1 开关是 {@code Options.hideGui}（26.x 才移到 {@code Hud.isHidden}），
 * 在 {@code Gui.render} 开头改掉、并在返回后恢复，避免永久改变玩家设置。HUD 各层是
 * 由 {@code LayeredDraw} 按 {@code hideGui} 每帧判断要不要画的，所以改在
 * {@code Gui.render} 的 HEAD 就能影响同一帧。</p>
 */
@Mixin(Gui.class)
public abstract class HudMixin {

    @Unique
    private boolean musicplayer$restoreHud;

    @Inject(method = "render", at = @At("HEAD"))
    private void musicplayer$hideHudForIdle(GuiGraphics graphics, DeltaTracker deltaTracker,
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
        // 玩家自己按 F1 时 hideGui 已经是 true，不去接管，也不在结束时把它的设置改掉。
        if (!options.hideGui) {
            options.hideGui = true;
            musicplayer$restoreHud = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void musicplayer$restoreHudAfterIdle(GuiGraphics graphics, DeltaTracker deltaTracker,
                                                 CallbackInfo callbackInfo) {
        if (musicplayer$restoreHud) {
            Minecraft.getInstance().options.hideGui = false;
            musicplayer$restoreHud = false;
        }
    }
}
