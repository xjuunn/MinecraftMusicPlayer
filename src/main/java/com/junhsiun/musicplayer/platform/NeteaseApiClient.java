package com.junhsiun.musicplayer.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.model.ArtistInfo;
import com.junhsiun.musicplayer.model.PlaylistInfo;
import com.junhsiun.musicplayer.model.ProgramInfo;
import com.junhsiun.musicplayer.model.RadioInfo;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.model.UserPlaylistView;
import com.junhsiun.musicplayer.platform.url.ByfunsUrlProvider;
import com.junhsiun.musicplayer.platform.url.MycelisUrlProvider;
import com.junhsiun.musicplayer.platform.url.QijieyaUrlProvider;
import com.junhsiun.musicplayer.platform.url.SongUrlResolver;
import com.junhsiun.musicplayer.platform.url.VkeysUrlProvider;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import com.junhsiun.musicplayer.util.Messages;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.TextComponent;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class NeteaseApiClient {
    private static final int DETAIL_FETCH_BATCH_SIZE = 100;
    private static final int HOT_PLAYLIST_FETCH_SIZE = 30;
    /** 随机热门歌曲：每个歌单最多拉取的曲目数，避免大歌单全量分页。 */
    private static final int RANDOM_HOT_MAX_SONGS_PER_PLAYLIST = 200;
    /** 随机热门歌曲：整体超时（秒）。 */
    private static final long RANDOM_HOT_TOTAL_TIMEOUT_SECONDS = 30;
    /** 搜索类型：网易云 cloudsearch type 参数。 */
    private static final int SEARCH_TYPE_SONG = 1;
    private static final int SEARCH_TYPE_ARTIST = 100;
    private static final int SEARCH_TYPE_PLAYLIST = 1000;
    private static final int SEARCH_TYPE_USER = 1002;
    private static final int SEARCH_TYPE_RADIO = 1009;
    private static final String USER_AGENT = "MinecraftMusicPlayer/" + com.junhsiun.musicplayer.MusicPlayerMod.VERSION;
    private static volatile ExecutorService EXECUTOR = createExecutor();
    private static volatile ExecutorService PROBE_EXECUTOR = createProbeExecutor();
    /** 音源探测超时计时器；只做定时回调，不执行业务逻辑，单个守护线程即可。 */
    private static final ScheduledExecutorService PROBE_TIMER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "musicplayer-probe-timer");
        thread.setDaemon(true);
        return thread;
    });

    private static ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "musicplayer-http");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 音源探测与歌词等次要网络任务专用线程池。
     *
     * <p>单次点歌会并行探测多个第三方音源；这些站点在弱网或不可达时通常要等到超时才返回。
     * 与 {@link #executor()} 分离后，即使探测任务堆积，搜索、歌单、歌曲详情等主流程也不会被拖慢。
     * 16 线程可同时容纳多首歌曲的探测请求，减少排队等待。
     */
    private static ExecutorService createProbeExecutor() {
        return Executors.newFixedThreadPool(16, runnable -> {
            Thread thread = new Thread(runnable, "musicplayer-probe");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 返回可用的 HTTP 线程池。退出世界时 {@code shutdownExecutor()} 会关闭静态线程池，
     * 再次进入世界后调用会自动重建，避免 {@code RejectedExecutionException}。
     */
    public static ExecutorService executor() {
        ExecutorService executor = EXECUTOR;
        if (executor == null || executor.isShutdown()) {
            synchronized (NeteaseApiClient.class) {
                executor = EXECUTOR;
                if (executor == null || executor.isShutdown()) {
                    executor = createExecutor();
                    EXECUTOR = executor;
                }
            }
        }
        return executor;
    }

    /** 返回音源探测/歌词专用线程池，懒重建规则与 {@link #executor()} 一致。 */
    public static ExecutorService probeExecutor() {
        ExecutorService executor = PROBE_EXECUTOR;
        if (executor == null || executor.isShutdown()) {
            synchronized (NeteaseApiClient.class) {
                executor = PROBE_EXECUTOR;
                if (executor == null || executor.isShutdown()) {
                    executor = createProbeExecutor();
                    PROBE_EXECUTOR = executor;
                }
            }
        }
        return executor;
    }

    /**
     * 在探测线程池中执行音源解析任务，并从任务真正开始执行时起计时超时。
     *
     * <p>{@link CompletableFuture#orTimeout} 从提交时开始计时，快速连点歌曲时，
     * 队列中尚未运行的任务会先被判定超时，导致后续点歌随机返回空直链。
     * 这里把超时计时放进工作线程，排队等待时间不计入超时。
     *
     * @param task           实际执行的阻塞任务
     * @param timeoutSeconds 执行超时（秒），从任务开始执行时计算
     */
    public static <T> CompletableFuture<T> submitProbe(Callable<T> task, int timeoutSeconds) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            probeExecutor().execute(() -> {
                ScheduledFuture<?> watchdog = null;
                try {
                    watchdog = PROBE_TIMER.schedule(
                            () -> future.completeExceptionally(new TimeoutException("probe timed out")),
                            timeoutSeconds, TimeUnit.SECONDS);
                    future.complete(task.call());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                } finally {
                    if (watchdog != null) {
                        watchdog.cancel(false);
                    }
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    /** 单曲音源解析器：Byfuns/Qijieya 优先，Mycelis 保底。 */
    private final SongUrlResolver urlResolver;
    /** 基础地址提供者；为 null 时回退到共享配置（服务端行为）。 */
    private final Supplier<String> baseUrlSupplier;

    public NeteaseApiClient() {
        this(null);
    }

    /**
     * @param baseUrlSupplier 基础地址提供者，可为 null 表示始终读取共享配置。
     *                        客户端 BGM 使用独立实例注入该提供者，避免服务端配置影响主菜单。
     */
    public NeteaseApiClient(Supplier<String> baseUrlSupplier) {
        this.baseUrlSupplier = baseUrlSupplier;
        urlResolver = new SongUrlResolver();
        urlResolver.register(new ByfunsUrlProvider());
        urlResolver.register(new QijieyaUrlProvider());
        urlResolver.register(new VkeysUrlProvider());
        urlResolver.register(baseUrlSupplier == null
                ? new MycelisUrlProvider()
                : new MycelisUrlProvider(this::baseUrl));
    }

    public static void shutdownExecutor() {
        ExecutorService apiExecutor = EXECUTOR;
        if (apiExecutor != null) {
            apiExecutor.shutdown();
        }
        ExecutorService probeExecutor = PROBE_EXECUTOR;
        if (probeExecutor != null) {
            probeExecutor.shutdown();
        }
    }

    public CompletableFuture<TrackInfo> resolveSong(String id) {
        return resolveTrackDetail(id);
    }

    public CompletableFuture<TrackInfo> resolveRadioSong(String id) {
        return resolveTrackDetail(id);
    }

    private CompletableFuture<TrackInfo> resolveTrackDetail(String id) {
        return songDetail(id).thenCompose(detail ->
                urlResolver.resolve(id).thenApply(urls ->
                        new TrackInfo(detail.id(), detail.title(), detail.artist(), detail.artistId(), detail.coverUrl(), urls, detail.durationMillis()))
        );
    }

    public CompletableFuture<List<SearchEntry>> searchSongs(String keyword) {
        return searchSongs(keyword, 1);
    }

    public CompletableFuture<List<SearchEntry>> searchSongs(String keyword, int page) {
        return search(keyword, SEARCH_TYPE_SONG, page);
    }

    public CompletableFuture<List<SearchEntry>> searchArtists(String keyword) {
        return searchArtists(keyword, 1);
    }

    public CompletableFuture<List<SearchEntry>> searchArtists(String keyword, int page) {
        return search(keyword, SEARCH_TYPE_ARTIST, page);
    }

    public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword) {
        return searchPlaylists(keyword, 1);
    }

    public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword, int page) {
        return search(keyword, SEARCH_TYPE_PLAYLIST, page);
    }

    public CompletableFuture<List<SearchEntry>> searchUsers(String keyword) {
        return searchUsers(keyword, 1);
    }

    public CompletableFuture<List<SearchEntry>> searchUsers(String keyword, int page) {
        return search(keyword, SEARCH_TYPE_USER, page);
    }

    public CompletableFuture<List<SearchEntry>> searchRadios(String keyword) {
        return searchRadios(keyword, 1);
    }

    public CompletableFuture<List<SearchEntry>> searchRadios(String keyword, int page) {
        return search(keyword, SEARCH_TYPE_RADIO, page);
    }

    public CompletableFuture<PlaylistInfo> playlistDetail(String id) {
        return getJson("/playlist/detail", "id", id).thenApply(root -> {
            JsonObject p = obj(root, "playlist");
            MusicPlayerMod.LOGGER.debug("歌单详情: id={}, trackCount={}", id, intVal(p, "trackCount"));
            return new PlaylistInfo(
                    str(p, "id"), str(p, "name"),
                    str(obj(p, "creator"), "userId"),
                    str(obj(p, "creator"), "nickname"),
                    List.of(), intVal(p, "trackCount"));
        });
    }

    public CompletableFuture<List<SearchEntry>> playlistTracksPage(String playlistId, int offset, int limit) {
        return getJson("/playlist/track/all", "id", playlistId,
                "limit", Integer.toString(limit),
                "offset", Integer.toString(offset)).thenApply(root -> {
            JsonArray songs = arr(root, "songs");
            if (songs == null || songs.size() == 0) return List.of();
            List<SearchEntry> tracks = new ArrayList<>();
            for (JsonElement elem : songs) {
                JsonObject song = elem.getAsJsonObject();
                String songId = str(song, "id");
                String artistId = firstArtistId(song);
                tracks.add(new SearchEntry(songId, str(song, "name"), firstArtistName(song),
                        playSongCommand(songId), artistId.isBlank() ? "" : viewArtistCommand(artistId)));
            }
            MusicPlayerMod.LOGGER.debug("歌单曲目分页: id={}, offset={}, limit={}, 返回={}",
                    playlistId, offset, limit, tracks.size());
            return tracks;
        });
    }

    public CompletableFuture<UserPlaylistView> userPlaylists(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            List<SearchEntry> entries = new ArrayList<>();
            String resolvedUserId = userId;
            String nickname = "";
            String signature = "";
            int offset = 0;

            while (true) {
                JsonObject root = executeJson(baseRequest(
                        baseUrl() + "/user/playlist",
                        new String[]{"uid", userId, "limit", Integer.toString(DETAIL_FETCH_BATCH_SIZE), "offset", Integer.toString(offset)},
                        "application/json,text/plain,*/*"
                ));
                JsonArray playlists = arr(root, "playlist");
                if (playlists == null || playlists.size() == 0) {
                    break;
                }
                for (JsonElement elem : playlists) {
                    JsonObject playlist = elem.getAsJsonObject();
                    JsonObject creator = obj(playlist, "creator");
                    if (nickname.isBlank()) {
                        resolvedUserId = str(creator, "userId", userId);
                        nickname = str(creator, "nickname");
                        signature = str(creator, "signature");
                    }
                    String playlistId = str(playlist, "id");
                    entries.add(new SearchEntry(
                            playlistId,
                            str(playlist, "name"),
                            new TranslatableComponent("musicplayer.entry.track_count", intVal(playlist, "trackCount")),
                            viewPlaylistCommand(playlistId),
                            ""
                    ));
                }
                if (playlists.size() < DETAIL_FETCH_BATCH_SIZE) {
                    break;
                }
                offset += playlists.size();
            }

            return new UserPlaylistView(resolvedUserId, nickname, signature, entries);
        }, executor());
    }

    public CompletableFuture<List<TrackInfo>> artistTopSongs(String artistId) {
        return getJson("/artist/top/song", "id", artistId).thenCompose(root -> {
            JsonArray songs = arr(root, "songs");
            if (songs == null || songs.size() == 0) {
                return CompletableFuture.completedFuture(List.of());
            }
            List<CompletableFuture<TrackInfo>> futures = new ArrayList<>();
            for (JsonElement elem : songs) {
                futures.add(resolveSongFromNode(elem.getAsJsonObject()));
            }
            return collectResults(futures);
        });
    }

    public CompletableFuture<List<SearchEntry>> topArtists(int limit, int offset) {
        return getJson("/top/artists", "limit", Integer.toString(limit), "offset", Integer.toString(offset))
                .thenApply(root -> {
                    List<SearchEntry> entries = new ArrayList<>();
                    JsonArray artists = arr(root, "artists");
                    if (artists == null) return entries;
                    for (JsonElement elem : artists) {
                        JsonObject artist = elem.getAsJsonObject();
                        String artistId = str(artist, "id");
                        entries.add(new SearchEntry(
                                artistId,
                                str(artist, "name"),
                                new TranslatableComponent("musicplayer.entry.artist"),
                                viewArtistCommand(artistId),
                                ""
                        ));
                    }
                    return entries;
                });
    }

    public CompletableFuture<ArtistInfo> artistDetail(String artistId) {
        return getJson("/artists", "id", artistId)
                .thenCombine(artistSongCount(artistId), (root, songCount) -> {
                    JsonObject artist = obj(root, "artist");
                    return new ArtistInfo(
                            str(artist, "id"),
                            str(artist, "name"),
                            str(artist, "briefDesc"),
                            songCount
                    );
                });
    }

    private CompletableFuture<Integer> artistSongCount(String artistId) {
        return getJson("/artist/songs", "id", artistId, "limit", "1", "offset", "0")
                .thenApply(root -> intVal(root, "total"));
    }

    public CompletableFuture<List<SearchEntry>> artistSongsPage(String artistId, int offset, int limit) {
        return getJson("/artist/songs", "id", artistId,
                "limit", Integer.toString(limit),
                "offset", Integer.toString(offset)).thenApply(root -> {
            JsonArray songs = arr(root, "songs");
            if (songs == null || songs.size() == 0) return List.of();
            List<SearchEntry> tracks = new ArrayList<>();
            for (JsonElement elem : songs) {
                JsonObject song = elem.getAsJsonObject();
                String songId = str(song, "id");
                tracks.add(new SearchEntry(songId, str(song, "name"),
                        str(obj(song, "al"), "name"), playSongCommand(songId), ""));
            }
            MusicPlayerMod.LOGGER.debug("作者歌曲分页: id={}, offset={}, limit={}, 返回={}, total={}",
                    artistId, offset, limit, tracks.size(), intVal(root, "total"));
            return tracks;
        });
    }

    public CompletableFuture<List<TrackInfo>> randomHotTracks(int count) {
        int safeCount = Math.max(1, count);
        return CompletableFuture.supplyAsync(() -> {
            Random random = new Random();
            List<String> hotCategories = fetchHotPlaylistCategories();
            String selectedCategory = hotCategories.isEmpty()
                    ? "全部"
                    : hotCategories.get(random.nextInt(hotCategories.size()));

            List<SearchEntry> playlists = fetchTopPlaylists(selectedCategory, HOT_PLAYLIST_FETCH_SIZE, 0);
            if (playlists.isEmpty() && !"全部".equals(selectedCategory)) {
                playlists = fetchTopPlaylists("全部", HOT_PLAYLIST_FETCH_SIZE, 0);
            }
            if (playlists.isEmpty()) {
                throw new IllegalStateException("musicplayer.error.hot_playlists_failed");
            }

            Collections.shuffle(playlists, random);
            List<JsonObject> candidateNodes = new ArrayList<>();
            int playlistProbeCount = Math.min(playlists.size(), 8);
            for (int index = 0; index < playlistProbeCount && candidateNodes.size() < safeCount * 4; index++) {
                SearchEntry playlist = playlists.get(index);
                List<JsonObject> songNodes = fetchPlaylistSongsRawSync(playlist.id(), RANDOM_HOT_MAX_SONGS_PER_PLAYLIST);
                if (songNodes.isEmpty()) {
                    continue;
                }
                Collections.shuffle(songNodes, random);
                int take = Math.min(songNodes.size(), Math.max(12, safeCount));
                for (int songIndex = 0; songIndex < take && candidateNodes.size() < safeCount * 4; songIndex++) {
                    candidateNodes.add(songNodes.get(songIndex));
                }
            }

            if (candidateNodes.isEmpty()) {
                throw new IllegalStateException("musicplayer.error.hot_playlists_no_songs");
            }

            Collections.shuffle(candidateNodes, random);
            if (candidateNodes.size() > safeCount) {
                candidateNodes = new ArrayList<>(candidateNodes.subList(0, safeCount));
            }

            List<CompletableFuture<TrackInfo>> futures = candidateNodes.stream()
                    .map(this::resolveSongFromNode)
                    .collect(Collectors.toList());
            try {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .orTimeout(RANDOM_HOT_TOTAL_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                        .join();
            } catch (Exception ex) {
                MusicPlayerMod.LOGGER.warn("随机热门歌曲解析超时或失败", ex);
            }

            List<TrackInfo> tracks = new ArrayList<>();
            for (CompletableFuture<TrackInfo> future : futures) {
                try {
                    TrackInfo track = future.getNow(null);
                    if (track != null && track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
                        tracks.add(track);
                    }
                } catch (Exception ex) {
                    MusicPlayerMod.LOGGER.trace("随机热门歌曲解析忽略失败的曲目", ex);
                }
            }
            if (tracks.isEmpty()) {
                throw new IllegalStateException("musicplayer.error.random_hot_resolve_failed");
            }
            return tracks;
        }, executor());
    }

    private CompletableFuture<List<SearchEntry>> search(String keyword, int type, int page) {
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * config.searchLimit;
        return getJson(
                "/cloudsearch",
                "limit", Integer.toString(config.searchLimit),
                "offset", Integer.toString(offset),
                "type", Integer.toString(type),
                "keywords", keyword
        ).thenApply(root -> {
            List<SearchEntry> entries = new ArrayList<>();
            JsonObject result = obj(root, "result");
            JsonArray list = switch (type) {
                case SEARCH_TYPE_SONG -> arr(result, "songs");
                case SEARCH_TYPE_ARTIST -> arr(result, "artists");
                case SEARCH_TYPE_PLAYLIST -> arr(result, "playlists");
                case SEARCH_TYPE_USER -> arr(result, "userprofiles");
                case SEARCH_TYPE_RADIO -> arr(result, "djRadios");
                default -> new JsonArray();
            };
            if (list == null) {
                return entries;
            }

            for (JsonElement elem : list) {
                JsonObject node = elem.getAsJsonObject();
                if (type == SEARCH_TYPE_SONG) {
                    String songId = str(node, "id");
                    String artistId = firstArtistId(node);
                    entries.add(new SearchEntry(
                            songId,
                            str(node, "name"),
                            firstArtistName(node),
                            playSongCommand(songId),
                            artistId.isBlank() ? "" : viewArtistCommand(artistId)
                    ));
                } else if (type == SEARCH_TYPE_ARTIST) {
                    String artistId = str(node, "id");
                    entries.add(new SearchEntry(
                            artistId,
                            str(node, "name"),
                            new TranslatableComponent("musicplayer.entry.artist"),
                            viewArtistCommand(artistId),
                            ""
                    ));
                } else if (type == SEARCH_TYPE_PLAYLIST) {
                    String playlistId = str(node, "id");
                    String ownerId = str(obj(node, "creator"), "userId");
                    entries.add(new SearchEntry(
                            playlistId,
                            str(node, "name"),
                            str(obj(node, "creator"), "nickname"),
                            viewPlaylistCommand(playlistId),
                            ownerId.isBlank() ? "" : viewUserCommand(ownerId)
                    ));
                } else if (type == SEARCH_TYPE_USER) {
                    String foundUserId = str(node, "userId");
                    entries.add(new SearchEntry(
                            foundUserId,
                            str(node, "nickname"),
                            str(node, "signature"),
                            viewUserCommand(foundUserId),
                            ""
                    ));
                } else if (type == SEARCH_TYPE_RADIO) {
                    String rid = str(node, "id");
                    String rname = str(node, "name");
                    entries.add(new SearchEntry(
                            rid,
                            rname,
                            new TranslatableComponent("musicplayer.entry.radio"),
                            viewRadioCommand(rid),
                            playRadioCommand(rid)
                    ));
                }
            }
            return entries;
        });
    }

    private CompletableFuture<TrackInfo> songDetail(String id) {
        return getJson("/song/detail", "ids", id).thenApply(root -> {
            JsonArray songs = arr(root, "songs");
            JsonObject song = songs != null && songs.size() != 0 ? songs.get(0).getAsJsonObject() : null;
            if (song == null) {
                return new TrackInfo(id, "", "", "", "", List.of(), 0L);
            }
            return new TrackInfo(
                    str(song, "id"),
                    str(song, "name"),
                    firstArtistName(song),
                    firstArtistId(song),
                    songCoverUrl(song),
                    List.of(),
                    lng(song, "dt")
            );
        });
    }

    // ── Radio / Podcast ──────────────────────────────────────────────

    public CompletableFuture<RadioInfo> radioDetail(String rid) {
        return getJson("/dj/detail", "rid", rid).thenApply(root -> {
            JsonObject data = obj(root, "data");
            if (data == null) {
                throw new IllegalStateException("musicplayer.error.radio_detail_empty");
            }
            JsonObject dj = obj(data, "dj");
            return new RadioInfo(
                    str(data, "id"),
                    str(data, "name"),
                    str(data, "desc"),
                    str(data, "category"),
                    str(data, "secondCategory", ""),
                    str(data, "picUrl"),
                    intVal(data, "programCount"),
                    intVal(data, "subCount"),
                    lng(data, "playCount"),
                    intVal(data, "radioFeeType")
            );
        });
    }

    public CompletableFuture<List<ProgramInfo>> radioPrograms(String rid, int limit, int offset, boolean asc) {
        return getJson("/dj/program", "rid", rid, "limit", Integer.toString(limit), "offset", Integer.toString(offset), "asc", Boolean.toString(asc))
                .thenApply(root -> {
                    List<ProgramInfo> programs = new ArrayList<>();
                    JsonArray list = arr(root, "programs");
                    if (list == null) return programs;
                    for (JsonElement elem : list) {
                        JsonObject node = elem.getAsJsonObject();
                        JsonObject mainSong = obj(node, "mainSong");
                        String mainTrackId = mainSong != null ? str(mainSong, "id") : "";
                        String coverUrl = str(node, "coverUrl");
                        if (coverUrl.isBlank()) coverUrl = str(node, "coverUrl");
                        JsonObject radio = obj(node, "radio");
                        String radioId = radio != null ? str(radio, "id") : rid;
                        String radioName = radio != null ? str(radio, "name") : "";
                        programs.add(new ProgramInfo(
                                str(node, "id"),
                                str(node, "name"),
                                mainTrackId,
                                lng(node, "duration"),
                                coverUrl,
                                str(node, "description"),
                                radioId,
                                radioName
                        ));
                    }
                    return programs;
                });
    }

    public CompletableFuture<ProgramInfo> programDetail(String programId) {
        return getJson("/dj/program/detail", "id", programId).thenApply(root -> {
            JsonObject prog = obj(root, "program");
            if (prog == null) {
                throw new IllegalStateException("musicplayer.error.program_detail_empty");
            }
            JsonObject mainSong = obj(prog, "mainSong");
            String mainTrackId = mainSong != null ? str(mainSong, "id") : "";
            JsonObject radio = obj(prog, "radio");
            String radioId = radio != null ? str(radio, "id") : "";
            String radioName = radio != null ? str(radio, "name") : "";
            return new ProgramInfo(
                    str(prog, "id"),
                    str(prog, "name"),
                    mainTrackId,
                    lng(prog, "duration"),
                    str(prog, "coverUrl"),
                    str(prog, "description"),
                    radioId,
                    radioName
            );
        });
    }

    public CompletableFuture<List<SearchEntry>> hotRadios(int limit, int offset) {
        return getJson("/dj/hot", "limit", Integer.toString(limit), "offset", Integer.toString(offset))
                .thenApply(root -> {
                    List<SearchEntry> entries = new ArrayList<>();
                    JsonArray list = arr(root, "djRadios");
                    if (list == null) list = arr(root, "data");
                    if (list == null) return entries;
                    for (JsonElement elem : list) {
                        JsonObject node = elem.getAsJsonObject();
                        String rid = str(node, "id");
                        String name = str(node, "name");
                        // 接口使用小写 rcmdtext，旧实现读取 rcmdText 会始终取不到简介
                        String rcmd = str(node, "rcmdtext");
                        if (rcmd.isBlank()) rcmd = str(node, "rcmdText");
                        if (rcmd.isBlank()) rcmd = str(node, "desc");
                        entries.add(new SearchEntry(
                                rid,
                                name,
                                rcmd.isBlank() ? new TranslatableComponent("musicplayer.entry.radio") : new TextComponent(rcmd),
                                viewRadioCommand(rid),
                                ""
                        ));
                    }
                    return entries;
                });
    }

    public CompletableFuture<List<SearchEntry>> radioCategories() {
        return getJson("/dj/category/recommend").thenApply(root -> {
            List<SearchEntry> entries = new ArrayList<>();
            JsonArray data = arr(root, "data");
            if (data == null) return entries;
            for (JsonElement elem : data) {
                JsonObject node = elem.getAsJsonObject();
                // 接口字段为 categoryId/categoryName，读取 id/name 会得到空分类
                String categoryId = str(node, "categoryId");
                if (categoryId.isBlank()) categoryId = str(node, "id");
                String name = str(node, "categoryName");
                if (name.isBlank()) name = str(node, "name");
                JsonArray radios = arr(node, "radios");
                int radioCount = radios != null ? radios.size() : 0;
                entries.add(new SearchEntry(
                        categoryId,
                        name,
                        new TranslatableComponent("musicplayer.entry.radio_count", radioCount),
                        "",
                        ""
                ));
            }
            return entries;
        });
    }

    private CompletableFuture<JsonObject> getJson(String path, String... queryPairs) {
        return getJsonFromAbsoluteUrl(baseUrl() + path, queryPairs);
    }

    private List<String> fetchHotPlaylistCategories() {
        JsonObject root = executeJson(baseRequest(
                baseUrl() + "/playlist/hot",
                new String[0],
                "application/json,text/plain,*/*"
        ));
        JsonArray tags = arr(root, "tags");
        List<String> categories = new ArrayList<>();
        if (tags != null) {
            for (JsonElement tag : tags) {
                String name = str(tag.getAsJsonObject(), "name");
                if (!name.isBlank()) {
                    categories.add(name);
                }
            }
        }
        if (categories.stream().noneMatch("全部"::equals)) {
            categories.add("全部");
        }
        return categories;
    }

    private List<SearchEntry> fetchTopPlaylists(String category, int limit, int offset) {
        JsonObject root = executeJson(baseRequest(
                baseUrl() + "/top/playlist",
                new String[]{
                        "order", "hot",
                        "cat", category,
                        "limit", Integer.toString(limit),
                        "offset", Integer.toString(offset)
                },
                "application/json,text/plain,*/*"
        ));
        JsonArray playlists = arr(root, "playlists");
        List<SearchEntry> entries = new ArrayList<>();
        if (playlists == null) {
            return entries;
        }
        for (JsonElement elem : playlists) {
            JsonObject playlist = elem.getAsJsonObject();
            String playlistId = str(playlist, "id");
            String ownerId = str(obj(playlist, "creator"), "userId");
            entries.add(new SearchEntry(
                    playlistId,
                    str(playlist, "name"),
                    str(obj(playlist, "creator"), "nickname"),
                    viewPlaylistCommand(playlistId),
                    ownerId.isBlank() ? "" : viewUserCommand(ownerId)
            ));
        }
        return entries;
    }

    private List<JsonObject> fetchPlaylistSongsRawSync(String playlistId, int maxSongs) {
        List<JsonObject> songs = new ArrayList<>();
        int offset = 0;

        while (songs.size() < maxSongs) {
            JsonObject root = executeJson(baseRequest(
                    baseUrl() + "/playlist/track/all",
                    new String[]{"id", playlistId, "limit", Integer.toString(DETAIL_FETCH_BATCH_SIZE), "offset", Integer.toString(offset)},
                    "application/json,text/plain,*/*"
            ));
            JsonArray page = arr(root, "songs");
            if (page == null || page.size() == 0) {
                break;
            }
            for (JsonElement elem : page) {
                if (songs.size() >= maxSongs) break;
                songs.add(elem.getAsJsonObject());
            }
            if (page.size() < DETAIL_FETCH_BATCH_SIZE) {
                break;
            }
            offset += page.size();
        }

        return songs;
    }

    private CompletableFuture<TrackInfo> resolveSongFromNode(JsonObject songNode) {
        String id = str(songNode, "id");
        if (id.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String title = str(songNode, "name");
        String artist = firstArtistName(songNode);
        String artistId = firstArtistId(songNode);
        String coverUrl = songCoverUrl(songNode);
        long duration = lng(songNode, "dt");
        return urlResolver.resolve(id).thenApply(urls -> new TrackInfo(id, title, artist, artistId, coverUrl, urls, duration));
    }

    private static CompletableFuture<List<TrackInfo>> collectResults(List<CompletableFuture<TrackInfo>> futures) {
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(nil -> {
                    List<TrackInfo> tracks = new ArrayList<>();
                    for (CompletableFuture<TrackInfo> future : futures) {
                        try {
                            TrackInfo track = future.join();
                            if (track != null && track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
                                tracks.add(track);
                            }
                        } catch (Exception ex) {
                            MusicPlayerMod.LOGGER.trace("收集曲目结果时忽略失败的请求", ex);
                        }
                    }
                    return tracks;
                });
    }

    private CompletableFuture<JsonObject> getJsonFromAbsoluteUrl(String absoluteUrl, String... queryPairs) {
        return CompletableFuture.supplyAsync(() -> {
            Request request = baseRequest(absoluteUrl, queryPairs, "application/json,text/plain,*/*");
            return executeJson(request);
        }, executor());
    }

    private static Request baseRequest(String absoluteUrl, String[] queryPairs, String accept) {
        HttpUrl parsed = HttpUrl.parse(absoluteUrl);
        if (parsed == null) {
            throw new IllegalArgumentException("musicplayer.error.invalid_api_url");
        }
        HttpUrl.Builder builder = parsed.newBuilder();
        for (int index = 0; index + 1 < queryPairs.length; index += 2) {
            builder.addQueryParameter(queryPairs[index], queryPairs[index + 1]);
        }
        return new Request.Builder()
                .url(builder.build())
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .get()
                .build();
    }

    private JsonObject executeJson(Request request) {
        OkHttpClient client = HttpClientFactory.createApiClient();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            String body = Objects.requireNonNull(response.body()).string();
            return new JsonParser().parse(body).getAsJsonObject();
        } catch (Exception exception) {
            MusicPlayerMod.LOGGER.warn("请求音乐接口失败: {} - {}", request.method(), Messages.sanitizeForLog(request.url().toString()), exception.getMessage());
            MusicPlayerMod.LOGGER.debug("请求音乐接口失败详情", exception);
            throw new RuntimeException(exception);
        }
    }

    private String baseUrl() {
        String raw = baseUrlSupplier != null ? baseUrlSupplier.get() : MusicPlayerConfigManager.get().neteaseBaseUrl;
        if (raw == null || raw.isBlank()) {
            raw = MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL;
        }
        raw = raw.trim();
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private static String firstArtistName(JsonObject songNode) {
        JsonArray artists = arr(songNode, "ar");
        if (artists != null && artists.size() != 0) {
            JsonElement el = artists.get(0);
            if (el != null && el.isJsonObject()) return str(el.getAsJsonObject(), "name");
        }
        JsonArray artistsAlt = arr(songNode, "artists");
        if (artistsAlt != null && artistsAlt.size() != 0) {
            JsonElement el = artistsAlt.get(0);
            if (el != null && el.isJsonObject()) return str(el.getAsJsonObject(), "name");
        }
        return "";
    }

    private static String firstArtistId(JsonObject songNode) {
        JsonArray artists = arr(songNode, "ar");
        if (artists != null && artists.size() != 0) {
            JsonElement el = artists.get(0);
            if (el != null && el.isJsonObject()) return str(el.getAsJsonObject(), "id");
        }
        JsonArray artistsAlt = arr(songNode, "artists");
        if (artistsAlt != null && artistsAlt.size() != 0) {
            JsonElement el = artistsAlt.get(0);
            if (el != null && el.isJsonObject()) return str(el.getAsJsonObject(), "id");
        }
        return "";
    }

    private static String songCoverUrl(JsonObject songNode) {
        String albumCover = str(obj(songNode, "al"), "picUrl");
        if (!albumCover.isBlank()) {
            return albumCover;
        }
        String albumCoverAlt = str(obj(songNode, "album"), "picUrl");
        if (!albumCoverAlt.isBlank()) {
            return albumCoverAlt;
        }
        return "";
    }

    private static String playSongCommand(String songId) {
        return "/music play song " + songId;
    }

    private static String viewArtistCommand(String artistId) {
        return "/music view artist " + artistId;
    }

    private static String viewPlaylistCommand(String playlistId) {
        return "/music view playlist " + playlistId;
    }

    private static String viewUserCommand(String userId) {
        return "/music view user " + userId;
    }

    private static String viewRadioCommand(String rid) {
        return "/music view radio " + rid;
    }

    private static String playRadioCommand(String rid) {
        return "/music play radio " + rid;
    }

    // --- null-safe Gson helpers matching Jackson's path() behavior ---

    private static JsonObject obj(JsonObject parent, String key) {
        if (parent == null) return null;
        JsonElement el = parent.get(key);
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    private static JsonArray arr(JsonObject parent, String key) {
        if (parent == null) return null;
        JsonElement el = parent.get(key);
        return el != null && el.isJsonArray() ? el.getAsJsonArray() : null;
    }

    private static String str(JsonObject parent, String key) {
        if (parent == null) return "";
        JsonElement el = parent.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : "";
    }

    private static String str(JsonObject parent, String key, String def) {
        if (parent == null) return def;
        JsonElement el = parent.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : def;
    }

    private static long lng(JsonObject parent, String key) {
        if (parent == null) return 0L;
        JsonElement el = parent.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber() ? el.getAsLong() : 0L;
    }


    private static int intVal(JsonObject parent, String key) {
        if (parent == null) return 0;
        JsonElement el = parent.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber() ? el.getAsInt() : 0;
    }
}
