package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.idle.IdleController;
import net.minecraft.Util;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 纯享模式期间不画 HUD。
 *
 * <p><b>刻意不碰 {@code Minecraft.options.hideGui}（也就是 F1 的那个开关）。</b>
 * F1 是全局状态：一旦置真，{@code GameRenderer.render} 会认为「玩家要求隐藏界面」，
 * 打开液态玻璃窗口的瞬间也处在隐藏态，窗口会被一起吞掉；而且纯享是在
 * {@code beforeGui} 里按帧恢复的，玩家在淡出期间打开界面时那条恢复路径根本不跑，
 * {@code hideGui} 就会一直卡在 true。</p>
 *
 * <p>这里改成只跳过 {@code Gui.render} 本身：HUD（血条、快捷栏、准星、聊天）全部不画，
 * 但主渲染目标和界面绘制完全不受影响，液态玻璃窗口照常出现。效果上与 F1 一致。</p>
 *
 * <p>注意本类只注入、不提供任何公开静态方法：Mixin 类不允许被普通代码直接引用
 * （会触发 {@code IllegalClassLoadError: ... is in a defined mixin package}），
 * 所以判定逻辑留在 {@link IdleController#shouldHideHud(long)} 里，这里只调用。</p>
 */
@Mixin(Gui.class)
public abstract class HudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void musicplayer$hideHudForIdle(GuiGraphics graphics, float partialTick,
                                            CallbackInfo callbackInfo) {
        // 从特效淡入的第一帧就隐藏，一直保持到淡出彻底结束，
        // 否则会出现「HUD 浮在半透明特效上，然后突然消失」的突兀感。
        if (IdleController.getInstance().shouldHideHud(Util.getMillis())) {
            callbackInfo.cancel();
        }
    }
}
