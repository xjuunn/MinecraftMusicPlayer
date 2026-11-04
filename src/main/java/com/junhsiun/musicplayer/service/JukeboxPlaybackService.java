package com.junhsiun.musicplayer.service;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.disc.MusicDiscHelper.DiscTrackData;
import com.junhsiun.musicplayer.mixin.JukeboxBlockEntityInvoker;
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
import java.util.concurrent.ConcurrentHashMap;

public final class JukeboxPlaybackService {
    /** 等不到客户端真实位置时最多等这么久就按服务端计时继续（兼容无模组客户端）。 */
    private static final long PLAYBACK_START_GRACE_MILLIS = 3_000L;

    private static final double AUDIBLE_RANGE_SQR = 64.0D * 64.0D;

    private final Map<Long, ActiveJukebox> activeJukeboxes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerPositionMillis = new HashMap<>();
    private final Map<UUID, Long> playerPositionTime = new HashMap<>();

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
        jukebox.setSongItemWithoutPlaying(insertedDisc);
        jukebox.setChanged();
        serverLevel.setBlock(pos, state.setValue(JukeboxBlock.HAS_RECORD, true), 2);

        if (heldStack.getCount() == 1) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
            heldStack.shrink(1);
        }

        MusicPlayerMod.LOGGER.info("Inserted custom music disc into jukebox at {}: trackId={}", pos, discData.trackId());
        startPlayback(serverLevel, pos, discData);
        return InteractionResult.SUCCESS;
    }

    /** 由自动化（上方容器播放列表、漏斗等）写入唱片机后同步自定义唱片播放。 */
    public void handleJukeboxItemChanged(ServerLevel level, BlockPos pos, ItemStack itemStack) {
        DiscTrackData discData = MusicDiscHelper.read(itemStack).orElse(null);
        if (discData == null || discData.urls().isEmpty()) {
            stopCustomPlayback(level, pos);
            return;
        }
        MusicPlayerMod.LOGGER.info("Loaded custom music disc into jukebox at {}: trackId={}", pos, discData.trackId());
        startPlayback(level, pos, discData);
    }

    public void stopCustomPlayback(ServerLevel level, BlockPos pos) {
        ActiveJukebox existing = activeJukeboxes.remove(pos.asLong());
        if (existing != null) {
            stopPlayback(level.getServer(), existing);
        }
    }

    /** 客户端报告自定义唱片播放结束，按原版播放列表规则切到下一张。 */
    public void handlePlaybackEnded(ServerPlayer player, String trackId) {
        if (trackId == null || trackId.isBlank() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ActiveJukebox active = activeJukeboxes.values().stream()
                .filter(aj -> aj.dimension().equals(level.dimension()))
                .filter(aj -> aj.listeners().contains(player.getUUID()))
                .filter(aj -> trackId.equals(aj.discData().trackId()))
                .findFirst()
                .orElse(null);
        if (active == null) {
            return;
        }
        if (!JukeboxBlockEntity.isInPlaylistMode(level, active.pos())) {
            // 非歌单模式：这张唱片就播这一首，播完应当收尾停止。
            // 如果只是 return，activeJukeboxes 里会一直留着这个条目，
            // isPlayerListening 恒为 true，服务端就会每 tick 把最后一句歌词
            // 重新发到 action bar 上，导致歌词永远不消失。
            MusicPlayerMod.LOGGER.info("Custom jukebox track finished at {}: trackId={}", active.pos(), trackId);
            activeJukeboxes.remove(active.key());
            stopPlayback(level.getServer(), active);
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(active.pos());
        if (!(blockEntity instanceof JukeboxBlockEntity jukebox)
                || !MusicDiscHelper.isMusicPlayerDisc(jukebox.getTheItem())) {
            return;
        }
        MusicPlayerMod.LOGGER.info("Custom jukebox track finished at {}: trackId={}", active.pos(), trackId);
        ((JukeboxBlockEntityInvoker) jukebox).musicplayer$tryToAdvancePlaylist(true);
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
                MusicPlayerMod.LOGGER.debug("Stopping custom jukebox playback on server at {}: {}", active.pos(), invalidReason);
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
        if (discData.trackId() == null || discData.trackId().isBlank()) {
            return;
        }
        if (discData.coverUrl().isBlank()) {
            // 历史唱片（封面字段为空）先单独补一次封面：整曲解析要等直链探测，
            // 慢的时候好几秒，而封面只是详情接口里的一个字段，能早得多到。
            MusicPlayerMod.netease().songCover(discData.trackId()).whenComplete((cover, throwable) ->
                    level.getServer().execute(() -> applyEarlyCover(level, pos, active, cover, throwable))
            );
        }
        MusicPlayerMod.netease().resolveSong(discData.trackId()).whenComplete((track, throwable) ->
                level.getServer().execute(() -> refreshPlaybackData(level, pos, active, discData, track, throwable))
        );
    }

    /** 封面单独到达时先推一次刷新，让客户端尽早开始下载。 */
    private void applyEarlyCover(ServerLevel level, BlockPos pos, ActiveJukebox expectedActive, String cover, Throwable throwable) {
        if (throwable != null || cover == null || cover.isBlank()) {
            return;
        }
        ActiveJukebox current = activeJukeboxes.get(pos.asLong());
        if (current == null || current != expectedActive) {
            return;
        }
        DiscTrackData previous = current.discData();
        if (cover.equals(previous.coverUrl())) {
            return;
        }
        current.setDiscData(new DiscTrackData(previous.trackId(), previous.title(), previous.artist(),
                previous.artistId(), cover, previous.urls(), previous.durationMillis()));
        pushRefresh(level, current, false);
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
        DiscTrackData resolved = resolveDiscData(original, previous, track, throwable);
        current.setDiscData(resolved);
        boolean urlsChanged = !resolved.urls().equals(previous.urls());
        if (urlsChanged
                || !resolved.coverUrl().equals(previous.coverUrl())
                || !resolved.title().equals(previous.title())
                || !resolved.artist().equals(previous.artist())) {
            pushRefresh(level, current, urlsChanged);
        }
    }

    /**
     * 把解析到的曲目信息合并进唱片数据。
     *
     * <p><b>封面只在"还没有封面"时才补。</b>接口返回的封面地址和唱片 NBT 里存的不一定是
     * 同一个字符串（CDN 节点、参数都可能不同），一旦用接口的地址覆盖已经生效的地址，
     * 客户端就得重新下载一张一模一样的图，那张图下载完之前唱片机的槽是空的——表现为封面
     * "闪一下"（有动画之后就是"收回去再滑下来"）。所以这里优先保留现有封面。</p>
     */
    private DiscTrackData resolveDiscData(DiscTrackData original, DiscTrackData previous, TrackInfo track, Throwable throwable) {
        if (throwable != null || track == null || track.sourceUrls() == null || track.sourceUrls().isEmpty()) {
            if (throwable != null) {
                MusicPlayerMod.LOGGER.warn("解析唱片曲目数据失败: {}", original.trackId(), throwable);
            }
            return original;
        }
        String coverUrl = previous.coverUrl();
        if (coverUrl.isBlank()) {
            coverUrl = original.coverUrl();
        }
        if (coverUrl.isBlank()) {
            coverUrl = track.coverUrl();
        }
        return new DiscTrackData(
                original.trackId(),
                track.title().isBlank() ? original.title() : track.title(),
                track.artist().isBlank() ? original.artist() : track.artist(),
                track.artistId().isBlank() ? original.artistId() : track.artistId(),
                coverUrl,
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
        if (jukebox.getTheItem().isEmpty()) {
            return "jukebox record item is empty";
        }
        return null;
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
                    clearPlayerPosition(player.getUUID());
                }
                continue;
            }

            stillListening.add(player.getUUID());
            if (active.listeners().add(player.getUUID())) {
                clearPlayerPosition(player.getUUID());
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
            clearPlayerPosition(uuid);
            return true;
        });
    }

    private void stopPlayback(MinecraftServer server, ActiveJukebox active) {
        for (UUID listener : active.listeners()) {
            ServerPlayer player = server.getPlayerList().getPlayer(listener);
            if (player != null) {
                sendStop(player, active.key());
            }
            clearPlayerPosition(listener);
        }
    }

    private void sendPlay(ServerPlayer player, ActiveJukebox active) {
        if (ServerPlayNetworking.canSend(player, JukeboxMusicPayload.TYPE)) {
            MusicPlayerMod.LOGGER.debug("Sending custom jukebox play to {} at {}", player.getScoreboardName(), active.pos());
            long offset;
            if (active.tryMarkFirstPlaySent()) {
                offset = 0L;
            } else {
                offset = Math.max(0L, System.currentTimeMillis() - active.startedAtMillis());
            }
            ServerPlayNetworking.send(player, JukeboxMusicPayload.play(
                    active.key(),
                    active.discData().urls(),
                    active.discData().title(),
                    active.discData().artist(),
                    active.discData().coverUrl(),
                    offset,
                    active.discData().trackId()
            ));
        }
    }

    private void sendStop(ServerPlayer player, long key) {
        if (ServerPlayNetworking.canSend(player, JukeboxMusicPayload.TYPE)) {
            MusicPlayerMod.LOGGER.debug("Sending custom jukebox stop to {} at {}", player.getScoreboardName(), BlockPos.of(key));
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
                            active.discData().coverUrl(),
                            0L,
                            active.discData().trackId()
                    ));
                } else {
                    ServerPlayNetworking.send(player, JukeboxMusicPayload.update(
                            active.key(),
                            active.discData().title(),
                            active.discData().artist(),
                            active.discData().coverUrl(),
                            active.discData().trackId()
                    ));
                }
            }
        }
    }
    public boolean isPlayerListening(ServerPlayer player) {
        return activeJukeboxes.values().stream().anyMatch(aj -> aj.listeners().contains(player.getUUID()));
    }

    public String getJukeboxTrackIdForPlayer(ServerPlayer player) {
        return activeJukeboxes.values().stream()
                .filter(aj -> aj.listeners().contains(player.getUUID()))
                .findFirst()
                .map(aj -> aj.discData().trackId())
                .orElse("");
    }

    /**
     * 唱片机当前的播放位置（毫秒），用于对齐歌词。
     *
     * <p>语音包发出去时客户端还在下载音频，这段静默期里不能让歌词跑：
     * 有客户端上报的真实位置就只信它；没有的话在宽限期内返回 0，
     * 超过宽限期才回退到服务端计时（兼容无模组客户端）。</p>
     */
    public long getJukeboxElapsedMillisForPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Long pos = playerPositionMillis.get(uuid);
        Long time = playerPositionTime.get(uuid);
        if (pos != null && time != null) {
            // 已经收到过真实位置：只信它，别用服务端计时去追
            return pos + (System.currentTimeMillis() - time);
        }
        return activeJukeboxes.values().stream()
                .filter(aj -> aj.listeners().contains(uuid))
                .findFirst()
                .map(aj -> {
                    long monotonic = System.currentTimeMillis() - aj.startedAtMillis();
                    return monotonic < PLAYBACK_START_GRACE_MILLIS ? 0L : monotonic;
                })
                .orElse(0L);
    }

    public void handlePositionReport(ServerPlayer player, String trackId, long positionMillis) {
        activeJukeboxes.values().stream()
                .filter(aj -> aj.listeners().contains(player.getUUID()))
                .filter(aj -> aj.discData().trackId().equals(trackId))
                .findFirst()
                .ifPresent(aj -> {
                    playerPositionMillis.put(player.getUUID(), positionMillis);
                    playerPositionTime.put(player.getUUID(), System.currentTimeMillis());
                });
    }

    public void clearPlayerPosition(UUID playerId) {
        playerPositionMillis.remove(playerId);
        playerPositionTime.remove(playerId);
    }

    private static final class ActiveJukebox {
        private final long key;
        private final ResourceKey<Level> dimension;
        private final BlockPos pos;
        private final Set<UUID> listeners = new HashSet<>();
        private volatile DiscTrackData discData;
        private final long startedAtMillis;
        private boolean firstPlaySent;

        private ActiveJukebox(long key, ResourceKey<Level> dimension, BlockPos pos, DiscTrackData discData) {
            this.key = key;
            this.dimension = dimension;
            this.pos = pos;
            this.discData = discData;
            this.startedAtMillis = System.currentTimeMillis();
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

        private DiscTrackData discData() {
            return discData;
        }

        private long startedAtMillis() {
            return startedAtMillis;
        }

        private synchronized boolean tryMarkFirstPlaySent() {
            if (firstPlaySent) {
                return false;
            }
            firstPlaySent = true;
            return true;
        }

        private void setDiscData(DiscTrackData discData) {
            this.discData = discData;
        }

        private Set<UUID> listeners() {
            return listeners;
        }
    }
}
