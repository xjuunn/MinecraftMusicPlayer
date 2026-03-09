package com.junhsiun.musicplayer.mixin.client;

import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(LevelEventHandler.class)
public interface LevelEventHandlerAccessor {
    @Accessor("playingJukeboxSongs")
    Map<BlockPos, SoundInstance> musicplayer$getPlayingJukeboxSongs();
}
