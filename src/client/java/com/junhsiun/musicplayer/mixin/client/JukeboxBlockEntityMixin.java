package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.ClientJukeboxController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JukeboxBlockEntity.class)
public class JukeboxBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void musicplayer$suppressParticles(Level level, BlockPos pos, BlockState state, JukeboxBlockEntity blockEntity, CallbackInfo ci) {
        if (ClientJukeboxController.getInstance().isFinished(pos)) {
            ci.cancel();
        }
    }
}
