package com.junhsiun.musicplayer.mixin.client;

import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(Options.class)
public interface OptionsAccessor {
    @Accessor("sourceVolumes")
    Map<SoundSource, Float> getSourceVolumes();
}
