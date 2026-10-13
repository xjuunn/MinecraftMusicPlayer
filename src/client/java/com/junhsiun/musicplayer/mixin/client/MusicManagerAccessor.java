package com.junhsiun.musicplayer.mixin.client;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MusicManager.class)
public interface MusicManagerAccessor {
    @Accessor("nextSongDelay")
    void setNextSongDelay(int delay);
}
