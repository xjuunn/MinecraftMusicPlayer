package com.junhsiun.musicplayer.platform.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import com.junhsiun.musicplayer.platform.NeteaseApiClient;
import com.junhsiun.musicplayer.platform.url.ByfunsUrlProvider;
import com.junhsiun.musicplayer.platform.url.MycelisUrlProvider;
import com.junhsiun.musicplayer.platform.url.QijieyaUrlProvider;
import com.junhsiun.musicplayer.platform.url.SongUrlResolver;
import com.junhsiun.musicplayer.platform.url.VkeysUrlProvider;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网易云直连实现：直接访问 {@code music.163.com} 的网页版明文接口。
 *
 * <p>相比经由第三方后端转发，直连省去了反向代理的冷启动与排队开销（实测 7 秒级降到 100 毫秒级），
 * 并且不依赖任何自建或公共服务。所有加密族（weapi/eapi）都不参与——模组用到的接口
 * 全部在 {@code /api/} 明文前缀下可用。
 *
 * <p>本类只做「请求 + 字段映射」，失败一律以异常结束，由 {@link MusicApiRouter} 决定是否回退后端。
 */
public final class DirectMusicApi implements MusicApi {
    private static final int SEARCH_TYPE_SONG = 1;
    private static final int SEARCH_TYPE_ARTIST = 100;
    private static final int SEARCH_TYPE_PLAYLIST = 1000;
    private static final int SEARCH_TYPE_USER = 1002;
    private static final int SEARCH_TYPE_RADIO = 1009;

    /** 歌单详情单次请求的分页参数；给足 n 才会返回全量 trackIds。 */
    private static final String PLAYLIST_DETAIL_PAGE_SIZE = "100000";
    private static final String PLAYLIST_DETAIL_S = "8";
    /** 批量歌曲详情上限（网易云经验值，超过会被拒绝）。 */
    private static final int SONG_DETAIL_BATCH = 500;
    /** 播放地址批量上限。 */
    private static final int PLAYER_URL_BATCH = 400;
    /** 用户歌单分页大小；与后端取值保持一致即可，仅影响每次请求的条数。 */
    private static final int USER_PLAYLIST_PAGE_SIZE = 100;
    private static final int HOT_PLAYLIST_FETCH_SIZE = 30;
    private static final int RANDOM_HOT_MAX_SONGS_PER_PLAYLIST = 200;

    /** 歌单 trackIds 缓存有效期；分页加载时同一歌单会被连续请求多次。 */
    private static final long TRACK_IDS_CACHE_MILLIS = 5 * 60 * 1000L;
    private static final int TRACK_IDS_CACHE_ENTRIES = 64;

    private final SongUrlResolver urlResolver;
    private final Map<String, CachedTrackIds> trackIdsCache = new ConcurrentHashMap<>();

    /** 直连模式下的音源顺序：官方直链优先，Mycelis 仍作最后保底。 */
    public DirectMusicApi() {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new DirectSongUrlProvider());
        resolver.register(new ByfunsUrlProvider());
        resolver.register(new QijieyaUrlProvider());
        resolver.register(new VkeysUrlProvider());
        resolver.register(new MycelisUrlProvider(DirectMusicApi::configuredBaseUrl));
        this.urlResolver = resolver;
    }

    /** 允许外部注入音源解析器，便于测试与自定义音源顺序。 */
    public DirectMusicApi(SongUrlResolver urlResolver) {
        this.urlResolver = urlResolver;
    }

    /** 歌单 trackIds 缓存项，{@code expiresAt} 为 0 表示不过期。 */
    private record CachedTrackIds(List<String> ids, long expiresAt) {
        boolean expired(long now) {
            return expiresAt > 0 && now > expiresAt;
        }
    }

    // ── 单曲 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<TrackInfo> resolveSong(String id) {
        return resolveTrack(id);
    }

    @Override
    public CompletableFuture<TrackInfo> resolveRadioSong(String id) {
        return resolveTrack(id);
    }

    private CompletableFuture<TrackInfo> resolveTrack(String id) {
        // 详情与直链互不依赖，并行发起；此处不阻塞线程池
        CompletableFuture<TrackInfo> detailFuture = async(() -> songDetailSync(id));
        CompletableFuture<List<String>> urlFuture = urlResolver.resolve(id)
                .exceptionally(ignored -> List.of());
        return detailFuture.thenCombine(urlFuture, (detail, urls) ->
                new TrackInfo(detail.id(), detail.title(), detail.artist(), detail.artistId(),
                        detail.coverUrl(), urls, detail.durationMillis()));
    }

    /** 单曲详情（不含直链）。 */
    private TrackInfo songDetailSync(String id) {
        Map<String, String> form = Map.of("c", "[{\"id\":\"" + id + "\"}]");
        JsonObject root;
        try {
            root = DirectHttp.post(NeteaseEndpoints.SONG_DETAIL, form);
        } catch (Exception exception) {
            throw new MusicApiException("musicplayer.error.song_detail_failed", exception);
        }
        JsonArray songs = ApiJson.arr(root, "songs");
        if (songs == null || songs.isEmpty() || !songs.get(0).isJsonObject()) {
            return new TrackInfo(id, "", "", "", "", List.of(), 0L);
        }
        JsonObject song = songs.get(0).getAsJsonObject();
        return new TrackInfo(
                ApiJson.firstNonBlank(song, "id"),
                ApiJson.str(song, "name"),
                ApiJson.firstArtistName(song),
                ApiJson.firstArtistId(song),
                ApiJson.albumCover(song),
                List.of(),
                ApiJson.durationMillis(song)
        );
    }

    // ── 搜索 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<List<SearchEntry>> searchSongs(String keyword) {
        return search(keyword, SEARCH_TYPE_SONG, 1);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchSongs(String keyword, int page) {
        return search(keyword, SEARCH_TYPE_SONG, page);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchArtists(String keyword) {
        return search(keyword, SEARCH_TYPE_ARTIST, 1);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchArtists(String keyword, int page) {
        return search(keyword, SEARCH_TYPE_ARTIST, page);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword) {
        return search(keyword, SEARCH_TYPE_PLAYLIST, 1);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword, int page) {
        return search(keyword, SEARCH_TYPE_PLAYLIST, page);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchUsers(String keyword) {
        return search(keyword, SEARCH_TYPE_USER, 1);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchUsers(String keyword, int page) {
        return search(keyword, SEARCH_TYPE_USER, page);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchRadios(String keyword) {
        return search(keyword, SEARCH_TYPE_RADIO, 1);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchRadios(String keyword, int page) {
        return search(keyword, SEARCH_TYPE_RADIO, page);
    }

    private CompletableFuture<List<SearchEntry>> search(String keyword, int type, int page) {
        int limit = Math.max(1, MusicPlayerConfigManager.get().searchLimit);
        int offset = (Math.max(1, page) - 1) * limit;
        return async(() -> {
            JsonObject root = DirectHttp.get(NeteaseEndpoints.CLOUD_SEARCH,
                    "s", keyword,
                    "type", Integer.toString(type),
                    "limit", Integer.toString(limit),
                    "offset", Integer.toString(offset));
            JsonObject result = ApiJson.obj(root, "result");
            JsonArray list = switch (type) {
                case SEARCH_TYPE_SONG -> ApiJson.arr(result, "songs");
                case SEARCH_TYPE_ARTIST -> ApiJson.arr(result, "artists");
                case SEARCH_TYPE_PLAYLIST -> ApiJson.arr(result, "playlists");
                case SEARCH_TYPE_USER -> ApiJson.arr(result, "userprofiles");
                case SEARCH_TYPE_RADIO -> ApiJson.arr(result, "djRadios");
                default -> null;
            };
            List<SearchEntry> entries = new ArrayList<>();
            if (list == null) {
                return entries;
            }
            for (JsonElement element : list) {
                if (!element.isJsonObject()) continue;
                JsonObject node = element.getAsJsonObject();
                SearchEntry entry = toSearchEntry(node, type);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return entries;
        });
    }

    private static SearchEntry toSearchEntry(JsonObject node, int type) {
        return switch (type) {
            case SEARCH_TYPE_SONG -> {
                String songId = ApiJson.str(node, "id");
                String artistId = ApiJson.firstArtistId(node);
                yield new SearchEntry(songId, ApiJson.str(node, "name"), ApiJson.firstArtistName(node),
                        ApiCommands.playSong(songId),
                        artistId.isBlank() ? "" : ApiCommands.viewArtist(artistId));
            }
            case SEARCH_TYPE_ARTIST -> {
                String artistId = ApiJson.str(node, "id");
                yield new SearchEntry(artistId, ApiJson.str(node, "name"),
                        new TranslatableComponent("musicplayer.entry.artist"),
                        ApiCommands.viewArtist(artistId), "");
            }
            case SEARCH_TYPE_PLAYLIST -> {
                String playlistId = ApiJson.str(node, "id");
                String ownerId = ApiJson.str(ApiJson.obj(node, "creator"), "userId");
                yield new SearchEntry(playlistId, ApiJson.str(node, "name"),
                        ApiJson.str(ApiJson.obj(node, "creator"), "nickname"),
                        ApiCommands.viewPlaylist(playlistId),
                        ownerId.isBlank() ? "" : ApiCommands.viewUser(ownerId));
            }
            case SEARCH_TYPE_USER -> {
                String userId = ApiJson.str(node, "userId");
                yield new SearchEntry(userId, ApiJson.str(node, "nickname"), ApiJson.str(node, "signature"),
                        ApiCommands.viewUser(userId), "");
            }
            case SEARCH_TYPE_RADIO -> {
                String rid = ApiJson.str(node, "id");
                yield new SearchEntry(rid, ApiJson.str(node, "name"),
                        new TranslatableComponent("musicplayer.entry.radio"),
                        ApiCommands.viewRadio(rid), ApiCommands.playRadio(rid));
            }
            default -> null;
        };
    }

    // ── 歌单 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<PlaylistInfo> playlistDetail(String id) {
        return async(() -> {
            JsonObject root = DirectHttp.get(NeteaseEndpoints.PLAYLIST_DETAIL,
                    "id", id, "n", PLAYLIST_DETAIL_PAGE_SIZE, "s", PLAYLIST_DETAIL_S);
            JsonObject playlist = ApiJson.obj(root, "playlist");
            if (playlist == null) {
                throw new MusicApiException("musicplayer.error.playlist_detail_empty");
            }
            JsonObject creator = ApiJson.obj(playlist, "creator");
            int trackCount = ApiJson.intVal(playlist, "trackCount");
            if (trackCount <= 0) {
                JsonArray ids = ApiJson.arr(playlist, "trackIds");
                trackCount = ids == null ? 0 : ids.size();
            }
            MusicPlayerMod.LOGGER.debug("歌单详情(直连): id={}, trackCount={}", id, trackCount);
            return new PlaylistInfo(
                    ApiJson.str(playlist, "id"),
                    ApiJson.str(playlist, "name"),
                    ApiJson.str(creator, "userId"),
                    ApiJson.str(creator, "nickname"),
                    List.of(),
                    trackCount
            );
        });
    }

    @Override
    public CompletableFuture<List<SearchEntry>> playlistTracksPage(String playlistId, int offset, int limit) {
        return async(() -> {
            // 歌单详情一次返回全量 trackIds，按偏移量切出需要的区间，再批量补详情
            List<String> allIds = playlistTrackIdsSync(playlistId);
            if (allIds.isEmpty()) {
                return List.<SearchEntry>of();
            }
            int from = Math.max(0, offset);
            int to = Math.min(allIds.size(), from + Math.max(1, limit));
            if (from >= to) {
                return List.<SearchEntry>of();
            }
            MusicPlayerMod.LOGGER.debug("歌单曲目分页(直连): id={}, offset={}, limit={}, 命中={}",
                    playlistId, offset, limit, to - from);
            return toTrackEntries(fetchSongNodes(allIds.subList(from, to)));
        });
    }

    /** 取歌单全部曲目 ID，带短期缓存以支持连续分页加载。 */
    private List<String> playlistTrackIdsSync(String playlistId) throws Exception {
        long now = System.currentTimeMillis();
        CachedTrackIds cached = trackIdsCache.get(playlistId);
        if (cached != null && !cached.expired(now)) {
            return cached.ids();
        }
        JsonObject root = DirectHttp.get(NeteaseEndpoints.PLAYLIST_DETAIL,
                "id", playlistId, "n", PLAYLIST_DETAIL_PAGE_SIZE, "s", PLAYLIST_DETAIL_S);
        JsonArray trackIds = ApiJson.arr(ApiJson.obj(root, "playlist"), "trackIds");
        List<String> ids = new ArrayList<>();
        if (trackIds != null) {
            for (JsonElement element : trackIds) {
                if (!element.isJsonObject()) continue;
                String songId = ApiJson.str(element.getAsJsonObject(), "id");
                if (!songId.isBlank()) {
                    ids.add(songId);
                }
            }
        }
        if (!ids.isEmpty()) {
            evictTrackIdsCache(now);
            trackIdsCache.put(playlistId, new CachedTrackIds(List.copyOf(ids), now + TRACK_IDS_CACHE_MILLIS));
        }
        return ids;
    }

    private void evictTrackIdsCache(long now) {
        trackIdsCache.entrySet().removeIf(entry -> entry.getValue().expired(now));
        if (trackIdsCache.size() >= TRACK_IDS_CACHE_ENTRIES) {
            trackIdsCache.clear();
        }
    }

    /** 批量拉取歌曲详情节点，自动按接口上限分批。 */
    private List<JsonObject> fetchSongNodes(List<String> songIds) throws Exception {
        List<JsonObject> nodes = new ArrayList<>(songIds.size());
        for (int start = 0; start < songIds.size(); start += SONG_DETAIL_BATCH) {
            int end = Math.min(songIds.size(), start + SONG_DETAIL_BATCH);
            List<String> batch = songIds.subList(start, end);
            StringBuilder body = new StringBuilder(batch.size() * 16 + 8);
            body.append('[');
            for (int index = 0; index < batch.size(); index++) {
                if (index > 0) body.append(',');
                body.append("{\"id\":\"").append(batch.get(index)).append("\"}");
            }
            body.append(']');
            JsonObject root = DirectHttp.post(NeteaseEndpoints.SONG_DETAIL, Map.of("c", body.toString()));
            JsonArray songs = ApiJson.arr(root, "songs");
            if (songs != null) {
                for (JsonElement element : songs) {
                    if (element.isJsonObject()) {
                        nodes.add(element.getAsJsonObject());
                    }
                }
            }
        }
        return nodes;
    }

    private static List<SearchEntry> toTrackEntries(List<JsonObject> nodes) {
        List<SearchEntry> entries = new ArrayList<>(nodes.size());
        for (JsonObject song : nodes) {
            String songId = ApiJson.str(song, "id");
            if (songId.isBlank()) continue;
            String artistId = ApiJson.firstArtistId(song);
            entries.add(new SearchEntry(songId, ApiJson.str(song, "name"), ApiJson.firstArtistName(song),
                    ApiCommands.playSong(songId),
                    artistId.isBlank() ? "" : ApiCommands.viewArtist(artistId)));
        }
        return entries;
    }

    @Override
    public CompletableFuture<UserPlaylistView> userPlaylists(String userId) {
        return async(() -> {
            List<SearchEntry> entries = new ArrayList<>();
            String resolvedUserId = userId;
            String nickname = "";
            String signature = "";
            int offset = 0;
            // 用户歌单接口有分页，循环拉取直到不足一页
            while (true) {
                JsonObject root = DirectHttp.get(NeteaseEndpoints.USER_PLAYLIST,
                        "uid", userId,
                        "limit", Integer.toString(USER_PLAYLIST_PAGE_SIZE),
                        "offset", Integer.toString(offset),
                        "includeVideo", "true");
                JsonArray playlists = ApiJson.arr(root, "playlist");
                if (playlists == null || playlists.isEmpty()) {
                    break;
                }
                for (JsonElement element : playlists) {
                    if (!element.isJsonObject()) continue;
                    JsonObject playlist = element.getAsJsonObject();
                    JsonObject creator = ApiJson.obj(playlist, "creator");
                    if (nickname.isBlank()) {
                        resolvedUserId = ApiJson.str(creator, "userId", userId);
                        nickname = ApiJson.str(creator, "nickname");
                        signature = ApiJson.str(creator, "signature");
                    }
                    String playlistId = ApiJson.str(playlist, "id");
                    entries.add(new SearchEntry(
                            playlistId,
                            ApiJson.str(playlist, "name"),
                            new TranslatableComponent("musicplayer.entry.track_count",
                                    ApiJson.intVal(playlist, "trackCount")),
                            ApiCommands.viewPlaylist(playlistId),
                            ""
                    ));
                }
                if (playlists.size() < USER_PLAYLIST_PAGE_SIZE) {
                    break;
                }
                offset += playlists.size();
            }
            return new UserPlaylistView(resolvedUserId, nickname, signature, entries);
        });
    }

    // ── 歌手 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<List<TrackInfo>> artistTopSongs(String artistId) {
        return async(() -> {
            JsonObject root = DirectHttp.get(NeteaseEndpoints.ARTIST_TOP_SONG, "id", artistId);
            JsonArray songs = ApiJson.arr(root, "songs");
            List<JsonObject> nodes = new ArrayList<>();
            if (songs == null) {
                return nodes;
            }
            for (JsonElement element : songs) {
                if (element.isJsonObject()) {
                    nodes.add(element.getAsJsonObject());
                }
            }
            return nodes;
        }).thenCompose(this::resolveNodes);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> topArtists(int limit, int offset) {
        return async(() -> {
            JsonObject root = DirectHttp.get(NeteaseEndpoints.ARTIST_TOP,
                    "limit", Integer.toString(Math.max(1, limit)),
                    "offset", Integer.toString(Math.max(0, offset)));
            JsonArray artists = ApiJson.arr(root, "artists");
            List<SearchEntry> entries = new ArrayList<>();
            if (artists == null) {
                return entries;
            }
            for (JsonElement element : artists) {
                if (!element.isJsonObject()) continue;
                JsonObject artist = element.getAsJsonObject();
                String artistId = ApiJson.str(artist, "id");
                entries.add(new SearchEntry(artistId, ApiJson.str(artist, "name"),
                        new TranslatableComponent("musicplayer.entry.artist"),
                        ApiCommands.viewArtist(artistId), ""));
            }
            return entries;
        });
    }

    @Override
    public CompletableFuture<ArtistInfo> artistDetail(String artistId) {
        return async(() -> {
            JsonObject root = DirectHttp.get(NeteaseEndpoints.ARTIST_DETAIL + artistId);
            JsonObject artist = ApiJson.obj(root, "artist");
            if (artist == null) {
                throw new MusicApiException("musicplayer.error.artist_detail_empty");
            }
            int songCount = artistSongCount(artistId);
            return new ArtistInfo(
                    ApiJson.str(artist, "id"),
                    ApiJson.str(artist, "name"),
                    ApiJson.str(artist, "briefDesc"),
                    songCount
            );
        });
    }

    private int artistSongCount(String artistId) throws Exception {
        JsonObject root = DirectHttp.get(NeteaseEndpoints.ARTIST_SONGS,
                "id", artistId, "limit", "1", "offset", "0");
        return ApiJson.intVal(root, "total");
    }

    @Override
    public CompletableFuture<List<SearchEntry>> artistSongsPage(String artistId, int offset, int limit) {
        return async(() -> {
            JsonObject root = DirectHttp.get(NeteaseEndpoints.ARTIST_SONGS,
                    "id", artistId,
                    "limit", Integer.toString(Math.max(1, limit)),
                    "offset", Integer.toString(Math.max(0, offset)),
                    "order", "hot", "work_type", "1", "private_cloud", "true");
            JsonArray songs = ApiJson.arr(root, "songs");
            List<SearchEntry> entries = new ArrayList<>();
            if (songs == null) {
                return entries;
            }
            for (JsonElement element : songs) {
                if (!element.isJsonObject()) continue;
                JsonObject song = element.getAsJsonObject();
                String songId = ApiJson.str(song, "id");
                // 与后端一致：副标题显示专辑名
                String albumName = ApiJson.albumName(song);
                entries.add(new SearchEntry(songId, ApiJson.str(song, "name"),
                        albumName.isBlank() ? ApiJson.firstArtistName(song) : albumName,
                        ApiCommands.playSong(songId), ""));
            }
            MusicPlayerMod.LOGGER.debug("作者歌曲分页(直连): id={}, offset={}, limit={}, 返回={}, total={}",
                    artistId, offset, limit, entries.size(), ApiJson.intVal(root, "total"));
            return entries;
        });
    }

    // ── 播客 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<RadioInfo> radioDetail(String rid) {
        return async(() -> {
            JsonObject root = DirectHttp.get(NeteaseEndpoints.RADIO_DETAIL, "id", rid);
            JsonObject data = ApiJson.obj(root, "data");
            if (data == null) {
                throw new MusicApiException("musicplayer.error.radio_detail_empty");
            }
            return new RadioInfo(
                    ApiJson.str(data, "id"),
                    ApiJson.str(data, "name"),
                    ApiJson.str(data, "desc"),
                    ApiJson.str(data, "category"),
                    ApiJson.str(data, "secondCategory"),
                    ApiJson.str(data, "picUrl"),
                    ApiJson.intVal(data, "programCount"),
                    ApiJson.intVal(data, "subCount"),
                    ApiJson.lng(data, "playCount"),
                    ApiJson.intVal(data, "radioFeeType")
            );
        });
    }

    @Override
    public CompletableFuture<List<ProgramInfo>> radioPrograms(String rid, int limit, int offset, boolean asc) {
        return async(() -> {
            JsonObject root = DirectHttp.get(NeteaseEndpoints.RADIO_PROGRAMS,
                    "radioId", rid,
                    "limit", Integer.toString(Math.max(1, limit)),
                    "offset", Integer.toString(Math.max(0, offset)),
                    "asc", Boolean.toString(asc));
            JsonArray programs = ApiJson.arr(root, "programs");
            List<ProgramInfo> entries = new ArrayList<>();
            if (programs == null) {
                return entries;
            }
            for (JsonElement element : programs) {
                if (!element.isJsonObject()) continue;
                JsonObject node = element.getAsJsonObject();
                entries.add(new ProgramInfo(
                        ApiJson.str(node, "id"),
                        ApiJson.str(node, "name"),
                        ApiJson.str(ApiJson.obj(node, "mainSong"), "id"),
                        ApiJson.lng(node, "duration"),
                        ApiJson.str(node, "coverUrl"),
                        ApiJson.str(node, "description"),
                        ApiJson.str(ApiJson.obj(node, "radio"), "id", rid),
                        ApiJson.str(ApiJson.obj(node, "radio"), "name")
                ));
            }
            return entries;
        });
    }

    @Override
    public CompletableFuture<ProgramInfo> programDetail(String programId) {
        return async(() -> {
            JsonObject root = DirectHttp.get(NeteaseEndpoints.PROGRAM_DETAIL, "id", programId);
            JsonObject program = ApiJson.obj(root, "program");
            if (program == null) {
                throw new MusicApiException("musicplayer.error.program_detail_empty");
            }
            JsonObject radio = ApiJson.obj(program, "radio");
            return new ProgramInfo(
                    ApiJson.str(program, "id"),
                    ApiJson.str(program, "name"),
                    ApiJson.str(ApiJson.obj(program, "mainSong"), "id"),
                    ApiJson.lng(program, "duration"),
                    ApiJson.str(program, "coverUrl"),
                    ApiJson.str(program, "description"),
                    ApiJson.str(radio, "id"),
                    ApiJson.str(radio, "name")
            );
        });
    }

    @Override
    public CompletableFuture<List<SearchEntry>> hotRadios(int limit, int offset) {
        return async(() -> {
            JsonObject root = DirectHttp.post(NeteaseEndpoints.RADIO_HOT, Map.of(
                    "limit", Integer.toString(Math.max(1, limit)),
                    "offset", Integer.toString(Math.max(0, offset))));
            JsonArray list = ApiJson.arr(root, "djRadios");
            if (list == null) {
                list = ApiJson.arr(root, "data");
            }
            List<SearchEntry> entries = new ArrayList<>();
            if (list == null) {
                return entries;
            }
            for (JsonElement element : list) {
                if (!element.isJsonObject()) continue;
                JsonObject node = element.getAsJsonObject();
                String rid = ApiJson.str(node, "id");
                // 该接口用小写 rcmdtext，旧接口用 rcmdText，两者都兼容
                String rcmd = ApiJson.firstNonBlank(node, "rcmdtext", "rcmdText", "desc");
                entries.add(new SearchEntry(rid, ApiJson.str(node, "name"),
                        rcmd.isBlank() ? new TranslatableComponent("musicplayer.entry.radio")
                                : new TextComponent(rcmd),
                        ApiCommands.viewRadio(rid), ""));
            }
            return entries;
        });
    }

    @Override
    public CompletableFuture<List<SearchEntry>> radioCategories() {
        return async(() -> {
            JsonObject root = DirectHttp.get(NeteaseEndpoints.RADIO_CATEGORY_RECOMMEND);
            JsonArray data = ApiJson.arr(root, "data");
            List<SearchEntry> entries = new ArrayList<>();
            if (data == null) {
                return entries;
            }
            for (JsonElement element : data) {
                if (!element.isJsonObject()) continue;
                JsonObject node = element.getAsJsonObject();
                // 该接口用 categoryId/categoryName，不是 id/name
                String categoryId = ApiJson.firstNonBlank(node, "categoryId", "id");
                JsonArray radios = ApiJson.arr(node, "radios");
                int radioCount = radios == null ? 0 : radios.size();
                entries.add(new SearchEntry(
                        categoryId,
                        ApiJson.firstNonBlank(node, "categoryName", "name"),
                        new TranslatableComponent("musicplayer.entry.radio_count", radioCount),
                        "", ""
                ));
            }
            return entries;
        });
    }

    // ── 推荐 ────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<List<TrackInfo>> randomHotTracks(int count) {
        int safeCount = Math.max(1, count);
        return async(() -> {
            Random random = new Random();
            List<String> categories = hotPlaylistCategoriesSync();
            String category = categories.isEmpty() ? "全部" : categories.get(random.nextInt(categories.size()));

            List<SearchEntry> playlists = topPlaylistsSync(category, HOT_PLAYLIST_FETCH_SIZE, 0);
            if (playlists.isEmpty() && !"全部".equals(category)) {
                playlists = topPlaylistsSync("全部", HOT_PLAYLIST_FETCH_SIZE, 0);
            }
            if (playlists.isEmpty()) {
                throw new MusicApiException("musicplayer.error.hot_playlists_failed");
            }

            Collections.shuffle(playlists, random);
            List<JsonObject> candidates = new ArrayList<>();
            int probeCount = Math.min(playlists.size(), 8);
            for (int index = 0; index < probeCount && candidates.size() < safeCount * 4; index++) {
                List<JsonObject> songs = playlistSongNodesSync(playlists.get(index).id(),
                        RANDOM_HOT_MAX_SONGS_PER_PLAYLIST);
                if (songs.isEmpty()) continue;
                Collections.shuffle(songs, random);
                int take = Math.min(songs.size(), Math.max(12, safeCount));
                for (int songIndex = 0; songIndex < take && candidates.size() < safeCount * 4; songIndex++) {
                    candidates.add(songs.get(songIndex));
                }
            }
            if (candidates.isEmpty()) {
                throw new MusicApiException("musicplayer.error.hot_playlists_no_songs");
            }

            Collections.shuffle(candidates, random);
            if (candidates.size() > safeCount) {
                candidates = new ArrayList<>(candidates.subList(0, safeCount));
            }
            return candidates;
        }).thenCompose(this::resolveNodes);
    }

    private List<String> hotPlaylistCategoriesSync() throws Exception {
        JsonObject root = DirectHttp.get(NeteaseEndpoints.PLAYLIST_HOT_TAGS);
        JsonArray tags = ApiJson.arr(root, "tags");
        List<String> categories = new ArrayList<>();
        if (tags != null) {
            for (JsonElement tag : tags) {
                if (!tag.isJsonObject()) continue;
                String name = ApiJson.str(tag.getAsJsonObject(), "name");
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

    private List<SearchEntry> topPlaylistsSync(String category, int limit, int offset) throws Exception {
        JsonObject root = DirectHttp.get(NeteaseEndpoints.PLAYLIST_LIST,
                "cat", category, "order", "hot",
                "limit", Integer.toString(limit), "offset", Integer.toString(offset));
        JsonArray playlists = ApiJson.arr(root, "playlists");
        List<SearchEntry> entries = new ArrayList<>();
        if (playlists == null) {
            return entries;
        }
        for (JsonElement element : playlists) {
            if (!element.isJsonObject()) continue;
            JsonObject playlist = element.getAsJsonObject();
            String playlistId = ApiJson.str(playlist, "id");
            JsonObject creator = ApiJson.obj(playlist, "creator");
            String ownerId = ApiJson.str(creator, "userId");
            entries.add(new SearchEntry(playlistId, ApiJson.str(playlist, "name"),
                    ApiJson.str(creator, "nickname"),
                    ApiCommands.viewPlaylist(playlistId),
                    ownerId.isBlank() ? "" : ApiCommands.viewUser(ownerId)));
        }
        return entries;
    }

    /** 从歌单详情取 trackIds，再批量补歌曲详情（限制最大条数避免大歌单全量分页）。 */
    private List<JsonObject> playlistSongNodesSync(String playlistId, int maxSongs) throws Exception {
        List<String> allIds = playlistTrackIdsSync(playlistId);
        if (allIds.isEmpty()) {
            return List.of();
        }
        int take = Math.min(allIds.size(), Math.max(1, maxSongs));
        return fetchSongNodes(allIds.subList(0, take));
    }

    // ── 内部工具 ────────────────────────────────────────────────────

    /** 把阻塞的同步逻辑放到业务线程池执行。 */
    private static <T> CompletableFuture<T> async(ThrowingSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (MusicApiException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new MusicApiException("musicplayer.error.api_request_failed", exception);
            }
        }, NeteaseApiClient.executor());
    }

    /** 解析单个歌曲节点（详情已知，只需补直链）。 */
    private CompletableFuture<TrackInfo> resolveNode(JsonObject song) {
        String songId = ApiJson.str(song, "id");
        if (songId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String title = ApiJson.str(song, "name");
        String artist = ApiJson.firstArtistName(song);
        String artistId = ApiJson.firstArtistId(song);
        String coverUrl = ApiJson.albumCover(song);
        long duration = ApiJson.durationMillis(song);
        return urlResolver.resolve(songId)
                .thenApply(urls -> new TrackInfo(songId, title, artist, artistId, coverUrl, urls, duration));
    }

    /** 并发解析一组歌曲节点，过滤掉无可用直链的条目；不阻塞线程。 */
    private CompletableFuture<List<TrackInfo>> resolveNodes(List<JsonObject> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<CompletableFuture<TrackInfo>> futures = new ArrayList<>(nodes.size());
        for (JsonObject node : nodes) {
            futures.add(resolveNode(node).exceptionally(ignored -> null));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            List<TrackInfo> tracks = new ArrayList<>(futures.size());
            for (CompletableFuture<TrackInfo> future : futures) {
                TrackInfo track = future.getNow(null);
                if (track != null && track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
                    tracks.add(track);
                }
            }
            return tracks;
        });
    }

    /** 保留后端地址，供 Mycelis 音源在直连失败时保底。 */
    private static String configuredBaseUrl() {
        String raw = MusicPlayerConfigManager.get().neteaseBaseUrl;
        if (raw == null || raw.isBlank()) {
            return MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL;
        }
        raw = raw.trim();
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    /** 允许抛出受检异常的供应者，便于在 {@code supplyAsync} 中直接调用阻塞方法。 */
    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
