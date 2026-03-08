package com.junhsiun.musicplayer.service;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.network.MusicControlPayload;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class MusicQueueService {
    private final Deque<QueuedTrack> queue = new ArrayDeque<>();
    private final Set<UUID> optedOutPlayers = new HashSet<>();
    private final Set<UUID> voteSkipPlayers = new HashSet<>();
    private final Object requestPipelineLock = new Object();

    private CurrentPlayback currentPlayback;
    private CompletableFuture<Void> requestPipeline = CompletableFuture.completedFuture(null);

    public boolean isPlaying() { return currentPlayback != null; }
    public int queuedCount() { return queue.size(); }
    public TrackInfo currentTrack() { return currentPlayback == null ? null : currentPlayback.track(); }
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
        if (currentPlayback == null) return;
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        if (config.autoAdvance && System.currentTimeMillis() >= currentPlayback.expectedEndAt()) {
            advance(server, "当前歌曲播放结束，自动切换到下一首。");
        }
    }

    public void shutdown(MinecraftServer server) {
        stop(server, "服务器正在关闭，已停止播放。");
        queue.clear();
        voteSkipPlayers.clear();
        optedOutPlayers.clear();
        synchronized (requestPipelineLock) {
            requestPipeline = CompletableFuture.completedFuture(null);
        }
    }

    public void handleJoin(ServerPlayer player) { if (currentPlayback != null && !optedOutPlayers.contains(player.getUUID())) sendPlay(player, currentPlayback.track()); }
    public void handleDisconnect(ServerPlayer player) { optedOutPlayers.remove(player.getUUID()); voteSkipPlayers.remove(player.getUUID()); }
    public void joinPlayer(ServerPlayer player) { optedOutPlayers.remove(player.getUUID()); if (currentPlayback != null) sendPlay(player, currentPlayback.track()); }
    public void leavePlayer(ServerPlayer player) { optedOutPlayers.add(player.getUUID()); sendStop(player, "你已退出当前播放。"); }

    public void requestSong(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, String songId) {
        if (!MusicPlayerConfigManager.get().allowSongRequest) { source.sendFailure(Component.literal("管理员已关闭点歌功能。")); return; }
        if (queue.size() >= MusicPlayerConfigManager.get().maxQueueSize) { source.sendFailure(Component.literal("播放队列已满，请稍后再试。")); return; }
        enqueueRequest(() -> MusicPlayerMod.netease().resolveSong(songId).handle((track, throwable) -> {
            server.execute(() -> {
                if (throwable != null) { source.sendFailure(Component.literal("点歌失败: " + rootMessage(throwable))); return; }
                enqueueOrStart(server, source, requester, track);
            });
            return null;
        }));
    }

    public void requestPlaylist(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, String playlistId) {
        if (!MusicPlayerConfigManager.get().allowPlaylistRequest) { source.sendFailure(Component.literal("管理员已关闭歌单点播功能。")); return; }
        enqueueRequest(() -> MusicPlayerMod.netease().playlistDetail(playlistId).handle((playlist, throwable) -> {
            server.execute(() -> {
                if (throwable != null) { source.sendFailure(Component.literal("加载歌单失败: " + rootMessage(throwable))); return; }
                if (playlist.tracks().isEmpty()) { source.sendFailure(Component.literal("这个歌单没有可播放的歌曲。")); return; }
                int added = 0; int playlistLimit = MusicPlayerConfigManager.get().playlistQueueLimit; int queueLimit = MusicPlayerConfigManager.get().maxQueueSize;
                for (SearchEntry entry : playlist.tracks()) {
                    if (added >= playlistLimit || queue.size() >= queueLimit) break;
                    queue.addLast(new QueuedTrack(entry.id(), entry.title(), entry.subtitle(), entry.subtitleCommand(), requester.getUUID(), requester.getGameProfile().name()));
                    added++;
                }
                int total = playlist.tracks().size(); int finalAdded = added;
                source.sendSuccess(() -> Component.literal("已将歌单《" + playlist.title() + "》加入队列，新增 " + finalAdded + "/" + total + " 首。").withStyle(ChatFormatting.GREEN), false);
                if (currentPlayback == null) advance(server, null);
            });
            return null;
        }));
    }

    public void voteSkip(MinecraftServer server, ServerPlayer voter) {
        if (currentPlayback == null) { voter.sendSystemMessage(Component.literal("当前没有正在播放的歌曲。").withStyle(ChatFormatting.RED)); return; }
        if (!voteSkipPlayers.add(voter.getUUID())) { voter.sendSystemMessage(Component.literal("你已经为当前歌曲投过票了。").withStyle(ChatFormatting.YELLOW)); return; }
        int activeListeners = activeListeners(server);
        int requiredVotes = Math.max(1, (int) Math.ceil(activeListeners * MusicPlayerConfigManager.get().voteSkipPercent));
        int currentVotes = voteSkipPlayers.size();
        broadcast(server, Component.literal("投票切歌: " + currentVotes + "/" + requiredVotes).withStyle(ChatFormatting.GOLD));
        if (currentVotes >= requiredVotes) advance(server, "投票通过，切换到下一首。");
    }

    public void skipNow(MinecraftServer server, CommandSourceStack source) { if (currentPlayback == null) { source.sendFailure(Component.literal("当前没有歌曲在播放。")); return; } advance(server, "管理员已切换到下一首。"); }
    public void stop(MinecraftServer server, String reason) { currentPlayback = null; voteSkipPlayers.clear(); server.getPlayerList().getPlayers().forEach(player -> sendStop(player, reason)); }
    public void clearQueue(CommandSourceStack source) { queue.clear(); source.sendSuccess(() -> Component.literal("播放队列已清空。"), false); }
    public List<Component> describeQueue() { return describeQueue(1, 10); }

    public List<Component> describeQueue(int page, int pageSize) {
        List<Component> lines = new ArrayList<>();
        if (currentPlayback == null) lines.add(Component.literal("当前没有歌曲在播放。").withStyle(ChatFormatting.GRAY));
        else {
            TrackInfo track = currentPlayback.track();
            lines.add(Component.literal("当前播放: ").withStyle(ChatFormatting.GOLD).append(Component.literal(track.title()).withStyle(ChatFormatting.AQUA)).append(Component.literal(" - " + track.artist()).withStyle(ChatFormatting.GRAY)));
        }
        if (queue.isEmpty()) { lines.add(Component.literal("队列为空。").withStyle(ChatFormatting.GRAY)); return lines; }
        List<QueuedTrack> all = queue.stream().toList();
        int safePageSize = Math.max(1, pageSize); int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) safePageSize)); int safePage = Math.max(1, Math.min(page, totalPages));
        int start = (safePage - 1) * safePageSize; int end = Math.min(all.size(), start + safePageSize);
        lines.add(Component.literal("待播队列 · 第 " + safePage + "/" + totalPages + " 页").withStyle(ChatFormatting.YELLOW));
        for (int index = start; index < end; index++) {
            QueuedTrack queuedTrack = all.get(index);
            MutableComponent line = Component.literal((index + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal(queuedTrack.title()).withStyle(ChatFormatting.GREEN)).append(Component.literal(" - " + queuedTrack.artist()).withStyle(ChatFormatting.GRAY));
            lines.add(line);
        }
        return lines;
    }

    public Component describeNowPlaying() {
        if (currentPlayback == null) return Component.literal("当前没有歌曲在播放。").withStyle(ChatFormatting.GRAY);
        TrackInfo track = currentPlayback.track();
        return Component.literal("当前播放: ").withStyle(ChatFormatting.GOLD).append(Component.literal(track.title()).withStyle(ChatFormatting.AQUA)).append(Component.literal(" - " + track.artist()).withStyle(ChatFormatting.GRAY));
    }

    private void enqueueOrStart(MinecraftServer server, CommandSourceStack source, ServerPlayer requester, TrackInfo track) {
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
        source.sendSuccess(() -> Component.literal("已加入队列: " + track.title()).withStyle(ChatFormatting.GREEN), false);
        if (MusicPlayerConfigManager.get().announceQueueChanges) broadcast(server, Component.literal(requester.getGameProfile().name() + " 点歌: ").withStyle(ChatFormatting.GOLD).append(Component.literal(track.title()).withStyle(ChatFormatting.AQUA)).append(Component.literal(" - " + track.artist()).withStyle(ChatFormatting.GRAY)));
    }

    private void advance(MinecraftServer server, String reason) {
        voteSkipPlayers.clear();
        if (queue.isEmpty()) { stop(server, reason == null ? "播放列表已结束。" : reason); return; }
        QueuedTrack next = queue.removeFirst();
        MusicPlayerMod.netease().resolveSong(next.songId()).whenComplete((track, throwable) -> server.execute(() -> {
            if (throwable != null) { broadcast(server, Component.literal("跳过不可播放的歌曲: " + next.title()).withStyle(ChatFormatting.RED)); advance(server, null); return; }
            if (reason != null && !reason.isBlank()) broadcast(server, Component.literal(reason).withStyle(ChatFormatting.YELLOW));
            startTrack(server, track);
        }));
    }

    private void startTrack(MinecraftServer server, TrackInfo track) {
        currentPlayback = new CurrentPlayback(track, System.currentTimeMillis(), System.currentTimeMillis() + Math.max(30_000L, track.durationMillis()));
        voteSkipPlayers.clear();
        server.getPlayerList().getPlayers().stream().filter(player -> !optedOutPlayers.contains(player.getUUID())).sorted(Comparator.comparing(player -> player.getGameProfile().name())).forEach(player -> sendPlay(player, track));
        broadcast(server, Component.literal("正在播放: ").withStyle(ChatFormatting.GOLD).append(Component.literal(track.title()).withStyle(ChatFormatting.AQUA)).append(Component.literal(" - " + track.artist()).withStyle(ChatFormatting.GRAY)));
    }

    private int activeListeners(MinecraftServer server) { return Math.max(1, (int) server.getPlayerList().getPlayers().stream().filter(player -> !optedOutPlayers.contains(player.getUUID())).count()); }
    private void sendPlay(ServerPlayer player, TrackInfo track) { if (ServerPlayNetworking.canSend(player, MusicControlPayload.TYPE)) ServerPlayNetworking.send(player, MusicControlPayload.play(track.sourceUrls(), track.title(), track.artist())); }
    private void sendStop(ServerPlayer player, String reason) { if (ServerPlayNetworking.canSend(player, MusicControlPayload.TYPE)) ServerPlayNetworking.send(player, MusicControlPayload.stop(reason)); }
    private void broadcast(MinecraftServer server, Component message) { server.getPlayerList().broadcastSystemMessage(message, false); }
    private void enqueueRequest(Supplier<CompletableFuture<Void>> supplier) { synchronized (requestPipelineLock) { requestPipeline = requestPipeline.handle((ignored, throwable) -> null).thenCompose(ignored -> supplier.get().exceptionally(throwable -> null)); } }
    private static String rootMessage(Throwable throwable) { Throwable current = throwable; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? current.toString() : current.getMessage(); }
    private record CurrentPlayback(TrackInfo track, long startedAt, long expectedEndAt) {}
    private record QueuedTrack(String songId, String title, String artist, String artistCommand, UUID requesterId, String requesterName) {}
}
