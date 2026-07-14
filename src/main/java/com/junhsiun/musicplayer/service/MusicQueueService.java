package com.junhsiun.musicplayer.service;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import com.junhsiun.musicplayer.network.MusicPlaybackReportPayload;
import com.junhsiun.musicplayer.util.Messages;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class MusicQueueService {
    private static final long FALLBACK_TRACK_TIMEOUT_MS = 10 * 60_000L;

    private final Deque<QueuedTrack> queue = new ArrayDeque<>();
    private final Set<UUID> optedOutPlayers = new HashSet<>();
    private final Set<UUID> voteSkipPlayers = new HashSet<>();
    private final Object requestPipelineLock = new Object();
    private final Map<String, CompletableFuture<TrackInfo>> trackCache = new LinkedHashMap<>();

    private CurrentPlayback currentPlayback;
    private CompletableFuture<Void> requestPipeline = CompletableFuture.completedFuture(null);

    public boolean isPlaying() {
        return currentPlayback != null;
    }

    public int queuedCount() {
        return queue.size();
    }

    public TrackInfo currentTrack() {
        return currentPlayback == null ? null : currentPlayback.track();
    }

    public long playbackElapsedMillis() {
        return currentPlayback == null ? 0L : Math.max(0L, System.currentTimeMillis() - currentPlayback.startedAt());
    }

    public long playbackDurationMillis() {
        return currentPlayback == null ? 0L : currentPlayback.track().durationMillis();
    }

    public String currentRequesterName() {
        return currentPlayback == null ? "" : currentPlayback.requesterName();
    }

    public List<SearchEntry> queuedEntries(int page, int pageSize) {
        if (queue.isEmpty()) {
            return List.of();
        }
        List<QueuedTrack> all = queue.stream().toList();
        int safePageSize = Math.max(1, pageSize);
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) safePageSize));
        int safePage = Math.max(1, Math.min(page, totalPages));
        int start = (safePage - 1) * safePageSize;
        int end = Math.min(all.size(), start + safePageSize);
        List<SearchEntry> entries = new ArrayList<>(end - start);
        for (int index = start; index < end; index++) {
            QueuedTrack queuedTrack = all.get(index);
            entries.add(new SearchEntry(
                    queuedTrack.songId(),
                    queuedTrack.title(),
                    queuedTrack.artist(),
                    "/music play song " + queuedTrack.songId(),
                    queuedTrack.artistCommand()
            ));
        }
        return entries;
    }

    public void tick(MinecraftServer server) {
        if (currentPlayback == null) {
            return;
        }
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        if (config.autoAdvance && System.currentTimeMillis() >= currentPlayback.expectedEndAt()) {
            advance(server, "");
        }
    }

    public void shutdown(MinecraftServer server) {
        stop(server, "Server is shutting down. Playback stopped.");
        queue.clear();
        trackCache.clear();
        voteSkipPlayers.clear();
        optedOutPlayers.clear();
        synchronized (requestPipelineLock) {
            requestPipeline = CompletableFuture.completedFuture(null);
        }
    }

    public void handleJoin(ServerPlayer player) {
        if (currentPlayback != null && !optedOutPlayers.contains(player.getUUID())) {
            long offset = Math.max(0L, System.currentTimeMillis() - currentPlayback.startedAt());
            sendPlay(player, currentPlayback.track(), offset);
        }
    }

    public void refreshCacheSettings() {
        refreshTrackCache();
    }

    public void handleDisconnect(ServerPlayer player) {
        optedOutPlayers.remove(player.getUUID());
        voteSkipPlayers.remove(player.getUUID());
    }

    public void handlePlaybackReport(MinecraftServer server, ServerPlayer player, MusicPlaybackReportPayload payload) {
        if (payload == null || currentPlayback == null) {
            return;
        }
        if (optedOutPlayers.contains(player.getUUID())) {
            return;
        }
        if (payload.trackId() == null || payload.trackId().isBlank()) {
            return;
        }
        if (!payload.trackId().equals(currentPlayback.track().id())) {
            return;
        }

        if ("ended".equals(payload.action())) {
            advance(server, "");
            return;
        }
        if ("failed".equals(payload.action())) {
            String message = payload.message() == null || payload.message().isBlank()
                    ? "Current track failed. Switching to the next track."
                    : "Current track failed. Switching to the next track. Reason: " + payload.message();
            advance(server, message);
        }
    }

    public void joinPlayer(ServerPlayer player) {
        optedOutPlayers.remove(player.getUUID());
        if (currentPlayback != null) {
            long offset = Math.max(0L, System.currentTimeMillis() - currentPlayback.startedAt());
            sendPlay(player, currentPlayback.track(), offset);
        }
    }

    public void leavePlayer(ServerPlayer player) {
        optedOutPlayers.add(player.getUUID());
        sendStop(player, "You left the current playback.");
    }

    public void requestSong(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, String songId) {
        if (!MusicPlayerConfigManager.get().allowSongRequest) {
            source.sendFailure(Component.literal("Song requests are disabled by the administrator."));
            return;
        }
        if (queue.size() >= MusicPlayerConfigManager.get().maxQueueSize) {
            source.sendFailure(Component.literal("The playback queue is full. Try again later."));
            return;
        }
        if (isTrackActiveOrQueued(songId)) {
            source.sendSuccess(() -> Component.literal("This track is already playing or already queued.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        enqueueRequest(() -> resolveTrack(songId).handle((track, throwable) -> {
            server.execute(() -> {
                if (throwable != null) {
                    source.sendFailure(Component.literal("Failed to request the track: " + rootMessage(throwable)));
                    return;
                }
                enqueueOrStart(server, source, requester, track);
            });
            return null;
        }));
    }

    public void requestPlaylist(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, String playlistId) {
        if (!MusicPlayerConfigManager.get().allowPlaylistRequest) {
            source.sendFailure(Component.literal("Playlist requests are disabled by the administrator."));
            return;
        }
        enqueueRequest(() -> MusicPlayerMod.netease().playlistDetail(playlistId).handle((playlist, throwable) -> {
            server.execute(() -> {
                if (throwable != null) {
                    source.sendFailure(Component.literal("Failed to load playlist: " + rootMessage(throwable)));
                    return;
                }
                if (playlist.tracks().isEmpty()) {
                    source.sendFailure(Component.literal("This playlist has no playable tracks."));
                    return;
                }
                int playlistLimit = MusicPlayerConfigManager.get().playlistQueueLimit;
                int queueLimit = MusicPlayerConfigManager.get().maxQueueSize;
                int total = playlist.tracks().size();
                int playableCount = Math.min(total, Math.min(playlistLimit, queueLimit + 1));
                if (playableCount <= 0) {
                    source.sendFailure(Component.literal("Current configuration does not allow loading this playlist."));
                    return;
                }

                List<SearchEntry> selectedTracks = new ArrayList<>(playlist.tracks().subList(0, playableCount));
                SearchEntry firstTrack = selectedTracks.getFirst();

                currentPlayback = null;
                voteSkipPlayers.clear();
                server.getPlayerList().getPlayers().forEach(player -> sendStop(player, ""));
                queue.clear();
                for (int index = 1; index < selectedTracks.size(); index++) {
                    SearchEntry entry = selectedTracks.get(index);
                    queue.addLast(new QueuedTrack(
                            entry.id(),
                            entry.title(),
                            entry.subtitle(),
                            entry.subtitleCommand(),
                            requester.getUUID(),
                            requester.getGameProfile().name()
                    ));
                }
                refreshTrackCache();

                int omittedCount = total - playableCount;
                resolveTrack(firstTrack.id()).whenComplete((track, trackThrowable) -> server.execute(() -> {
                    if (trackThrowable != null) {
                        source.sendFailure(Component.literal("Failed to load the first track: " + rootMessage(trackThrowable)));
                        if (queue.isEmpty()) {
                            stop(server, "Playlist mode failed to start.");
                        } else {
                            advance(server, "The first playlist track failed. Skipping to the next one.");
                        }
                        return;
                    }

                    startTrack(server, track, requester.getUUID(), requester.getGameProfile().name());
                    if (omittedCount > 0) {
                        source.sendSuccess(() -> Component.literal("Some tracks were skipped due to queue limits: " + omittedCount)
                                .withStyle(ChatFormatting.YELLOW), false);
                    }
                }));
            });
            return null;
        }));
    }

    public void voteSkip(MinecraftServer server, ServerPlayer voter) {
        if (currentPlayback == null) {
            voter.sendSystemMessage(Component.literal("No track is currently playing.").withStyle(ChatFormatting.RED));
            return;
        }
        if (voter.getUUID().equals(currentPlayback.requesterId())) {
            advance(server, "Requester skipped their own track.");
            return;
        }
        if (!voteSkipPlayers.add(voter.getUUID())) {
            voter.sendSystemMessage(Component.literal("You have already voted for this track.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        int activeListeners = activeListeners(server);
        int requiredVotes = Math.max(1, (int) Math.ceil(activeListeners * MusicPlayerConfigManager.get().voteSkipPercent));
        int currentVotes = voteSkipPlayers.size();
        broadcast(server, Component.literal("Vote skip: " + currentVotes + "/" + requiredVotes).withStyle(ChatFormatting.GOLD));
        if (currentVotes >= requiredVotes) {
            advance(server, "Vote passed. Switching to the next track.");
        }
    }

    public void skipNow(MinecraftServer server, CommandSourceStack source) {
        if (currentPlayback == null) {
            source.sendFailure(Component.literal("No track is currently playing."));
            return;
        }
        advance(server, "Administrator skipped to the next track.");
    }

    public void stop(MinecraftServer server, String reason) {
        currentPlayback = null;
        voteSkipPlayers.clear();
        refreshTrackCache();
        server.getPlayerList().getPlayers().forEach(player -> sendStop(player, reason));
        if (reason != null && !reason.isBlank()) {
            broadcast(server, Component.literal(reason).withStyle(ChatFormatting.YELLOW));
        }
    }

    public void clearQueue(CommandSourceStack source) {
        queue.clear();
        refreshTrackCache();
        source.sendSuccess(() -> Component.literal("Playback queue cleared."), false);
    }

    public boolean moveQueuedTrackToFront(String songId) {
        if (songId == null || songId.isBlank() || queue.isEmpty()) {
            return false;
        }
        QueuedTrack target = null;
        for (QueuedTrack queuedTrack : queue) {
            if (songId.equals(queuedTrack.songId())) {
                target = queuedTrack;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        queue.remove(target);
        queue.addFirst(target);
        refreshTrackCache();
        return true;
    }

    public List<Component> describeQueue() {
        return describeQueue(1, 10);
    }

    public List<Component> describeQueue(int page, int pageSize) {
        List<Component> lines = new ArrayList<>();
        if (currentPlayback == null) {
            lines.add(Component.literal("Nothing is playing right now.").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(renderCurrentTrackLine(currentPlayback.track()));
        }
        if (queue.isEmpty()) {
            lines.add(Component.literal("Queue is empty.").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        List<QueuedTrack> all = queue.stream().toList();
        int safePageSize = Math.max(1, pageSize);
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) safePageSize));
        int safePage = Math.max(1, Math.min(page, totalPages));
        int start = (safePage - 1) * safePageSize;
        int end = Math.min(all.size(), start + safePageSize);
        lines.add(Component.literal("Queue · Page " + safePage + "/" + totalPages).withStyle(ChatFormatting.YELLOW));
        for (int index = start; index < end; index++) {
            QueuedTrack queuedTrack = all.get(index);
            MutableComponent line = Component.literal((index + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Messages.clickableCommand(queuedTrack.title(), "Replay this track", "/music play song " + queuedTrack.songId(), ChatFormatting.GREEN))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
            if (queuedTrack.artistCommand() != null && !queuedTrack.artistCommand().isBlank()) {
                line.append(Messages.clickableCommand(queuedTrack.artist(), "View artist details", queuedTrack.artistCommand(), ChatFormatting.GRAY));
            } else {
                line.append(Component.literal(queuedTrack.artist()).withStyle(ChatFormatting.GRAY));
            }
            lines.add(line);
        }
        return lines;
    }

    public Component describeNowPlaying() {
        if (currentPlayback == null) {
            return Component.literal("Nothing is playing right now.").withStyle(ChatFormatting.GRAY);
        }
        return renderCurrentTrackLine(currentPlayback.track());
    }

    private void enqueueOrStart(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, TrackInfo track) {
        if (isTrackActiveOrQueued(track.id())) {
            source.sendSuccess(() -> Component.literal("This track is already playing or already queued.").withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (currentPlayback == null) {
            startTrack(server, track, requester.getUUID(), requester.getGameProfile().name());
            return;
        }
        queue.addLast(new QueuedTrack(
                track.id(),
                track.title(),
                track.artist(),
                track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(),
                requester.getUUID(),
                requester.getGameProfile().name()
        ));
        refreshTrackCache();
        if (MusicPlayerConfigManager.get().announceQueueChanges) {
            broadcast(server, Component.literal(requester.getGameProfile().name() + " queued: ").withStyle(ChatFormatting.GOLD)
                    .append(Messages.clickableCommand(track.title(), "Replay this track", "/music play song " + track.id(), ChatFormatting.AQUA))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(track.artistId() != null && !track.artistId().isBlank()
                            ? Messages.clickableCommand(track.artist(), "View artist details", "/music view artist " + track.artistId(), ChatFormatting.GRAY)
                            : Component.literal(track.artist()).withStyle(ChatFormatting.GRAY)));
        } else {
            source.sendSuccess(() -> Component.literal("Queued: ").withStyle(ChatFormatting.GRAY)
                    .append(Messages.clickableCommand(track.title(), "Replay this track", "/music play song " + track.id(), ChatFormatting.AQUA)), false);
        }
    }

    private void advance(MinecraftServer server, String reason) {
        currentPlayback = null;
        voteSkipPlayers.clear();
        if (queue.isEmpty()) {
            stop(server, reason == null ? "Playback queue finished." : reason);
            return;
        }
        QueuedTrack next = queue.removeFirst();
        refreshTrackCache();
        resolveTrack(next.songId()).whenComplete((track, throwable) -> server.execute(() -> {
            if (!server.isRunning()) {
                return;
            }
            if (throwable != null) {
                broadcast(server, Component.literal("Skipped an unplayable track: " + next.title()).withStyle(ChatFormatting.RED));
                advance(server, null);
                return;
            }
            if (reason != null && !reason.isBlank()) {
                broadcast(server, Component.literal(reason).withStyle(ChatFormatting.YELLOW));
            }
            try {
                startTrack(server, track, next.requesterId(), next.requesterName());
            } catch (Exception e) {
                MusicPlayerMod.LOGGER.error("Failed to start track: {}", track.title(), e);
                advance(server, "Failed to start track. Skipping to the next one.");
            }
        }));
    }

    private void startTrack(MinecraftServer server, TrackInfo track, UUID requesterId, String requesterName) {
        long now = System.currentTimeMillis();
        long timeout = track.durationMillis() > 0L
                ? Math.min(FALLBACK_TRACK_TIMEOUT_MS, track.durationMillis() + 60_000L)
                : FALLBACK_TRACK_TIMEOUT_MS;
        currentPlayback = new CurrentPlayback(track, now, now + timeout, requesterId, requesterName);
        voteSkipPlayers.clear();
        refreshTrackCache();
        server.getPlayerList().getPlayers().stream()
                .filter(player -> !optedOutPlayers.contains(player.getUUID()))
                .sorted(Comparator.comparing(player -> player.getGameProfile().name()))
                .forEach(player -> sendPlay(player, track, 0L));
        broadcast(server, renderNowPlayingBroadcast(track));
        broadcast(server, renderNowPlayingActions(track));
    }

    private int activeListeners(MinecraftServer server) {
        return Math.max(1, (int) server.getPlayerList().getPlayers().stream()
                .filter(player -> !optedOutPlayers.contains(player.getUUID()))
                .count());
    }

    private boolean isTrackActiveOrQueued(String songId) {
        if (songId == null || songId.isBlank()) {
            return false;
        }
        if (currentPlayback != null && songId.equals(currentPlayback.track().id())) {
            return true;
        }
        return queue.stream().anyMatch(track -> songId.equals(track.songId()));
    }

    private void sendPlay(ServerPlayer player, TrackInfo track, long offsetMillis) {
        if (ServerPlayNetworking.canSend(player, MusicControlPayload.TYPE)) {
            ServerPlayNetworking.send(player, MusicControlPayload.play(track.id(), track.sourceUrls(), track.title(), track.artist(), offsetMillis));
        }
    }

    private void sendStop(ServerPlayer player, String reason) {
        if (ServerPlayNetworking.canSend(player, MusicControlPayload.TYPE)) {
            ServerPlayNetworking.send(player, MusicControlPayload.stop(reason));
        }
    }

    private void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private Component renderNowPlayingBroadcast(TrackInfo track) {
        MutableComponent line = Component.literal("Now playing: ").withStyle(ChatFormatting.GOLD)
                .append(Messages.clickableCommand(track.title(), "Replay this track", "/music play song " + track.id(), ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
        if (track.artistId() != null && !track.artistId().isBlank()) {
            line.append(Messages.clickableCommand(track.artist(), "View artist details", "/music view artist " + track.artistId(), ChatFormatting.GRAY));
        } else {
            line.append(Component.literal(track.artist()).withStyle(ChatFormatting.GRAY));
        }
        if (track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
            line.append(Component.literal(" "));
            line.append(Messages.clickableUrl("[Download]", "Open the direct track URL in your browser", track.sourceUrls().getFirst(), ChatFormatting.GREEN));
        }
        return line;
    }

    private Component renderNowPlayingActions(TrackInfo track) {
        MutableComponent line = Component.literal("");
        line.append(Messages.clickableCommand("[Vote Skip]", "Vote to skip to the next track", "/music vote next", ChatFormatting.YELLOW));
        line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(Messages.clickableCommand("[Queue]", "View the playback queue", "/music queue", ChatFormatting.GRAY));
        line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(Messages.clickableCommand("[Replay]", "Replay this track", "/music play song " + track.id(), ChatFormatting.AQUA));
        return line;
    }

    private Component renderCurrentTrackLine(TrackInfo track) {
        MutableComponent line = Component.literal("Current track: ").withStyle(ChatFormatting.GOLD)
                .append(Messages.clickableCommand(track.title(), "Replay this track", "/music play song " + track.id(), ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
        if (track.artistId() != null && !track.artistId().isBlank()) {
            line.append(Messages.clickableCommand(track.artist(), "View artist details", "/music view artist " + track.artistId(), ChatFormatting.GRAY));
        } else {
            line.append(Component.literal(track.artist()).withStyle(ChatFormatting.GRAY));
        }
        if (track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
            line.append(Component.literal(" "));
            line.append(Messages.clickableUrl("[Download]", "Open the direct track URL in your browser", track.sourceUrls().getFirst(), ChatFormatting.GREEN));
        }
        return line;
    }

    private void enqueueRequest(Supplier<CompletableFuture<Void>> supplier) {
        synchronized (requestPipelineLock) {
            requestPipeline = requestPipeline.handle((ignored, throwable) -> null)
                    .thenCompose(ignored -> supplier.get().exceptionally(throwable -> null));
        }
    }

    private CompletableFuture<TrackInfo> resolveTrack(String songId) {
        synchronized (trackCache) {
            CompletableFuture<TrackInfo> cached = trackCache.get(songId);
            if (cached != null) {
                return cached;
            }
            CompletableFuture<TrackInfo> future = MusicPlayerMod.netease().resolveSong(songId);
            trackCache.put(songId, future);
            future.whenComplete((track, throwable) -> {
                if (throwable != null) {
                    synchronized (trackCache) {
                        trackCache.remove(songId);
                    }
                }
            });
            trimTrackCacheLocked();
            return future;
        }
    }

    private void refreshTrackCache() {
        int cacheSize = Math.max(0, MusicPlayerConfigManager.get().queueCacheSize);
        synchronized (trackCache) {
            if (cacheSize == 0) {
                trackCache.clear();
                return;
            }
            List<String> keep = new ArrayList<>(cacheSize);
            for (QueuedTrack queuedTrack : queue) {
                if (keep.size() >= cacheSize) {
                    break;
                }
                keep.add(queuedTrack.songId());
            }
            trackCache.keySet().removeIf(songId -> !keep.contains(songId));
            for (String songId : keep) {
                trackCache.computeIfAbsent(songId, key -> {
                    CompletableFuture<TrackInfo> future = MusicPlayerMod.netease().resolveSong(key);
                    future.whenComplete((track, throwable) -> {
                        if (throwable != null) {
                            synchronized (trackCache) {
                                trackCache.remove(key);
                            }
                        }
                    });
                    return future;
                });
            }
            trimTrackCacheLocked();
        }
    }

    private void trimTrackCacheLocked() {
        int cacheSize = Math.max(0, MusicPlayerConfigManager.get().queueCacheSize);
        if (cacheSize == 0) {
            trackCache.clear();
            return;
        }
        while (trackCache.size() > cacheSize) {
            String firstKey = trackCache.keySet().iterator().next();
            trackCache.remove(firstKey);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private record CurrentPlayback(TrackInfo track, long startedAt, long expectedEndAt, UUID requesterId, String requesterName) {
    }

    private record QueuedTrack(String songId, String title, String artist, String artistCommand, UUID requesterId, String requesterName) {
    }
}
