package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.ClientMusicController;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void musicplayer$suppressVanillaMusic(CallbackInfo ci) {
        if (ClientMusicController.getInstance().isBackgroundMusicPlaying()) {
            ci.cancel();
        }
    }
}
