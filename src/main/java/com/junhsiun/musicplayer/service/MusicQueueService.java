package com.junhsiun.musicplayer.service;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.model.LyricLine;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import com.junhsiun.musicplayer.network.MusicPlaybackReportPayload;
import com.junhsiun.musicplayer.platform.LyricService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class MusicQueueService {
    private static final long FALLBACK_TRACK_TIMEOUT_MS = 10 * 60_000L;
    private static final int PLAYLIST_BATCH_SIZE = 8;

    private final Deque<QueuedTrack> queue = new ArrayDeque<>();
    private final Deque<QueuedTrack> playlistQueue = new ArrayDeque<>();
    private final Set<UUID> optedOutPlayers = new HashSet<>();
    private final Set<UUID> voteSkipPlayers = new HashSet<>();
    private final Object requestPipelineLock = new Object();
    private final Map<String, CompletableFuture<TrackInfo>> trackCache = new LinkedHashMap<>();

    private CurrentPlayback currentPlayback;
    private boolean paused;
    private long pausedAtMillis;
    private CompletableFuture<Void> requestPipeline = CompletableFuture.completedFuture(null);
    private final LyricService lyricService = new LyricService();
    private volatile List<LyricLine> currentLyrics = List.of();
    private String globalLastSentLyricText = "";
    private String globalLastLyric = "";
    private int globalLyricRefreshCounter = 0;
    private boolean lyricFetchAttempted = false;
    private long clientPositionMillis = -1L;
    private long clientPositionTime = 0L;
    private final Map<String, List<LyricLine>> jukeboxLyricsCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerJukeboxTrackId = new HashMap<>();
    private final Map<UUID, String> playerJukeboxLastLyric = new HashMap<>();
    private final Map<UUID, String> playerJukeboxSentLyricText = new HashMap<>();
    private final Map<UUID, Integer> playerJukeboxRefresh = new HashMap<>();
    private final Map<UUID, Boolean> lyricsPreference = new HashMap<>();
    private LyricsPreferenceState lyricsPreferenceState;

    private String playlistId = "";
    private int playlistTrackIndex = 0;
    private int playlistTotalTracks = 0;
    private UUID playlistRequesterId = null;
    private String playlistRequesterName = "";
    private boolean playlistMode = false;

    // ── Public query methods ─────────────────────────────────────────

    public boolean isPlaying() {
        return currentPlayback != null;
    }

    public int queuedCount() {
        return queue.size();
    }

    public int playlistQueuedCount() {
        return playlistQueue.size();
    }

    public int playlistRemainingCount() {
        return playlistMode ? playlistTotalTracks - playlistTrackIndex + playlistQueue.size() : 0;
    }

    public boolean isPlaylistMode() {
        return playlistMode;
    }

    public TrackInfo currentTrack() {
        return currentPlayback == null ? null : currentPlayback.track();
    }

    public long playbackElapsedMillis() {
        if (currentPlayback == null) return 0L;
        if (paused) return Math.max(0L, pausedAtMillis);
        return Math.max(0L, System.currentTimeMillis() - currentPlayback.startedAt());
    }

    public long playbackDurationMillis() {
        return currentPlayback == null ? 0L : currentPlayback.track().durationMillis();
    }

    public boolean isPaused() {
        return paused;
    }

    public void seek(MinecraftServer server, int deltaSeconds) {
        if (currentPlayback == null) return;
        long duration = playbackDurationMillis();
        if (duration <= 0L) return;
        long elapsed = paused ? pausedAtMillis : Math.max(0L, System.currentTimeMillis() - currentPlayback.startedAt());
        long newElapsed = Math.max(0L, Math.min(duration, elapsed + deltaSeconds * 1000L));
        long now = System.currentTimeMillis();
        if (paused) {
            pausedAtMillis = newElapsed;
            return;
        }
        long timeout = Math.min(currentPlayback.expectedEndAt() - currentPlayback.startedAt() + now - newElapsed, duration + 60_000L);
        currentPlayback = new CurrentPlayback(currentPlayback.track(), now - newElapsed, now - newElapsed + timeout, currentPlayback.requesterId(), currentPlayback.requesterName());
        clientPositionMillis = -1L;
        clientPositionTime = 0L;
        if (!paused) {
            server.getPlayerList().getPlayers().stream()
                    .filter(p -> !optedOutPlayers.contains(p.getUUID()))
                    .forEach(p -> sendPlay(p, currentPlayback.track(), newElapsed));
        }
    }

    public void pause(MinecraftServer server) {
        if (currentPlayback == null || paused) return;
        pausedAtMillis = Math.max(0L, System.currentTimeMillis() - currentPlayback.startedAt());
        paused = true;
        server.getPlayerList().getPlayers().stream()
                .filter(p -> !optedOutPlayers.contains(p.getUUID()))
                .forEach(p -> sendStop(p, "已暂停"));
    }

    public void resume(MinecraftServer server) {
        if (currentPlayback == null || !paused) return;
        long now = System.currentTimeMillis();
        long offset = pausedAtMillis;
        currentPlayback = new CurrentPlayback(currentPlayback.track(), now - offset, now - offset + currentPlayback.track().durationMillis() + 60_000L, currentPlayback.requesterId(), currentPlayback.requesterName());
        paused = false;
        pausedAtMillis = 0L;
        clientPositionMillis = -1L;
        clientPositionTime = 0L;
        server.getPlayerList().getPlayers().stream()
                .filter(p -> !optedOutPlayers.contains(p.getUUID()))
                .forEach(p -> sendPlay(p, currentPlayback.track(), offset));
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

    public List<SearchEntry> playlistEntries() {
        if (playlistQueue.isEmpty()) {
            return List.of();
        }
        return playlistQueue.stream()
                .map(track -> new SearchEntry(
                        track.songId(),
                        track.title(),
                        track.artist(),
                        "/music play song " + track.songId(),
                        track.artistCommand()
                ))
                .toList();
    }

    // ── Tick / lifecycle ───────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        try {
            tickInner(server);
        } catch (Exception e) {
            MusicPlayerMod.LOGGER.error("MusicQueueService tick 异常", e);
        }
    }

    private void tickInner(MinecraftServer server) {
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        long now = System.currentTimeMillis();

        if (currentPlayback != null) {
            if (!paused && config.autoAdvance && now >= currentPlayback.expectedEndAt()) {
                advance(server, "");
                return;
            }
            if (currentLyrics.isEmpty() && !lyricFetchAttempted) {
                lyricFetchAttempted = true;
                lyricService.fetchLyrics(currentPlayback.track().id())
                        .thenAccept(lines -> {
                            if (!lines.isEmpty()) {
                                currentLyrics = lines;
                                MusicPlayerMod.LOGGER.info("歌词已加载: {} 行", lines.size());
                            }
                        })
                        .exceptionally(ex -> {
                            MusicPlayerMod.LOGGER.warn("歌词获取失败: {}", currentPlayback.track().id(), ex);
                            return null;
                        });
            }
        }

        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (optedOutPlayers.contains(player.getUUID())) continue;
            Boolean pref = lyricsPreference.get(player.getUUID());
            boolean playerWantsLyrics;
            if (pref != null) {
                playerWantsLyrics = pref;
            } else {
                playerWantsLyrics = currentPlayback != null
                        || MusicPlayerMod.jukeboxService().isPlayerListening(player);
            }
            if (!playerWantsLyrics) continue;

            boolean nearJukebox = MusicPlayerMod.jukeboxService().isPlayerListening(player);
            String overlayText = "";

            if (nearJukebox) {
                String jtId = MusicPlayerMod.jukeboxService().getJukeboxTrackIdForPlayer(player);
                if (!jtId.isBlank()) {
                    if (!jtId.equals(playerJukeboxTrackId.get(player.getUUID()))) {
                        playerJukeboxTrackId.put(player.getUUID(), jtId);
                        if (!jukeboxLyricsCache.containsKey(jtId)) {
                            lyricService.fetchLyrics(jtId)
                                    .thenAccept(lines -> {
                                        if (!lines.isEmpty()) jukeboxLyricsCache.put(jtId, lines);
                                    })
                                    .exceptionally(ex -> {
                                        MusicPlayerMod.LOGGER.warn("唱片机歌词获取失败: {}", jtId, ex);
                                        return null;
                                    });
                        }
                    }

                    List<LyricLine> jLines = jukeboxLyricsCache.get(jtId);
                    if (jLines != null && !jLines.isEmpty()) {
                        long jElapsed = MusicPlayerMod.jukeboxService().getJukeboxElapsedMillisForPlayer(player);
                        LyricLine jLine = LyricService.findCurrentLine(jLines, jElapsed);

                        String jText;
                        if (jLine != null) {
                            jText = jLine.text();
                            playerJukeboxLastLyric.put(player.getUUID(), jText);
                        } else {
                            jText = playerJukeboxLastLyric.getOrDefault(player.getUUID(), "");
                        }

                        String lastSent = playerJukeboxSentLyricText.getOrDefault(player.getUUID(), "");
                        int counter = playerJukeboxRefresh.merge(player.getUUID(), 0, (old, v) -> old + 1);
                        if (counter >= 5) {
                            playerJukeboxRefresh.put(player.getUUID(), 0);
                            overlayText = jText;
                        }
                        if (!jText.equals(lastSent)) {
                            playerJukeboxSentLyricText.put(player.getUUID(), jText);
                            playerJukeboxRefresh.put(player.getUUID(), 0);
                            overlayText = jText;
                        }
                    }
                }
            } else {
                playerJukeboxTrackId.remove(player.getUUID());
                playerJukeboxLastLyric.remove(player.getUUID());
                playerJukeboxSentLyricText.remove(player.getUUID());
                playerJukeboxRefresh.remove(player.getUUID());

                if (currentPlayback == null || currentLyrics.isEmpty()) {
                    player.sendOverlayMessage(Component.literal(""));
                    continue;
                }

                long elapsed = getElapsedMillis();
                LyricLine line = LyricService.findCurrentLine(currentLyrics, elapsed);

                String lyricText;
                if (line != null) {
                    lyricText = line.text();
                    globalLastLyric = lyricText;
                } else {
                    lyricText = globalLastLyric;
                }

                if (++globalLyricRefreshCounter >= 5) {
                    globalLyricRefreshCounter = 0;
                    overlayText = lyricText;
                }
                if (!lyricText.equals(globalLastSentLyricText)) {
                    globalLastSentLyricText = lyricText;
                    globalLyricRefreshCounter = 0;
                    overlayText = lyricText;
                    if (!lyricText.isEmpty()) globalLastLyric = lyricText;
                }
            }

            if (!overlayText.isEmpty()) {
                player.sendOverlayMessage(Component.literal("♫ " + overlayText).withStyle(ChatFormatting.AQUA));
            }
        }
    }

    private long getElapsedMillis() {
        long monotonic = currentPlayback != null
                ? System.currentTimeMillis() - currentPlayback.startedAt()
                : 0L;
        if (clientPositionMillis < 0) {
            return monotonic;
        }
        long clientBased = clientPositionMillis + (System.currentTimeMillis() - clientPositionTime);
        return Math.max(monotonic, clientBased);
    }

    public boolean isLyricsEnabled(ServerPlayer player) {
        Boolean pref = lyricsPreference.get(player.getUUID());
        return pref != null ? pref : false;
    }

    public void toggleLyrics(ServerPlayer player, boolean enabled) {
        if (enabled) {
            lyricsPreference.put(player.getUUID(), true);
        } else {
            lyricsPreference.put(player.getUUID(), false);
            player.sendOverlayMessage(Component.literal(""));
        }
        if (lyricsPreferenceState != null) {
            lyricsPreferenceState.setEnabled(player.getUUID(), enabled);
        }
    }

    public boolean toggleLyrics(ServerPlayer player) {
        Boolean current = lyricsPreference.get(player.getUUID());
        if (current != null && current) {
            lyricsPreference.put(player.getUUID(), false);
            player.sendOverlayMessage(Component.literal(""));
            if (lyricsPreferenceState != null) {
                lyricsPreferenceState.setEnabled(player.getUUID(), false);
            }
            return false;
        } else {
            lyricsPreference.put(player.getUUID(), true);
            if (lyricsPreferenceState != null) {
                lyricsPreferenceState.setEnabled(player.getUUID(), true);
            }
            return true;
        }
    }

    public void shutdown(MinecraftServer server) {
        stop(server, "服务器关闭，播放已停止。");
        queue.clear();
        playlistQueue.clear();
        trackCache.clear();
        voteSkipPlayers.clear();
        optedOutPlayers.clear();
        resetPlaylistState();
        synchronized (requestPipelineLock) {
            requestPipeline = CompletableFuture.completedFuture(null);
        }
    }

    public void initLyricsState(MinecraftServer server) {
        lyricsPreferenceState = server.overworld().getDataStorage()
                .computeIfAbsent(LyricsPreferenceState.TYPE);
    }

    // ── Player join / leave / report ─────────────────────────────────

    public void handleJoin(ServerPlayer player) {
        if (lyricsPreferenceState != null && lyricsPreferenceState.isEnabled(player.getUUID())) {
            lyricsPreference.put(player.getUUID(), true);
        }
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
        if (payload == null || optedOutPlayers.contains(player.getUUID())) {
            return;
        }
        if (payload.trackId() == null || payload.trackId().isBlank()) {
            return;
        }

        if (currentPlayback == null || !payload.trackId().equals(currentPlayback.track().id())) {
            if ("position".equals(payload.action())) {
                try {
                    long pos = Long.parseLong(payload.message());
                    if (pos >= 0) {
                        MusicPlayerMod.jukeboxService().handlePositionReport(player, payload.trackId(), pos);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return;
        }

        if ("ended".equals(payload.action())) {
            advance(server, "");
            return;
        }
        if ("failed".equals(payload.action())) {
            String message = payload.message() == null || payload.message().isBlank()
                    ? "当前歌曲播放失败，正在切换到下一首。"
                    : "当前歌曲播放失败，正在切换到下一首。原因: " + payload.message();
            advance(server, message);
        }
        if ("position".equals(payload.action())) {
            try {
                long pos = Long.parseLong(payload.message());
                if (pos >= 0) {
                    clientPositionMillis = pos;
                    clientPositionTime = System.currentTimeMillis();
                }
            } catch (NumberFormatException ignored) {
            }
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
        sendStop(player, "你已退出当前播放。");
    }

    // ── Request song ─────────────────────────────────────────────────

    public void requestSong(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, String songId) {
        if (!MusicPlayerConfigManager.get().allowSongRequest) {
            source.sendFailure(Component.literal("管理员已禁用歌曲点播。"));
            return;
        }
        if (queue.size() >= MusicPlayerConfigManager.get().maxQueueSize) {
            source.sendFailure(Component.literal("单点队列已满，请稍后再试。"));
            return;
        }
        if (isTrackActiveOrQueued(songId)) {
            source.sendSuccess(() -> Component.literal("该歌曲正在播放或已在队列中。")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        enqueueRequest(() -> resolveTrack(songId).handle((track, throwable) -> {
            server.execute(() -> {
                if (throwable != null) {
                    source.sendFailure(Component.literal("点播失败: " + rootMessage(throwable)));
                    return;
                }
                enqueueOrStart(server, source, requester, track);
            });
            return null;
        }));
    }

    // ── Playlist mode ─────────────────────────────────────────────────

    public void requestPlaylist(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, String playlistId) {
        if (!MusicPlayerConfigManager.get().allowPlaylistRequest) {
            source.sendFailure(Component.literal("管理员已禁用歌单点播。"));
            return;
        }
        enqueueRequest(() -> MusicPlayerMod.netease().playlistDetail(playlistId).handle((playlist, throwable) -> {
            server.execute(() -> {
                if (throwable != null) {
                    source.sendFailure(Component.literal("加载歌单失败: " + rootMessage(throwable)));
                    return;
                }
                if (playlist.trackCount() <= 0) {
                    source.sendFailure(Component.literal("该歌单没有可播放的歌曲。"));
                    return;
                }

                // Stop current playback, clear everything
                stop(server, "");
                queue.clear();
                playlistQueue.clear();
                resetPlaylistState();

                // Setup playlist mode
                this.playlistId = playlistId;
                playlistTotalTracks = playlist.trackCount();
                playlistTrackIndex = 0;
                playlistRequesterId = requester.getUUID();
                playlistRequesterName = requester.getGameProfile().name();
                playlistMode = true;

                MusicPlayerMod.LOGGER.info("歌单模式启动: [{}] ID={}, 共 {} 首",
                        playlist.title(), playlistId, playlistTotalTracks);

                // Load first track and start playing
                loadPlaylistBatch(server, 1, () -> {
                    if (!playlistQueue.isEmpty()) {
                        QueuedTrack first = playlistQueue.removeFirst();
                        MusicPlayerMod.LOGGER.info("歌单首曲开始播放: {} - {} (1/{})",
                                first.title(), first.artist(), playlistTotalTracks);
                        resolveTrack(first.songId()).whenComplete((track, trackThrowable) -> server.execute(() -> {
                            if (trackThrowable != null) {
                                MusicPlayerMod.LOGGER.warn("歌单首曲加载失败: {}", rootMessage(trackThrowable));
                                advance(server, "歌单首曲加载失败，跳过到下一首。");
                                return;
                            }
                            startTrack(server, track, first.requesterId(), first.requesterName());
                            source.sendSuccess(() -> Component.literal("歌单模式已启动: [" + playlist.title() + "]，共 " + playlistTotalTracks + " 首")
                                    .withStyle(ChatFormatting.GREEN), false);

                            // Load remaining batch in background
                            loadPlaylistBatch(server, PLAYLIST_BATCH_SIZE, null);
                        }));
                    }
                });
            });
            return null;
        }));
    }

    private void loadPlaylistBatch(MinecraftServer server, int count, Runnable onComplete) {
        int batchSize = Math.min(count, playlistTotalTracks - playlistTrackIndex);
        if (batchSize <= 0) {
            if (onComplete != null) server.execute(onComplete);
            return;
        }

        int startIndex = playlistTrackIndex;
        playlistTrackIndex += batchSize;
        MusicPlayerMod.LOGGER.info("歌单批量加载 {} 首 ({}:{}/{})", batchSize, startIndex + 1, playlistTrackIndex, playlistTotalTracks);

        MusicPlayerMod.netease().playlistTracksPage(playlistId, startIndex, batchSize)
                .whenComplete((tracks, t) -> server.execute(() -> {
                    if (t != null) {
                        MusicPlayerMod.LOGGER.warn("歌单加载失败: {}", rootMessage(t));
                        if (onComplete != null) onComplete.run();
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    CompletableFuture<Void>[] futures = new CompletableFuture[tracks.size()];
                    for (int i = 0; i < tracks.size(); i++) {
                        SearchEntry entry = tracks.get(i);
                        int trackNumber = startIndex + i + 1;
                        futures[i] = resolveTrack(entry.id()).thenAccept(track -> {
                            playlistQueue.addLast(new QueuedTrack(
                                    track.id(), track.title(), track.artist(),
                                    track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(),
                                    playlistRequesterId, playlistRequesterName
                            ));
                            MusicPlayerMod.LOGGER.info("歌单加载: {}/{} - {} - {}",
                                    trackNumber, playlistTotalTracks, track.title(), track.artist());
                        }).exceptionally(throwable -> {
                            MusicPlayerMod.LOGGER.warn("歌单加载失败: [{}/{}] - {}, 原因: {}",
                                    trackNumber, playlistTotalTracks, entry.title(), rootMessage(throwable));
                            return null;
                        });
                    }
                    CompletableFuture.allOf(futures).whenComplete((v, ex) -> {
                        MusicPlayerMod.LOGGER.info("歌单批量加载完成，当前队列 {} 首，剩余 {} 首",
                                playlistQueue.size(), playlistTotalTracks - playlistTrackIndex);
                        if (onComplete != null) server.execute(onComplete);
                    });
                }));
    }

    private void loadNextPlaylistTrack(MinecraftServer server) {
        if (!playlistMode || playlistTrackIndex >= playlistTotalTracks) {
            return;
        }

        int index = playlistTrackIndex;
        playlistTrackIndex++;
        MusicPlayerMod.netease().playlistTracksPage(playlistId, index, 1)
                .whenComplete((tracks, t) -> server.execute(() -> {
                    if (t != null || tracks.isEmpty()) return;
                    SearchEntry entry = tracks.getFirst();
                    int trackNumber = index + 1;
                    resolveTrack(entry.id()).whenComplete((track, rt) -> server.execute(() -> {
                        if (rt != null) {
                            MusicPlayerMod.LOGGER.warn("歌单预载失败: [{}/{}] - {}, 原因: {}",
                                    trackNumber, playlistTotalTracks, entry.title(), rootMessage(rt));
                            return;
                        }
                        playlistQueue.addLast(new QueuedTrack(
                                track.id(), track.title(), track.artist(),
                                track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(),
                                playlistRequesterId, playlistRequesterName
                        ));
                        MusicPlayerMod.LOGGER.info("歌单预载: {}/{} - {} - {}",
                                trackNumber, playlistTotalTracks, track.title(), track.artist());
                    }));
                }));
    }

    private void resetPlaylistState() {
        playlistId = "";
        playlistTrackIndex = 0;
        playlistTotalTracks = 0;
        playlistRequesterId = null;
        playlistRequesterName = "";
        playlistMode = false;
    }

    // ── Vote / skip / stop ───────────────────────────────────────────

    public void voteSkip(MinecraftServer server, ServerPlayer voter) {
        if (currentPlayback == null) {
            voter.sendSystemMessage(Component.literal("当前没有歌曲在播放。").withStyle(ChatFormatting.RED));
            return;
        }
        if (voter.getUUID().equals(currentPlayback.requesterId())) {
            advance(server, "点歌人跳过了自己的歌曲。");
            return;
        }
        if (!voteSkipPlayers.add(voter.getUUID())) {
            voter.sendSystemMessage(Component.literal("你已经为当前歌曲投过票了。").withStyle(ChatFormatting.YELLOW));
            return;
        }
        int activeListeners = activeListeners(server);
        int requiredVotes = Math.max(1, (int) Math.ceil(activeListeners * MusicPlayerConfigManager.get().voteSkipPercent));
        int currentVotes = voteSkipPlayers.size();
        broadcast(server, Component.literal("投票跳过: " + currentVotes + "/" + requiredVotes).withStyle(ChatFormatting.GOLD));
        if (currentVotes >= requiredVotes) {
            advance(server, "投票通过，正在切换到下一首。");
        }
    }

    public void skipNow(MinecraftServer server, CommandSourceStack source) {
        if (currentPlayback == null) {
            source.sendFailure(Component.literal("当前没有歌曲在播放。"));
            return;
        }
        advance(server, "管理员跳过了当前歌曲。");
    }

    public void stop(MinecraftServer server, String reason) {
        currentPlayback = null;
        voteSkipPlayers.clear();
        clearLyrics(server);
        playlistQueue.clear();
        resetPlaylistState();
        refreshTrackCache();
        server.getPlayerList().getPlayers().forEach(player -> sendStop(player, reason));
        if (reason != null && !reason.isBlank()) {
            broadcast(server, Component.literal(reason).withStyle(ChatFormatting.YELLOW));
        }
    }

    public void clearQueue(CommandSourceStack source) {
        queue.clear();
        refreshTrackCache();
        source.sendSuccess(() -> Component.literal("单点队列已清空。"), false);
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

    public boolean removeFromQueue(String songId) {
        if (songId == null || songId.isBlank() || queue.isEmpty()) {
            return false;
        }
        boolean removed = queue.removeIf(track -> songId.equals(track.songId()));
        if (removed) {
            refreshTrackCache();
        }
        return removed;
    }

    public void stopPlaylist(MinecraftServer server) {
        if (!playlistMode && playlistQueue.isEmpty()) {
            return;
        }
        MusicPlayerMod.LOGGER.info("歌单模式已手动停止，进度: {}/{}",
                playlistTotalTracks - playlistRemainingCount(), playlistTotalTracks);
        playlistQueue.clear();
        resetPlaylistState();
        broadcast(server, Component.literal("歌单模式已停止。").withStyle(ChatFormatting.YELLOW));
    }

    // ── Queue display ────────────────────────────────────────────────

    public List<Component> describeQueue() {
        return describeQueue(1, 10);
    }

    public List<Component> describeQueue(int page, int pageSize) {
        List<Component> lines = new ArrayList<>();
        if (currentPlayback == null) {
            lines.add(Component.literal("当前没有歌曲在播放。").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(renderCurrentTrackLine(currentPlayback.track()));
        }
        if (queue.isEmpty()) {
            if (!playlistMode) {
                lines.add(Component.literal("单点队列为空。").withStyle(ChatFormatting.GRAY));
            }
        } else {
            List<QueuedTrack> all = queue.stream().toList();
            int safePageSize = Math.max(1, pageSize);
            int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) safePageSize));
            int safePage = Math.max(1, Math.min(page, totalPages));
            int start = (safePage - 1) * safePageSize;
            int end = Math.min(all.size(), start + safePageSize);
            lines.add(Component.literal("单点队列 · 第 " + safePage + "/" + totalPages + " 页").withStyle(ChatFormatting.YELLOW));
            for (int index = start; index < end; index++) {
                QueuedTrack queuedTrack = all.get(index);
                MutableComponent line = Component.literal((index + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Messages.clickableCommand(queuedTrack.title(), "重新播放这首歌曲", "/music play song " + queuedTrack.songId(), ChatFormatting.GREEN))
                        .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
                if (queuedTrack.artistCommand() != null && !queuedTrack.artistCommand().isBlank()) {
                    line.append(Messages.clickableCommand(queuedTrack.artist(), "查看作者详情", queuedTrack.artistCommand(), ChatFormatting.GRAY));
                } else {
                    line.append(Component.literal(queuedTrack.artist()).withStyle(ChatFormatting.GRAY));
                }
                lines.add(line);
            }
        }
        if (playlistMode) {
            int remaining = playlistRemainingCount();
            lines.add(Component.literal("歌单模式 · 剩余 " + remaining + " 首").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    public Component describeNowPlaying() {
        if (currentPlayback == null) {
            return Component.literal("当前没有歌曲在播放。").withStyle(ChatFormatting.GRAY);
        }
        return renderCurrentTrackLine(currentPlayback.track());
    }

    // ── Internal queue operations ─────────────────────────────────────

    private void enqueueOrStart(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, TrackInfo track) {
        if (isTrackActiveOrQueued(track.id())) {
            source.sendSuccess(() -> Component.literal("该歌曲正在播放或已在队列中。").withStyle(ChatFormatting.YELLOW), false);
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
            broadcast(server, Component.literal(requester.getGameProfile().name() + " 点歌: ").withStyle(ChatFormatting.GOLD)
                    .append(Messages.clickableCommand(track.title(), "重新播放这首歌曲", "/music play song " + track.id(), ChatFormatting.AQUA))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(track.artistId() != null && !track.artistId().isBlank()
                            ? Messages.clickableCommand(track.artist(), "查看作者详情", "/music view artist " + track.artistId(), ChatFormatting.GRAY)
                            : Component.literal(track.artist()).withStyle(ChatFormatting.GRAY)));
        } else {
            source.sendSuccess(() -> Component.literal("已加入队列: ").withStyle(ChatFormatting.GRAY)
                    .append(Messages.clickableCommand(track.title(), "重新播放这首歌曲", "/music play song " + track.id(), ChatFormatting.AQUA)), false);
        }
    }

    private void advance(MinecraftServer server, String reason) {
        currentPlayback = null;
        voteSkipPlayers.clear();
        clearLyrics(server);

        // Priority 1: normal queue (high-priority "macro" tasks)
        if (!queue.isEmpty()) {
            QueuedTrack next = queue.removeFirst();
            MusicPlayerMod.LOGGER.debug("advance: 从播放列表取曲 - {} - {}", next.title(), next.artist());
            advanceAndStart(server, reason, next);
            return;
        }

        // Priority 2: playlist queue (low-priority "micro" tasks)
        if (!playlistQueue.isEmpty()) {
            QueuedTrack next = playlistQueue.removeFirst();
            int consumed = playlistTrackIndex - playlistQueue.size();
            MusicPlayerMod.LOGGER.info("advance: 从歌单取曲 - {} - {} (歌单进度: {}/{})",
                    next.title(), next.artist(), consumed, playlistTotalTracks);
            // Silently load the next track from playlist
            loadNextPlaylistTrack(server);
            advanceAndStart(server, reason, next);
            return;
        }

        // Both empty
        if (playlistMode) {
            MusicPlayerMod.LOGGER.info("歌单已全部播放完毕，共 {} 首", playlistTotalTracks);
            playlistMode = false;
        }
        stop(server, reason == null ? "播放已全部完成。" : reason);
    }

    private void advanceAndStart(MinecraftServer server, String reason, QueuedTrack next) {
        refreshTrackCache();
        resolveTrack(next.songId()).whenComplete((track, throwable) -> server.execute(() -> {
            if (!server.isRunning()) {
                return;
            }
            if (throwable != null) {
                broadcast(server, Component.literal("跳过了无法播放的歌曲: " + next.title()).withStyle(ChatFormatting.RED));
                advance(server, null);
                return;
            }
            if (reason != null && !reason.isBlank()) {
                broadcast(server, Component.literal(reason).withStyle(ChatFormatting.YELLOW));
            }
            try {
                startTrack(server, track, next.requesterId(), next.requesterName());
            } catch (Exception e) {
                MusicPlayerMod.LOGGER.error("播放歌曲失败: {}", track.title(), e);
                advance(server, "播放失败，正在跳过到下一首。");
            }
        }));
    }

    private void startTrack(MinecraftServer server, TrackInfo track, UUID requesterId, String requesterName) {
        if (track.sourceUrls() == null || track.sourceUrls().isEmpty()) {
            MusicPlayerMod.LOGGER.warn("跳过无播放源的歌曲: {} - {}", track.title(), track.artist());
            advance(server, "该歌曲没有可用的播放源，已跳过。");
            return;
        }
        long now = System.currentTimeMillis();
        long timeout = track.durationMillis() > 0L
                ? Math.min(FALLBACK_TRACK_TIMEOUT_MS, track.durationMillis() + 60_000L)
                : FALLBACK_TRACK_TIMEOUT_MS;
        currentPlayback = new CurrentPlayback(track, now, now + timeout, requesterId, requesterName);
        paused = false;
        pausedAtMillis = 0L;
        voteSkipPlayers.clear();
        refreshTrackCache();
        server.getPlayerList().getPlayers().stream()
                .filter(player -> !optedOutPlayers.contains(player.getUUID()))
                .sorted(Comparator.comparing(player -> player.getGameProfile().name()))
                .forEach(player -> {
                    sendPlay(player, track, 0L);
                });
        broadcast(server, renderNowPlayingBroadcast(track));

        currentLyrics = List.of();
        globalLastSentLyricText = "";
        globalLastLyric = "";
        globalLyricRefreshCounter = 0;
        lyricFetchAttempted = true;
        lyricService.fetchLyrics(track.id())
                .thenAccept(lines -> server.execute(() -> {
                    if (!lines.isEmpty()) {
                        currentLyrics = lines;
                        MusicPlayerMod.LOGGER.info("歌词已加载: {} 行", lines.size());
                    }
                }))
                .exceptionally(ex -> {
                    MusicPlayerMod.LOGGER.warn("歌词获取失败: {}", track.id(), ex);
                    server.execute(() -> lyricFetchAttempted = false);
                    return null;
                });
    }

    private void clearLyrics(MinecraftServer server) {
        currentLyrics = List.of();
        globalLastSentLyricText = "";
        globalLastLyric = "";
        globalLyricRefreshCounter = 0;
        lyricFetchAttempted = false;
        clientPositionMillis = -1L;
        clientPositionTime = 0L;
        jukeboxLyricsCache.clear();
        playerJukeboxTrackId.clear();
        playerJukeboxLastLyric.clear();
        playerJukeboxSentLyricText.clear();
        playerJukeboxRefresh.clear();
        Component empty = Component.literal("");
        server.getPlayerList().getPlayers().stream()
                .filter(p -> !optedOutPlayers.contains(p.getUUID()))
                .forEach(p -> p.sendOverlayMessage(empty));
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
        if (queue.stream().anyMatch(track -> songId.equals(track.songId()))) {
            return true;
        }
        return playlistQueue.stream().anyMatch(track -> songId.equals(track.songId()));
    }

    // ── Network ──────────────────────────────────────────────────────

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

    // ── Render ───────────────────────────────────────────────────────

    private Component renderNowPlayingBroadcast(TrackInfo track) {
        MutableComponent line = Component.literal("正在播放: ").withStyle(ChatFormatting.GOLD)
                .append(Messages.clickableUrl(track.title(), "在浏览器中打开", Messages.NETEASE_SONG_URL + track.id(), ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
        if (track.artistId() != null && !track.artistId().isBlank()) {
            line.append(Messages.clickableCommand(track.artist(), "查看作者详情", "/music view artist " + track.artistId(), ChatFormatting.GRAY));
        } else {
            line.append(Component.literal(track.artist()).withStyle(ChatFormatting.GRAY));
        }
        line.append(Component.literal("\n"));
        if (track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
            line.append(Component.literal(" "));
            line.append(Messages.clickableUrl("[下载]", "在浏览器中打开歌曲直链", track.sourceUrls().getFirst(), ChatFormatting.GREEN));
        }
        line.append(Component.literal(" "));
        line.append(Messages.clickableCommand("[跳过]", "投票跳过当前歌曲", "/music skip", ChatFormatting.YELLOW));
        line.append(Component.literal(" "));
        line.append(Messages.clickableCommand("[队列]", "查看播放队列", "/music queue", ChatFormatting.GRAY));
        return line;
    }

    private Component renderCurrentTrackLine(TrackInfo track) {
        MutableComponent line = Component.literal("当前播放: ").withStyle(ChatFormatting.GOLD)
                .append(Messages.clickableUrl(track.title(), "在浏览器中打开", Messages.NETEASE_SONG_URL + track.id(), ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
        if (track.artistId() != null && !track.artistId().isBlank()) {
            line.append(Messages.clickableCommand(track.artist(), "查看作者详情", "/music view artist " + track.artistId(), ChatFormatting.GRAY));
        } else {
            line.append(Component.literal(track.artist()).withStyle(ChatFormatting.GRAY));
        }
        if (track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
            line.append(Component.literal(" "));
            line.append(Messages.clickableUrl("[下载]", "在浏览器中打开歌曲直链", track.sourceUrls().getFirst(), ChatFormatting.GREEN));
        }
        return line;
    }

    // ── Async pipeline ───────────────────────────────────────────────

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

    public void clearTrackCache() {
        synchronized (trackCache) {
            trackCache.clear();
        }
        MusicPlayerMod.LOGGER.info("音源缓存已清空");
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
