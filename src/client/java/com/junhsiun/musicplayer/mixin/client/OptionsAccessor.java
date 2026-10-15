package com.junhsiun.musicplayer.mixin.client;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Options.class)
public interface OptionsAccessor {
    @Accessor("sourceVolumes")
    Object2FloatMap<SoundSource> getSourceVolumes();
}
