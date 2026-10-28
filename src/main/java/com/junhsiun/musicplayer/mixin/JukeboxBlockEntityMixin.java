package com.junhsiun.musicplayer.mixin;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxBlockEntityMixin {
    @Redirect(
            method = "findNextPlaylistPosition",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/JukeboxBlockEntity;isMusicDisc(Lnet/minecraft/world/item/ItemStack;)Z"
            )
    )
    private static boolean musicplayer$acceptCustomDiscInPlaylist(ItemStack stack) {
        return stack.has(DataComponents.JUKEBOX_PLAYABLE) || MusicDiscHelper.isMusicPlayerDisc(stack);
    }

    @Redirect(
            method = "canPlaceItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/JukeboxBlockEntity;isMusicDisc(Lnet/minecraft/world/item/ItemStack;)Z"
            )
    )
    private static boolean musicplayer$acceptCustomDiscInsertion(ItemStack stack) {
        return stack.has(DataComponents.JUKEBOX_PLAYABLE) || MusicDiscHelper.isMusicPlayerDisc(stack);
    }

    @Inject(method = "setTheItem", at = @At("HEAD"))
    private void musicplayer$stopCustomPlaybackBeforeItemChange(ItemStack itemStack, CallbackInfo ci) {
        if (MusicDiscHelper.isMusicPlayerDisc(itemStack)) {
            return;
        }
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (blockEntity.getLevel() instanceof ServerLevel serverLevel) {
            MusicPlayerMod.jukeboxService().stopCustomPlayback(serverLevel, blockEntity.getBlockPos());
        }
    }

    @Inject(method = "setTheItem", at = @At("TAIL"))
    private void musicplayer$syncCustomDiscPlayback(ItemStack itemStack, CallbackInfo ci) {
        if (!MusicDiscHelper.isMusicPlayerDisc(itemStack)) {
            return;
        }
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (blockEntity.getLevel() instanceof ServerLevel serverLevel) {
            MusicPlayerMod.jukeboxService().handleJukeboxItemChanged(serverLevel, blockEntity.getBlockPos(), itemStack);
        }
    }
}