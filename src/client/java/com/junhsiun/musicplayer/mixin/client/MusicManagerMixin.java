package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.ClientJukeboxController;
import com.junhsiun.musicplayer.client.ClientMusicController;
import com.junhsiun.musicplayer.client.ClientBgmController;
import net.minecraft.client.sounds.MusicManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void musicplayer$suppressVanillaMusic(CallbackInfo ci) {
        if (ClientMusicController.getInstance().isBackgroundMusicPlaying()
                || ClientJukeboxController.getInstance().isPlayerNearAnyActiveJukebox()) {
            ci.cancel();
        }
    }

    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void musicplayer$interceptBgm(MusicManager.Music music, CallbackInfo ci) {
        ClientBgmController bgm = ClientBgmController.getInstance();
        ClientBgmController.Decision decision = bgm.onVanillaStartRequested();
        if (decision == ClientBgmController.Decision.PASS) {
            return;
        }
        ci.cancel();
        // 照抄原版 startPlaying 末尾：播放期间阻止 tick 逐 tick 重新触发
        ((MusicManagerAccessor) this).setNextSongDelay(Integer.MAX_VALUE);
        if (decision == ClientBgmController.Decision.PLAY) {
            bgm.requestPlay(music);
        }
    }
}
