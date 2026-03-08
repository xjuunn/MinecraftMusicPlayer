package com.junhsiun.musicplayer.service;

import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.disc.MusicDiscHelper.DiscTrackData;
import com.junhsiun.musicplayer.network.JukeboxMusicPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JukeboxPlaybackService {
    private static final double AUDIBLE_RANGE_SQR = 64.0D * 64.0D;

    private final Map<Long, ActiveJukebox> activeJukeboxes = new HashMap<>();

    public InteractionResult tryInsertCustomDisc(ServerPlayer player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = serverLevel.getBlockState(pos);
        if (!(state.getBlock() instanceof JukeboxBlock) || state.getValue(JukeboxBlock.HAS_RECORD)) {
            return InteractionResult.PASS;
        }

        ItemStack heldStack = player.getItemInHand(hand);
        if (!MusicDiscHelper.isMusicPlayerDisc(heldStack)) {
            return InteractionResult.PASS;
        }

        DiscTrackData discData = MusicDiscHelper.read(heldStack).orElse(null);
        if (discData == null || discData.urls().isEmpty()) {
            return InteractionResult.PASS;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        if (!(blockEntity instanceof JukeboxBlockEntity jukebox)) {
            return InteractionResult.PASS;
        }

        ItemStack insertedDisc = heldStack.copyWithCount(1);
        jukebox.setSongItemWithoutPlaying(insertedDisc);
        jukebox.setChanged();
        serverLevel.setBlock(pos, state.setValue(JukeboxBlock.HAS_RECORD, true), 3);

        if (heldStack.getCount() == 1) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
            heldStack.shrink(1);
        }

        startPlayback(serverLevel, pos, discData);
        return InteractionResult.SUCCESS;
    }

    public void tick(MinecraftServer server) {
        if (activeJukeboxes.isEmpty()) {
            return;
        }

        Set<Long> expiredKeys = new HashSet<>();
        for (ActiveJukebox active : activeJukeboxes.values()) {
            ServerLevel level = server.getLevel(active.dimension());
            if (level == null || !isStillValid(level, active)) {
                stopPlayback(server, active);
                expiredKeys.add(active.key());
                continue;
            }
            syncListeners(level, active);
        }

        for (Long key : expiredKeys) {
            activeJukeboxes.remove(key);
        }
    }

    public void shutdown(MinecraftServer server) {
        activeJukeboxes.values().forEach(active -> stopPlayback(server, active));
        activeJukeboxes.clear();
    }

    private void startPlayback(ServerLevel level, BlockPos pos, DiscTrackData discData) {
        long key = pos.asLong();
        ActiveJukebox existing = activeJukeboxes.remove(key);
        if (existing != null) {
            stopPlayback(level.getServer(), existing);
        }

        ActiveJukebox active = new ActiveJukebox(
                key,
                level.dimension(),
                pos.immutable(),
                discData,
                new HashSet<>()
        );
        activeJukeboxes.put(key, active);
        syncListeners(level, active);
    }

    private boolean isStillValid(ServerLevel level, ActiveJukebox active) {
        if (!level.isLoaded(active.pos())) {
            return false;
        }
        BlockState state = level.getBlockState(active.pos());
        if (!(state.getBlock() instanceof JukeboxBlock) || !state.getValue(JukeboxBlock.HAS_RECORD)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(active.pos());
        if (!(blockEntity instanceof JukeboxBlockEntity jukebox)) {
            return false;
        }
        return MusicDiscHelper.read(jukebox.getTheItem())
                .filter(data -> !data.urls().isEmpty())
                .map(data -> data.trackId().equals(active.discData().trackId())
                        && data.title().equals(active.discData().title())
                        && data.artist().equals(active.discData().artist()))
                .orElse(false);
    }

    private void syncListeners(ServerLevel level, ActiveJukebox active) {
        Set<UUID> stillListening = new HashSet<>();
        double centerX = active.pos().getX() + 0.5D;
        double centerY = active.pos().getY() + 0.5D;
        double centerZ = active.pos().getZ() + 0.5D;

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(centerX, centerY, centerZ) > AUDIBLE_RANGE_SQR) {
                if (active.listeners().remove(player.getUUID())) {
                    sendStop(player, active.key());
                }
                continue;
            }

            stillListening.add(player.getUUID());
            if (active.listeners().add(player.getUUID())) {
                sendPlay(player, active);
            }
        }

        active.listeners().removeIf(uuid -> {
            if (stillListening.contains(uuid)) {
                return false;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                sendStop(player, active.key());
            }
            return true;
        });
    }

    private void stopPlayback(MinecraftServer server, ActiveJukebox active) {
        for (UUID listener : active.listeners()) {
            ServerPlayer player = server.getPlayerList().getPlayer(listener);
            if (player != null) {
                sendStop(player, active.key());
            }
        }
    }

    private void sendPlay(ServerPlayer player, ActiveJukebox active) {
        if (ServerPlayNetworking.canSend(player, JukeboxMusicPayload.TYPE)) {
            ServerPlayNetworking.send(player, JukeboxMusicPayload.play(
                    active.key(),
                    active.discData().urls(),
                    active.discData().title(),
                    active.discData().artist()
            ));
        }
    }

    private void sendStop(ServerPlayer player, long key) {
        if (ServerPlayNetworking.canSend(player, JukeboxMusicPayload.TYPE)) {
            ServerPlayNetworking.send(player, JukeboxMusicPayload.stop(key));
        }
    }

    private record ActiveJukebox(
            long key,
            ResourceKey<Level> dimension,
            BlockPos pos,
            DiscTrackData discData,
            Set<UUID> listeners
    ) {
    }
}
