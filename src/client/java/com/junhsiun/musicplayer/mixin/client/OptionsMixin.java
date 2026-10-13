package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.config.MusicPlayerClientConfigManager;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsMixin {

    @Inject(method = "getSoundSourceVolume", at = @At("HEAD"), cancellable = true)
    private void musicplayer$bgmVolumeForMusic(SoundSource source, CallbackInfoReturnable<Float> cir) {
        if (source == SoundSource.MUSIC) {
            float volume = (float) MusicPlayerClientConfigManager.get().bgmVolume;
            if (Float.isNaN(volume)) {
                volume = 0.7F;
            }
            cir.setReturnValue(Math.max(0.0F, Math.min(1.0F, volume)));
        }
    }
}
