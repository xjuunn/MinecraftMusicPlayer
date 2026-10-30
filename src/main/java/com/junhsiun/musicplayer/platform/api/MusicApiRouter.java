package com.junhsiun.musicplayer.platform.api;

import com.junhsiun.musicplayer.model.ArtistInfo;
import com.junhsiun.musicplayer.model.PlaylistInfo;
import com.junhsiun.musicplayer.model.ProgramInfo;
import com.junhsiun.musicplayer.model.RadioInfo;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.model.UserPlaylistView;
import com.junhsiun.musicplayer.platform.url.UrlUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 取数路由器：直连优先，失败自动回退后端。
 *
 * <p>策略模式 + 熔断器：
 * <ul>
 *   <li>按 {@link MusicApiMode} 选择「直连 / 后端 / 直连+回退」；</li>
 *   <li>直连连续失败达到阈值时短暂熔断，期间直接走后端，避免每次请求都先等一次失败；</li>
 *   <li>熔断冷却结束后放行一次直连做探活，成功即恢复。</li>
 * </ul>
 *
 * <p>所有方法都不阻塞调用线程：回退逻辑挂在 future 的异常回调上。
 */
public final class MusicApiRouter implements MusicApi {
    private static final Logger LOGGER = LogManager.getLogger(MusicApiRouter.class);
    /** 连续失败多少次后熔断直连。 */
    private static final int FAILURE_THRESHOLD = 3;
    /** 熔断冷却时间（毫秒）；冷却后放行一次探活。 */
    private static final long COOLDOWN_MILLIS = 30_000L;

    private final Supplier<MusicApi> directSupplier;
    private final MusicApi proxy;
    private final Supplier<MusicApiMode> modeSupplier;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenedAt = new AtomicLong();

    public MusicApiRouter(Supplier<MusicApi> directSupplier, MusicApi proxy, Supplier<MusicApiMode> modeSupplier) {
        this.directSupplier = directSupplier;
        this.proxy = proxy;
        this.modeSupplier = modeSupplier;
    }

    /** 当前生效模式，供命令展示。 */
    public MusicApiMode mode() {
        return modeSupplier.get();
    }

    /** 直连当前是否被熔断（仅用于状态展示与调试）。 */
    public boolean isDirectCircuitOpen() {
        return circuitOpenedAt.get() > 0L && System.currentTimeMillis() - circuitOpenedAt.get() < COOLDOWN_MILLIS;
    }

    /** 供「设置模式」后立即生效：清空熔断状态。 */
    public void resetHealth() {
        consecutiveFailures.set(0);
        circuitOpenedAt.set(0L);
    }

    // ── 统一调度 ────────────────────────────────────────────────────

    /**
     * 按模式执行一次调用。
     *
     * @param directCall 直连调用（入参为直连实现）
     * @param proxyCall  后端调用（入参为后端实现）
     */
    private <T> CompletableFuture<T> dispatch(Function<MusicApi, CompletableFuture<T>> directCall,
                                              Function<MusicApi, CompletableFuture<T>> proxyCall) {
        MusicApiMode mode = mode();
        if (mode == MusicApiMode.PROXY) {
            return proxyCall.apply(proxy);
        }
        // 熔断只作用于「直连 + 回退」模式：仅直连模式下管理员明确要求走直连，
        // 即使刚失败过也应继续尝试，否则会出现 30 秒内所有请求直接失败的窗口。
        if (mode.fallsBackToProxy() && isDirectCircuitOpen()) {
            return fallBack(mode, proxyCall, new MusicApiException("musicplayer.error.direct_circuit_open"));
        }
        CompletableFuture<T> direct;
        try {
            direct = directCall.apply(directSupplier.get());
        } catch (Exception exception) {
            return fallBack(mode, proxyCall, exception);
        }
        if (direct == null) {
            return fallBack(mode, proxyCall, new MusicApiException("musicplayer.error.api_request_failed"));
        }
        if (!mode.fallsBackToProxy()) {
            // 仅直连模式：不静默回退，但仍记录健康状态
            return direct.whenComplete((result, throwable) -> markHealth(throwable));
        }
        return direct.handle((result, throwable) -> {
            if (throwable == null) {
                markHealth(null);
                return CompletableFuture.completedFuture(result);
            }
            markHealth(throwable);
            return retryProxy(proxyCall, throwable);
        }).thenCompose(Function.identity());
    }

    private <T> CompletableFuture<T> fallBack(MusicApiMode mode,
                                              Function<MusicApi, CompletableFuture<T>> proxyCall,
                                              Throwable reason) {
        if (!mode.fallsBackToProxy()) {
            return failed(reason);
        }
        LOGGER.debug("直连不可用，改走后端: {}", UrlUtil.rootMessage(reason));
        return retryProxy(proxyCall, reason);
    }

    /** 构造一个已失败的 future（Java 8 没有 {@code CompletableFuture.failedFuture}）。 */
    private static <T> CompletableFuture<T> failed(Throwable reason) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(reason);
        return future;
    }

    private <T> CompletableFuture<T> retryProxy(Function<MusicApi, CompletableFuture<T>> proxyCall, Throwable reason) {
        try {
            CompletableFuture<T> proxied = proxyCall.apply(proxy);
            if (proxied == null) {
                return failed(reason);
            }
            return proxied;
        } catch (Exception exception) {
            return failed(exception);
        }
    }

    /** 记录直连健康状态：成功清零，连续失败到阈值则开启熔断。 */
    private void markHealth(Throwable throwable) {
        if (throwable == null) {
            consecutiveFailures.set(0);
            circuitOpenedAt.set(0L);
            return;
        }
        LOGGER.debug("直连请求失败: {}", UrlUtil.rootMessage(throwable));
        if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
            circuitOpenedAt.set(System.currentTimeMillis());
            consecutiveFailures.set(0);
            if (mode().fallsBackToProxy()) {
                LOGGER.warn("直连连续失败 {} 次，{} 秒内改用后端接口",
                        FAILURE_THRESHOLD, COOLDOWN_MILLIS / 1000L);
            } else {
                // 仅直连模式没有可回退的后端，提示管理员考虑切换取数模式
                LOGGER.warn("直连连续失败 {} 次，当前为仅直连模式，请检查网络或改用 auto/proxy",
                        FAILURE_THRESHOLD);
            }
        }
    }

    // ── 单曲 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<TrackInfo> resolveSong(String id) {
        return dispatch(api -> api.resolveSong(id), api -> api.resolveSong(id));
    }

    @Override
    public CompletableFuture<TrackInfo> resolveRadioSong(String id) {
        return dispatch(api -> api.resolveRadioSong(id), api -> api.resolveRadioSong(id));
    }

    // ── 搜索 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<List<SearchEntry>> searchSongs(String keyword) {
        return dispatch(api -> api.searchSongs(keyword), api -> api.searchSongs(keyword));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchSongs(String keyword, int page) {
        return dispatch(api -> api.searchSongs(keyword, page), api -> api.searchSongs(keyword, page));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchArtists(String keyword) {
        return dispatch(api -> api.searchArtists(keyword), api -> api.searchArtists(keyword));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchArtists(String keyword, int page) {
        return dispatch(api -> api.searchArtists(keyword, page), api -> api.searchArtists(keyword, page));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword) {
        return dispatch(api -> api.searchPlaylists(keyword), api -> api.searchPlaylists(keyword));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword, int page) {
        return dispatch(api -> api.searchPlaylists(keyword, page), api -> api.searchPlaylists(keyword, page));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchUsers(String keyword) {
        return dispatch(api -> api.searchUsers(keyword), api -> api.searchUsers(keyword));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchUsers(String keyword, int page) {
        return dispatch(api -> api.searchUsers(keyword, page), api -> api.searchUsers(keyword, page));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchRadios(String keyword) {
        return dispatch(api -> api.searchRadios(keyword), api -> api.searchRadios(keyword));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchRadios(String keyword, int page) {
        return dispatch(api -> api.searchRadios(keyword, page), api -> api.searchRadios(keyword, page));
    }

    // ── 歌单 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<PlaylistInfo> playlistDetail(String id) {
        return dispatch(api -> api.playlistDetail(id), api -> api.playlistDetail(id));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> playlistTracksPage(String playlistId, int offset, int limit) {
        return dispatch(api -> api.playlistTracksPage(playlistId, offset, limit),
                api -> api.playlistTracksPage(playlistId, offset, limit));
    }

    @Override
    public CompletableFuture<UserPlaylistView> userPlaylists(String userId) {
        return dispatch(api -> api.userPlaylists(userId), api -> api.userPlaylists(userId));
    }

    // ── 歌手 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<List<TrackInfo>> artistTopSongs(String artistId) {
        return dispatch(api -> api.artistTopSongs(artistId), api -> api.artistTopSongs(artistId));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> artistTopSongEntries(String artistId) {
        return dispatch(api -> api.artistTopSongEntries(artistId), api -> api.artistTopSongEntries(artistId));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> topArtists(int limit, int offset) {
        return dispatch(api -> api.topArtists(limit, offset), api -> api.topArtists(limit, offset));
    }

    @Override
    public CompletableFuture<ArtistInfo> artistDetail(String artistId) {
        return dispatch(api -> api.artistDetail(artistId), api -> api.artistDetail(artistId));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> artistSongsPage(String artistId, int offset, int limit) {
        return dispatch(api -> api.artistSongsPage(artistId, offset, limit),
                api -> api.artistSongsPage(artistId, offset, limit));
    }

    // ── 播客 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<RadioInfo> radioDetail(String rid) {
        return dispatch(api -> api.radioDetail(rid), api -> api.radioDetail(rid));
    }

    @Override
    public CompletableFuture<List<ProgramInfo>> radioPrograms(String rid, int limit, int offset, boolean asc) {
        return dispatch(api -> api.radioPrograms(rid, limit, offset, asc),
                api -> api.radioPrograms(rid, limit, offset, asc));
    }

    @Override
    public CompletableFuture<ProgramInfo> programDetail(String programId) {
        return dispatch(api -> api.programDetail(programId), api -> api.programDetail(programId));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> hotRadios(int limit, int offset) {
        return dispatch(api -> api.hotRadios(limit, offset), api -> api.hotRadios(limit, offset));
    }

    @Override
    public CompletableFuture<List<SearchEntry>> radioCategories() {
        return dispatch(MusicApi::radioCategories, MusicApi::radioCategories);
    }

    // ── 推荐 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<List<TrackInfo>> randomHotTracks(int count) {
        return dispatch(api -> api.randomHotTracks(count), api -> api.randomHotTracks(count));
    }
}
