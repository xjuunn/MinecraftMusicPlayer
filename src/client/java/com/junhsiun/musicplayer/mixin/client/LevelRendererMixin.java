package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.client.ClientJukeboxController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Invoker("stopJukeboxSong")
    protected abstract void musicplayer$stopJukeboxSong(BlockPos pos);

    @Invoker("notifyNearbyEntities")
    protected abstract void musicplayer$notifyNearbyEntities(Level level, BlockPos pos, boolean jukeboxPlaying);

    @Inject(method = "playJukeboxSong", at = @At("HEAD"), cancellable = true)
    private void musicplayer$skipVanillaJukeboxSound(Holder<JukeboxSong> song, BlockPos pos, CallbackInfo ci) {
        if (!ClientJukeboxController.getInstance().shouldSuppressVanillaSound(pos)) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        musicplayer$stopJukeboxSong(pos);
        musicplayer$notifyNearbyEntities(level, pos, true);
        MusicPlayerMod.LOGGER.info("Intercepted vanilla jukebox sound at {} for custom URL disc.", pos);
        ci.cancel();
    }
}
