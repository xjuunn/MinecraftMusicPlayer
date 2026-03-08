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
            advance(server, "当前歌曲播放结束，自动切换到下一首。");
        }
    }

    public void shutdown(MinecraftServer server) {
        stop(server, "服务器正在关闭，已停止播放。");
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
            sendPlay(player, currentPlayback.track());
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
            advance(server, "当前歌曲播放结束，自动切换到下一首。");
            return;
        }
        if ("failed".equals(payload.action())) {
            String message = payload.message() == null || payload.message().isBlank()
                    ? "当前歌曲播放失败，自动切换到下一首。"
                    : "当前歌曲播放失败，自动切换到下一首。原因: " + payload.message();
            advance(server, message);
        }
    }

    public void joinPlayer(ServerPlayer player) {
        optedOutPlayers.remove(player.getUUID());
        if (currentPlayback != null) {
            sendPlay(player, currentPlayback.track());
        }
    }

    public void leavePlayer(ServerPlayer player) {
        optedOutPlayers.add(player.getUUID());
        sendStop(player, "你已退出当前播放。");
    }

    public void requestSong(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, String songId) {
        if (!MusicPlayerConfigManager.get().allowSongRequest) {
            source.sendFailure(Component.literal("管理员已关闭点歌功能。"));
            return;
        }
        if (queue.size() >= MusicPlayerConfigManager.get().maxQueueSize) {
            source.sendFailure(Component.literal("播放队列已满，请稍后再试。"));
            return;
        }
        if (isTrackActiveOrQueued(songId)) {
            source.sendSuccess(() -> Component.literal("这首歌已经在播放或队列中，无需重复点歌。").withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        enqueueRequest(() -> resolveTrack(songId).handle((track, throwable) -> {
            server.execute(() -> {
                if (throwable != null) {
                    source.sendFailure(Component.literal("点歌失败: " + rootMessage(throwable)));
                    return;
                }
                enqueueOrStart(server, source, requester, track);
            });
            return null;
        }));
    }

    public void requestPlaylist(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, String playlistId) {
        if (!MusicPlayerConfigManager.get().allowPlaylistRequest) {
            source.sendFailure(Component.literal("管理员已关闭歌单点播功能。"));
            return;
        }
        enqueueRequest(() -> MusicPlayerMod.netease().playlistDetail(playlistId).handle((playlist, throwable) -> {
            server.execute(() -> {
                if (throwable != null) {
                    source.sendFailure(Component.literal("加载歌单失败: " + rootMessage(throwable)));
                    return;
                }
                if (playlist.tracks().isEmpty()) {
                    source.sendFailure(Component.literal("这个歌单没有可播放的歌曲。"));
                    return;
                }
                int playlistLimit = MusicPlayerConfigManager.get().playlistQueueLimit;
                int queueLimit = MusicPlayerConfigManager.get().maxQueueSize;
                int total = playlist.tracks().size();
                int playableCount = Math.min(total, Math.min(playlistLimit, queueLimit + 1));
                if (playableCount <= 0) {
                    source.sendFailure(Component.literal("当前配置不允许载入这个歌单。"));
                    return;
                }

                List<SearchEntry> selectedTracks = new ArrayList<>(playlist.tracks().subList(0, playableCount));
                SearchEntry firstTrack = selectedTracks.getFirst();

                stop(server, "");
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
                        source.sendFailure(Component.literal("歌单首曲加载失败: " + rootMessage(trackThrowable)));
                        if (queue.isEmpty()) {
                            stop(server, "歌单播放模式启动失败。");
                        } else {
                            advance(server, "歌单首曲加载失败，已自动跳到下一首。");
                        }
                        return;
                    }

                    startTrack(server, track);
                    source.sendSuccess(() -> Component.literal("已切换到歌单播放模式：《" + playlist.title() + "》，将从第一首开始顺序播放。")
                            .withStyle(ChatFormatting.GREEN), false);
                    if (omittedCount > 0) {
                        source.sendSuccess(() -> Component.literal("受配置上限影响，未载入 " + omittedCount + " 首歌曲。")
                                .withStyle(ChatFormatting.YELLOW), false);
                    }
                }));
            });
            return null;
        }));
    }

    public void voteSkip(MinecraftServer server, ServerPlayer voter) {
        if (currentPlayback == null) {
            voter.sendSystemMessage(Component.literal("当前没有正在播放的歌曲。").withStyle(ChatFormatting.RED));
            return;
        }
        if (!voteSkipPlayers.add(voter.getUUID())) {
            voter.sendSystemMessage(Component.literal("你已经为当前歌曲投过票了。").withStyle(ChatFormatting.YELLOW));
            return;
        }
        int activeListeners = activeListeners(server);
        int requiredVotes = Math.max(1, (int) Math.ceil(activeListeners * MusicPlayerConfigManager.get().voteSkipPercent));
        int currentVotes = voteSkipPlayers.size();
        broadcast(server, Component.literal("投票切歌: " + currentVotes + "/" + requiredVotes).withStyle(ChatFormatting.GOLD));
        if (currentVotes >= requiredVotes) {
            advance(server, "投票通过，切换到下一首。");
        }
    }

    public void skipNow(MinecraftServer server, CommandSourceStack source) {
        if (currentPlayback == null) {
            source.sendFailure(Component.literal("当前没有歌曲在播放。"));
            return;
        }
        advance(server, "管理员已切换到下一首。");
    }

    public void stop(MinecraftServer server, String reason) {
        currentPlayback = null;
        voteSkipPlayers.clear();
        refreshTrackCache();
        server.getPlayerList().getPlayers().forEach(player -> sendStop(player, reason));
    }

    public void clearQueue(CommandSourceStack source) {
        queue.clear();
        refreshTrackCache();
        source.sendSuccess(() -> Component.literal("播放队列已清空。"), false);
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
            lines.add(Component.literal("当前没有歌曲在播放。").withStyle(ChatFormatting.GRAY));
        } else {
            TrackInfo track = currentPlayback.track();
            lines.add(Component.literal("当前播放: ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(track.title()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" - " + track.artist()).withStyle(ChatFormatting.GRAY)));
        }
        if (queue.isEmpty()) {
            lines.add(Component.literal("队列为空。").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        List<QueuedTrack> all = queue.stream().toList();
        int safePageSize = Math.max(1, pageSize);
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) safePageSize));
        int safePage = Math.max(1, Math.min(page, totalPages));
        int start = (safePage - 1) * safePageSize;
        int end = Math.min(all.size(), start + safePageSize);
        lines.add(Component.literal("待播队列 · 第 " + safePage + "/" + totalPages + " 页").withStyle(ChatFormatting.YELLOW));
        for (int index = start; index < end; index++) {
            QueuedTrack queuedTrack = all.get(index);
            MutableComponent line = Component.literal((index + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Messages.clickableCommand(queuedTrack.title(), "点击重新点播这首歌曲", "/music play song " + queuedTrack.songId(), ChatFormatting.GREEN))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
            if (queuedTrack.artistCommand() != null && !queuedTrack.artistCommand().isBlank()) {
                line.append(Messages.clickableCommand(queuedTrack.artist(), "点击查看作者详情", queuedTrack.artistCommand(), ChatFormatting.GRAY));
            } else {
                line.append(Component.literal(queuedTrack.artist()).withStyle(ChatFormatting.GRAY));
            }
            lines.add(line);
        }
        return lines;
    }

    public Component describeNowPlaying() {
        if (currentPlayback == null) {
            return Component.literal("当前没有歌曲在播放。").withStyle(ChatFormatting.GRAY);
        }
        TrackInfo track = currentPlayback.track();
        return renderCurrentTrackLine(track);
    }

    private void enqueueOrStart(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, TrackInfo track) {
        if (isTrackActiveOrQueued(track.id())) {
            source.sendSuccess(() -> Component.literal("这首歌已经在播放或队列中，无需重复点歌。").withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (currentPlayback == null) {
            startTrack(server, track);
            source.sendSuccess(() -> Component.literal("已开始播放《" + track.title() + "》。").withStyle(ChatFormatting.GREEN), false);
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
        source.sendSuccess(() -> Component.literal("已加入队列: " + track.title()).withStyle(ChatFormatting.GREEN), false);
        if (MusicPlayerConfigManager.get().announceQueueChanges) {
            broadcast(server, Component.literal(requester.getGameProfile().name() + " 点歌: ").withStyle(ChatFormatting.GOLD)
                    .append(Messages.clickableCommand(track.title(), "点击重新点播这首歌曲", "/music play song " + track.id(), ChatFormatting.AQUA))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(track.artistId() != null && !track.artistId().isBlank()
                            ? Messages.clickableCommand(track.artist(), "点击查看作者详情", "/music view artist " + track.artistId(), ChatFormatting.GRAY)
                            : Component.literal(track.artist()).withStyle(ChatFormatting.GRAY)));
        }
    }

    private void advance(MinecraftServer server, String reason) {
        voteSkipPlayers.clear();
        if (queue.isEmpty()) {
            stop(server, reason == null ? "播放列表已结束。" : reason);
            return;
        }
        QueuedTrack next = queue.removeFirst();
        refreshTrackCache();
        resolveTrack(next.songId()).whenComplete((track, throwable) -> server.execute(() -> {
            if (throwable != null) {
                broadcast(server, Component.literal("跳过不可播放的歌曲: " + next.title()).withStyle(ChatFormatting.RED));
                advance(server, null);
                return;
            }
            if (reason != null && !reason.isBlank()) {
                broadcast(server, Component.literal(reason).withStyle(ChatFormatting.YELLOW));
            }
            startTrack(server, track);
        }));
    }

    private void startTrack(MinecraftServer server, TrackInfo track) {
        long now = System.currentTimeMillis();
        long fallbackDuration = track.durationMillis() > 0L
                ? track.durationMillis() + 60_000L
                : FALLBACK_TRACK_TIMEOUT_MS;
        currentPlayback = new CurrentPlayback(track, now, now + Math.max(FALLBACK_TRACK_TIMEOUT_MS, fallbackDuration));
        voteSkipPlayers.clear();
        refreshTrackCache();
        server.getPlayerList().getPlayers().stream()
                .filter(player -> !optedOutPlayers.contains(player.getUUID()))
                .sorted(Comparator.comparing(player -> player.getGameProfile().name()))
                .forEach(player -> sendPlay(player, track));
        broadcast(server, renderNowPlayingBroadcast(track));
    }

    private int activeListeners(MinecraftServer server) {
        return Math.max(1, (int) server.getPlayerList().getPlayers().stream().filter(player -> !optedOutPlayers.contains(player.getUUID())).count());
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

    private void sendPlay(ServerPlayer player, TrackInfo track) {
        if (ServerPlayNetworking.canSend(player, MusicControlPayload.TYPE)) {
            ServerPlayNetworking.send(player, MusicControlPayload.play(track.id(), track.sourceUrls(), track.title(), track.artist()));
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
        MutableComponent line = Component.literal("正在播放: ").withStyle(ChatFormatting.GOLD)
                .append(Messages.clickableCommand(track.title(), "点击重新点播这首歌曲", "/music play song " + track.id(), ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
        if (track.artistId() != null && !track.artistId().isBlank()) {
            line.append(Messages.clickableCommand(track.artist(), "点击查看作者详情", "/music view artist " + track.artistId(), ChatFormatting.GRAY));
        } else {
            line.append(Component.literal(track.artist()).withStyle(ChatFormatting.GRAY));
        }
        if (track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
            line.append(Component.literal(" "));
            line.append(Messages.clickableUrl("[下载]", "点击在浏览器中打开当前歌曲直链", track.sourceUrls().getFirst(), ChatFormatting.GREEN));
        }
        return line;
    }

    private Component renderCurrentTrackLine(TrackInfo track) {
        MutableComponent line = Component.literal("当前播放: ").withStyle(ChatFormatting.GOLD)
                .append(Messages.clickableCommand(track.title(), "点击重新点播这首歌曲", "/music play song " + track.id(), ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
        if (track.artistId() != null && !track.artistId().isBlank()) {
            line.append(Messages.clickableCommand(track.artist(), "点击查看作者详情", "/music view artist " + track.artistId(), ChatFormatting.GRAY));
        } else {
            line.append(Component.literal(track.artist()).withStyle(ChatFormatting.GRAY));
        }
        if (track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
            line.append(Component.literal(" "));
            line.append(Messages.clickableUrl("[下载]", "点击在浏览器中打开当前歌曲直链", track.sourceUrls().getFirst(), ChatFormatting.GREEN));
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

    private record CurrentPlayback(TrackInfo track, long startedAt, long expectedEndAt) {
    }

    private record QueuedTrack(String songId, String title, String artist, String artistCommand, UUID requesterId, String requesterName) {
    }
}
