package com.junhsiun.musicplayer.service;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.disc.MusicDiscHelper.DiscTrackData;
import com.junhsiun.musicplayer.model.TrackInfo;
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
        if (MusicDiscHelper.isPendingDisc(heldStack)) {
            return InteractionResult.FAIL;
        }
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
        jukebox.setTheItem(insertedDisc);
        jukebox.setChanged();

        if (heldStack.getCount() == 1) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
            heldStack.shrink(1);
        }

        MusicPlayerMod.LOGGER.info(
                "Inserted custom music disc into jukebox at {}: trackId={}, preservedCustomData={}",
                pos,
                discData.trackId(),
                MusicDiscHelper.isMusicPlayerDisc(jukebox.getTheItem())
        );
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
            String invalidReason = level == null ? "missing level" : getInvalidReason(level, active);
            if (invalidReason != null) {
                MusicPlayerMod.LOGGER.info("Stopping custom jukebox playback on server at {}: {}", active.pos(), invalidReason);
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
        ActiveJukebox active = startPlaybackResolved(level, pos, discData);
        if (discData.trackId() != null && !discData.trackId().isBlank()) {
            MusicPlayerMod.netease().resolveSong(discData.trackId()).whenComplete((track, throwable) ->
                    level.getServer().execute(() -> refreshPlaybackData(level, pos, active, discData, track, throwable))
            );
        }
    }

    private ActiveJukebox startPlaybackResolved(ServerLevel level, BlockPos pos, DiscTrackData discData) {
        long key = pos.asLong();
        ActiveJukebox existing = activeJukeboxes.remove(key);
        if (existing != null) {
            stopPlayback(level.getServer(), existing);
        }

        ActiveJukebox active = new ActiveJukebox(key, level.dimension(), pos.immutable(), discData);
        activeJukeboxes.put(key, active);
        syncListeners(level, active);
        return active;
    }

    private void refreshPlaybackData(ServerLevel level, BlockPos pos, ActiveJukebox expectedActive, DiscTrackData original, TrackInfo track, Throwable throwable) {
        ActiveJukebox current = activeJukeboxes.get(pos.asLong());
        if (current == null || current != expectedActive) {
            return;
        }
        DiscTrackData previous = current.discData();
        DiscTrackData resolved = resolveDiscData(original, track, throwable);
        current.setDiscData(resolved);
        boolean urlsChanged = !resolved.urls().equals(previous.urls());
        if (urlsChanged
                || !resolved.coverUrl().equals(previous.coverUrl())
                || !resolved.title().equals(previous.title())
                || !resolved.artist().equals(previous.artist())) {
            pushRefresh(level, current, urlsChanged);
        }
    }

    private DiscTrackData resolveDiscData(DiscTrackData original, TrackInfo track, Throwable throwable) {
        if (throwable != null || track == null || track.sourceUrls() == null || track.sourceUrls().isEmpty()) {
            if (throwable != null) {
                MusicPlayerMod.LOGGER.warn("??????????????????????: {}", original.trackId(), throwable);
            }
            return original;
        }
        return new DiscTrackData(
                original.trackId(),
                track.title().isBlank() ? original.title() : track.title(),
                track.artist().isBlank() ? original.artist() : track.artist(),
                track.artistId().isBlank() ? original.artistId() : track.artistId(),
                track.coverUrl().isBlank() ? original.coverUrl() : track.coverUrl(),
                track.sourceUrls(),
                track.durationMillis() > 0L ? track.durationMillis() : original.durationMillis()
        );
    }

    private String getInvalidReason(ServerLevel level, ActiveJukebox active) {
        if (!level.isLoaded(active.pos())) {
            return "chunk not loaded";
        }
        BlockState state = level.getBlockState(active.pos());
        if (!(state.getBlock() instanceof JukeboxBlock) || !state.getValue(JukeboxBlock.HAS_RECORD)) {
            return "jukebox block missing or has no record";
        }
        BlockEntity blockEntity = level.getBlockEntity(active.pos());
        if (!(blockEntity instanceof JukeboxBlockEntity jukebox)) {
            return "missing jukebox block entity";
        }
        return MusicDiscHelper.read(jukebox.getTheItem())
                .filter(data -> !data.urls().isEmpty())
                .map(data -> {
                    if (!active.sourceTrackId().isBlank() && !data.trackId().isBlank()) {
                        return data.trackId().equals(active.sourceTrackId())
                                ? null
                                : "track id changed from " + active.sourceTrackId() + " to " + data.trackId();
                    }
                    return data.title().equals(active.sourceTitle())
                            && data.artist().equals(active.sourceArtist())
                            ? null
                            : "disc metadata no longer matches source";
                })
                .orElse("jukebox item is missing custom disc data");
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
            MusicPlayerMod.LOGGER.info("Sending custom jukebox play to {} at {}", player.getScoreboardName(), active.pos());
            ServerPlayNetworking.send(player, JukeboxMusicPayload.play(
                    active.key(),
                    active.discData().urls(),
                    active.discData().title(),
                    active.discData().artist(),
                    active.discData().coverUrl()
            ));
        }
    }

    private void sendStop(ServerPlayer player, long key) {
        if (ServerPlayNetworking.canSend(player, JukeboxMusicPayload.TYPE)) {
            MusicPlayerMod.LOGGER.info("Sending custom jukebox stop to {} at {}", player.getScoreboardName(), BlockPos.of(key));
            ServerPlayNetworking.send(player, JukeboxMusicPayload.stop(key));
        }
    }

    private void pushRefresh(ServerLevel level, ActiveJukebox active, boolean urlsChanged) {
        for (UUID listener : active.listeners()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(listener);
            if (player == null || player.level() != level) {
                continue;
            }
            if (ServerPlayNetworking.canSend(player, JukeboxMusicPayload.TYPE)) {
                if (urlsChanged) {
                    ServerPlayNetworking.send(player, JukeboxMusicPayload.refresh(
                            active.key(),
                            active.discData().urls(),
                            active.discData().title(),
                            active.discData().artist(),
                            active.discData().coverUrl()
                    ));
                } else {
                    ServerPlayNetworking.send(player, JukeboxMusicPayload.update(
                            active.key(),
                            active.discData().title(),
                            active.discData().artist(),
                            active.discData().coverUrl()
                    ));
                }
            }
        }
    }
    private static final class ActiveJukebox {
        private final long key;
        private final ResourceKey<Level> dimension;
        private final BlockPos pos;
        private final String sourceTrackId;
        private final String sourceTitle;
        private final String sourceArtist;
        private final Set<UUID> listeners = new HashSet<>();
        private volatile DiscTrackData discData;

        private ActiveJukebox(long key, ResourceKey<Level> dimension, BlockPos pos, DiscTrackData discData) {
            this.key = key;
            this.dimension = dimension;
            this.pos = pos;
            this.sourceTrackId = discData.trackId() == null ? "" : discData.trackId();
            this.sourceTitle = discData.title() == null ? "" : discData.title();
            this.sourceArtist = discData.artist() == null ? "" : discData.artist();
            this.discData = discData;
        }

        private long key() {
            return key;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private BlockPos pos() {
            return pos;
        }

        private String sourceTrackId() {
            return sourceTrackId;
        }

        private String sourceTitle() {
            return sourceTitle;
        }

        private String sourceArtist() {
            return sourceArtist;
        }

        private DiscTrackData discData() {
            return discData;
        }

        private void setDiscData(DiscTrackData discData) {
            this.discData = discData;
        }

        private Set<UUID> listeners() {
            return listeners;
        }
    }
}
