package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.idle.IdleController;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 纯享模式期间隐藏 HUD，效果等同于按住 F1。
 *
 * <p>1.20.1 里 F1 开关是 {@code Minecraft.options.hideGui}（26.x 才移到 {@code Hud.isHidden}）。
 * 特效出现前把它置真、淡出后恢复。这里做成挂靠在 {@code Options} 上的静态工具类，
 * 由 {@code IdleRenderer.beforeGui} 每帧调用。</p>
 *
 * <p>玩家自己按 F1 会把 {@code hideGui} 改成常驻状态，所以接管前必须先记下原值，
 * 结束纯享时还原，否则「退出纯享后强制显示 HUD」会覆盖玩家原本的设置。</p>
 */
@Mixin(Options.class)
public abstract class HudMixin {

    /** 接管前玩家自己的 {@code hideGui}：{@code -1} 表示当前没有接管。 */
    @Unique
    private static int musicplayer$savedHideGui = -1;

    /** 每帧调用：纯享期间隐藏 HUD，其余时间保持玩家自己的 F1 状态不动。 */
    public static void musicplayer$updateHudHidden(Minecraft minecraft) {
        boolean shouldHide = IdleController.getInstance().shouldHideHud(Util.getMillis());
        if (!shouldHide) {
            musicplayer$restoreHud(minecraft);
            return;
        }
        if (musicplayer$savedHideGui < 0) {
            musicplayer$savedHideGui = minecraft.options.hideGui ? 1 : 0;
        }
        minecraft.options.hideGui = true;
    }

    /** 退还 HUD 控制权：恢复成接管前玩家自己的 F1 状态；从未接管时什么都不做。 */
    public static void musicplayer$restoreHud(Minecraft minecraft) {
        if (musicplayer$savedHideGui < 0) {
            return;
        }
        minecraft.options.hideGui = musicplayer$savedHideGui == 1;
        musicplayer$savedHideGui = -1;
    }
}
