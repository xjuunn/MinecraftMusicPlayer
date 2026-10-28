package com.junhsiun.musicplayer.mixin;

import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(JukeboxBlockEntity.class)
public interface JukeboxBlockEntityInvoker {
    @Invoker("tryToAdvancePlaylist")
    void musicplayer$tryToAdvancePlaylist(boolean replayCurrentSongOtherwise);
}