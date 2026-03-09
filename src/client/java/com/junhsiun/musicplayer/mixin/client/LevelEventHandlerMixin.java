package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.ClientJukeboxController;
import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelEventHandler.class)
public abstract class LevelEventHandlerMixin {
    @Shadow @Final private ClientLevel level;

    @Shadow
    protected abstract void stopJukeboxSong(BlockPos pos);

    @Shadow
    protected abstract void notifyNearbyEntities(Level level, BlockPos pos, boolean jukeboxPlaying);

    @Inject(method = "playJukeboxSong", at = @At("HEAD"), cancellable = true)
    private void musicplayer$skipVanillaJukeboxSound(Holder<JukeboxSong> song, BlockPos pos, CallbackInfo ci) {
        if (!ClientJukeboxController.getInstance().shouldSuppressVanillaSound(pos)) {
            return;
        }
        stopJukeboxSong(pos);
        notifyNearbyEntities(level, pos, true);
        MusicPlayerMod.LOGGER.info("Intercepted vanilla jukebox sound at {} for custom URL disc.", pos);
        ci.cancel();
    }
}
