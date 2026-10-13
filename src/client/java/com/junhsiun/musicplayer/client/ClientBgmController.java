package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.util.Java8;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.sounds.Music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * 网络背景音乐核心：在原版 {@code MusicManager.startPlaying} 触发时接管播放。
 * 间隔完全由原版 {@code stopPlaying()} 计算，本类不写任何手写延时；
 * 网络不可用或连续失败时静默降级回原版音乐。
 */
public final class ClientBgmController {
    public enum Decision {
        /** 放行原版播放。 */
        PASS,
        /** 网络音乐正在播放，取消原版触发但不换歌。 */
        HOLD,
        /** 拦截原版触发并播放网络音乐。 */
        PLAY
    }

    private static final ClientBgmController INSTANCE = new ClientBgmController();
    private static final int POOL_BATCH_SIZE = 3;
    private static final int PLAYLIST_PAGE_SIZE = 100;
    private static final int MAX_TRACK_FAILURES = 3;
    private static final long FALLBACK_MILLIS = 60_000L;

    private final Object lock = new Object();
    private final Random random = new Random();
    private final List<TrackInfo> pool = new ArrayList<>();
    private final List<String> pageIds = new ArrayList<>();
    private final List<Runnable> refillWaiters = new ArrayList<>();

    private String playlistId = "";
    private String pagePlaylistId = "";
    private int pageCursor;
    private int generation;
    private boolean refillInFlight;
    private boolean bypassOnce;
    private volatile long fallbackUntil;
    /** 客户端主线程专用：连续播放失败计数。 */
    private int consecutiveFailures;

    private final ClientMusicController.BgmListener bgmListener = new ClientMusicController.BgmListener() {
        @Override
        public void onBgmEnded() {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                consecutiveFailures = 0;
                if (minecraft.getMusicManager() != null) {
                    // 间隔交给原版计算（currentMusic 为 null 时 stopPlaying 只重置倒计时）
                    minecraft.getMusicManager().stopPlaying();
                }
                ensurePoolRefill();
            });
        }

        @Override
        public void onBgmFailed(String message) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> handleTrackFailure(message));
        }
    };

    private ClientBgmController() {
    }

    public static ClientBgmController getInstance() {
        return INSTANCE;
    }

    /**
     * 接收服务端同步的歌单（空串 = 该存档关闭网络背景音乐，使用原版）。客户端主线程调用。
     */
    public void configureServer(String serverPlaylistId) {
        applyPlaylist(serverPlaylistId == null ? "" : serverPlaylistId.trim());
    }

    /**
     * 使用客户端本地歌单（主菜单 / 未连接存档时）。客户端主线程调用。
     */
    public void configureLocal(String localPlaylistId) {
        applyPlaylist(localPlaylistId == null ? "" : localPlaylistId.trim());
    }

    public void reset() {
        applyPlaylist("");
    }

    /**
     * 全局开关（设置界面）变化后调用：关闭时停止正在播放的网络背景音乐并交还原版。客户端主线程调用。
     */
    public void onGlobalToggleChanged() {
        if (!isEnabled() && ClientMusicController.getInstance().isBgmPlaying()) {
            ClientMusicController.getInstance().stop("Network BGM disabled.");
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getMusicManager() != null) {
                minecraft.getMusicManager().stopPlaying();
            }
        }
    }

    /**
     * 进入存档时调用：停止主菜单背景音乐（对齐原版行为），存档内的下一次触发按原版节奏重新开始。
     * 客户端主线程调用。
     */
    public void onEnteredWorld() {
        if (ClientMusicController.getInstance().isBgmPlaying()) {
            ClientMusicController.getInstance().stop("Entered world.");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getMusicManager() != null) {
            minecraft.getMusicManager().stopPlaying();
        }
    }

    private void applyPlaylist(String id) {
        boolean wasEnabled;
        boolean nowEnabled;
        synchronized (lock) {
            wasEnabled = isEnabledLocked();
            playlistId = id;
            nowEnabled = isEnabledLocked();
            pool.clear();
            pageIds.clear();
            refillWaiters.clear();
            pagePlaylistId = "";
            pageCursor = 0;
            generation++;
            refillInFlight = false;
            bypassOnce = false;
            fallbackUntil = 0L;
        }
        consecutiveFailures = 0;
        if (wasEnabled && !nowEnabled) {
            // 仅停止网络背景音乐本身，不影响队列/唱机播放
            if (ClientMusicController.getInstance().isBgmPlaying()) {
                ClientMusicController.getInstance().stop("BGM disabled.");
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getMusicManager() != null) {
                minecraft.getMusicManager().stopPlaying();
            }
        }
    }

    /**
     * 全局开关是客户端本地设置（设置界面），歌单为空表示关闭。
     */
    public boolean isEnabled() {
        synchronized (lock) {
            return isEnabledLocked();
        }
    }

    private boolean isEnabledLocked() {
        return MusicPlayerConfigManager.get().enableNetworkBgm && !Java8.isBlank(playlistId);
    }

    /**
     * 原版 {@code startPlaying} 被调用时的拦截决策。客户端主线程调用。
     */
    public Decision onVanillaStartRequested() {
        synchronized (lock) {
            if (bypassOnce) {
                bypassOnce = false;
                return Decision.PASS;
            }
            if (!isEnabled()) {
                return Decision.PASS;
            }
            if (fallbackUntil > System.currentTimeMillis()) {
                return Decision.PASS;
            }
            if (ClientMusicController.getInstance().isBgmPlaying()) {
                return Decision.HOLD;
            }
            if (ClientMusicController.getInstance().isBackgroundMusicPlaying()
                    || ClientJukeboxController.getInstance().isPlayerNearAnyActiveJukebox()) {
                return Decision.PASS;
            }
            return Decision.PLAY;
        }
    }

    /** 已决定拦截后调用：优先取缓存池，池空则异步补一批。客户端主线程调用。 */
    public void requestPlay(Music vanillaMusic) {
        try {
            requestPlayInternal(vanillaMusic);
        } catch (RuntimeException exception) {
            // 任何同步异常都不能传回原版 startPlaying/tick，否则整局崩溃
            MusicPlayerMod.LOGGER.warn("网络背景音乐启动异常，降级为原版", exception);
            try {
                handleFailure(vanillaMusic, exception.toString());
            } catch (RuntimeException fallbackException) {
                MusicPlayerMod.LOGGER.error("网络背景音乐降级失败", fallbackException);
            }
        }
    }

    private void requestPlayInternal(Music vanillaMusic) {
        TrackInfo track = pollPool();
        if (track != null) {
            ensurePoolRefill();
            playTrack(track);
            return;
        }
        refill(() -> {
            if (!isEnabled()) {
                playVanillaNow(vanillaMusic);
                return;
            }
            TrackInfo next = pollPool();
            if (next != null) {
                ensurePoolRefill();
                playTrack(next);
            } else {
                handleFailure(vanillaMusic, "缓存补充后仍无可用曲目");
            }
        });
    }

    private void handleTrackFailure(String message) {
        consecutiveFailures++;
        if (consecutiveFailures >= MAX_TRACK_FAILURES) {
            consecutiveFailures = 0;
            handleFailure(null, message);
            return;
        }
        TrackInfo next = pollPool();
        if (next != null) {
            playTrack(next);
            return;
        }
        refill(() -> {
            if (!isEnabled()) {
                return;
            }
            TrackInfo retry = pollPool();
            if (retry != null) {
                ensurePoolRefill();
                playTrack(retry);
            } else {
                handleFailure(null, "重试后仍无可用曲目");
            }
        });
    }

    /** 播放不可用时的静默降级：网络检测 → 需要时进入降级窗口 → 立即放行原版。 */
    private void handleFailure(Music vanillaMusic, String reason) {
        if (reason != null && !Java8.isBlank(reason)) {
            MusicPlayerMod.LOGGER.debug("网络背景音乐暂不可用，降级为原版音乐: {}", reason);
        }
        try {
            ClientNetworkChecker.check().whenComplete((status, error) -> {
                boolean offline = status == ClientNetworkChecker.Status.OFFLINE;
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> {
                    if (offline) {
                        fallbackUntil = System.currentTimeMillis() + FALLBACK_MILLIS;
                    }
                    playVanillaNow(vanillaMusic);
                });
            });
        } catch (RuntimeException exception) {
            MusicPlayerMod.LOGGER.warn("网络检测执行失败，直接降级为原版", exception);
            playVanillaNow(vanillaMusic);
        }
    }

    /** 立即放行一次原版播放（bypass 下一次拦截判断，避免递归）。客户端主线程调用。 */
    private void playVanillaNow(Music vanillaMusic) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getMusicManager() == null) {
            return;
        }
        Music situational = vanillaMusic != null ? vanillaMusic : minecraft.getSituationalMusic();
        if (situational == null) {
            return;
        }
        if (minecraft.screen instanceof LevelLoadingScreen) {
            return;
        }
        synchronized (lock) {
            bypassOnce = true;
        }
        minecraft.getMusicManager().startPlaying(situational);
    }

    private void playTrack(TrackInfo track) {
        ClientMusicController.getInstance().playBgm(
                track.id(), track.sourceUrls(), track.title(), track.artist(), bgmListener);
    }

    private TrackInfo pollPool() {
        synchronized (lock) {
            if (pool.isEmpty()) {
                return null;
            }
            return pool.remove(random.nextInt(pool.size()));
        }
    }

    private void ensurePoolRefill() {
        synchronized (lock) {
            if (pool.size() >= POOL_BATCH_SIZE || !isEnabled()) {
                return;
            }
        }
        refill(null);
    }

    /** 异步补充缓存池；waiter 在补充完成后于客户端主线程执行（成功失败都会执行）。 */
    private void refill(Runnable waiter) {
        List<Runnable> immediate = null;
        int gen;
        String wanted;
        synchronized (lock) {
            if (waiter != null) {
                refillWaiters.add(waiter);
            }
            if (!isEnabled()) {
                immediate = new ArrayList<>(refillWaiters);
                refillWaiters.clear();
            } else if (refillInFlight) {
                return;
            } else {
                refillInFlight = true;
            }
            gen = generation;
            wanted = playlistId;
        }
        if (immediate != null) {
            runWaiters(immediate);
            return;
        }
        try {
            ensurePage(wanted, gen)
                    .thenCompose(ignored -> resolveBatch(wanted, gen))
                    .whenComplete((added, error) -> completeRefill(gen, error));
        } catch (RuntimeException exception) {
            // 同步异常（如线程池不可用）：重置补货状态并让等待者走降级，不能传回原版 tick
            MusicPlayerMod.LOGGER.warn("网络背景音乐缓存补充分发失败", exception);
            List<Runnable> waiters;
            synchronized (lock) {
                if (gen != generation) {
                    return;
                }
                refillInFlight = false;
                waiters = new ArrayList<>(refillWaiters);
                refillWaiters.clear();
            }
            runWaiters(waiters);
        }
    }

    private CompletableFuture<Void> ensurePage(String pid, int gen) {
        synchronized (lock) {
            if (gen == generation && pid.equals(pagePlaylistId) && !pageIds.isEmpty()) {
                if (pageCursor >= pageIds.size()) {
                    Collections.shuffle(pageIds, random);
                    pageCursor = 0;
                }
                return CompletableFuture.completedFuture(null);
            }
        }
        return MusicPlayerMod.netease().playlistTracksPage(pid, 0, PLAYLIST_PAGE_SIZE).thenAccept(entries -> {
            List<String> ids = new ArrayList<>();
            if (entries != null) {
                for (SearchEntry entry : entries) {
                    if (entry != null && entry.id() != null && !Java8.isBlank(entry.id())) {
                        ids.add(entry.id());
                    }
                }
            }
            synchronized (lock) {
                if (gen != generation) {
                    return;
                }
                pageIds.clear();
                pageIds.addAll(ids);
                Collections.shuffle(pageIds, random);
                pageCursor = 0;
                pagePlaylistId = pid;
            }
        });
    }

    private CompletableFuture<Integer> resolveBatch(String pid, int gen) {
        List<String> chosen = new ArrayList<>();
        synchronized (lock) {
            if (gen != generation || pageIds.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            for (int i = 0; i < POOL_BATCH_SIZE; i++) {
                if (pageCursor >= pageIds.size()) {
                    Collections.shuffle(pageIds, random);
                    pageCursor = 0;
                }
                chosen.add(pageIds.get(pageCursor++));
            }
        }
        List<CompletableFuture<TrackInfo>> futures = new ArrayList<>();
        for (String id : chosen) {
            try {
                futures.add(MusicPlayerMod.netease().resolveSong(id)
                        .handle((track, error) -> error == null ? track : null));
            } catch (RuntimeException exception) {
                MusicPlayerMod.LOGGER.debug("网络背景音乐解析歌曲失败: {}", id, exception);
            }
        }
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return all.thenApply(ignored -> {
            List<TrackInfo> resolved = new ArrayList<>();
            for (CompletableFuture<TrackInfo> future : futures) {
                TrackInfo track = future.getNow(null);
                if (track != null && track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
                    resolved.add(track);
                }
            }
            if (resolved.isEmpty()) {
                return 0;
            }
            synchronized (lock) {
                if (gen != generation) {
                    return 0;
                }
                pool.addAll(resolved);
                return resolved.size();
            }
        });
    }

    private void completeRefill(int gen, Throwable error) {
        List<Runnable> waiters;
        synchronized (lock) {
            if (gen != generation) {
                return;
            }
            refillInFlight = false;
            waiters = new ArrayList<>(refillWaiters);
            refillWaiters.clear();
        }
        if (error != null) {
            MusicPlayerMod.LOGGER.debug("网络背景音乐缓存补充失败: {}", error.toString());
        }
        if (!waiters.isEmpty()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.execute(() -> runWaiters(waiters));
            }
        }
    }

    private void runWaiters(List<Runnable> waiters) {
        for (Runnable waiter : waiters) {
            try {
                waiter.run();
            } catch (RuntimeException exception) {
                MusicPlayerMod.LOGGER.warn("网络背景音乐等待回调执行失败", exception);
            }
        }
    }
}
