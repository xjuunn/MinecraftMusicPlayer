package com.junhsiun.musicplayer.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.model.ArtistInfo;
import com.junhsiun.musicplayer.model.PlaylistInfo;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.model.UserPlaylistView;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class NeteaseApiClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DETAIL_FETCH_BATCH_SIZE = 100;
    private static final int HOT_PLAYLIST_FETCH_SIZE = 30;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "musicplayer-http");
        thread.setDaemon(true);
        return thread;
    });

    public CompletableFuture<TrackInfo> resolveSong(String id) {
        return songDetail(id).thenCompose(detail ->
                songUrls(id).thenApply(urls -> new TrackInfo(detail.id(), detail.title(), detail.artist(), detail.artistId(), detail.coverUrl(), urls, detail.durationMillis()))
        );
    }

    public CompletableFuture<List<SearchEntry>> searchSongs(String keyword) {
        return searchSongs(keyword, 1);
    }

    public CompletableFuture<List<SearchEntry>> searchSongs(String keyword, int page) {
        return search(keyword, 1, page);
    }

    public CompletableFuture<List<SearchEntry>> searchArtists(String keyword) {
        return searchArtists(keyword, 1);
    }

    public CompletableFuture<List<SearchEntry>> searchArtists(String keyword, int page) {
        return search(keyword, 100, page);
    }

    public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword) {
        return searchPlaylists(keyword, 1);
    }

    public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword, int page) {
        return search(keyword, 1000, page);
    }

    public CompletableFuture<List<SearchEntry>> searchUsers(String keyword) {
        return searchUsers(keyword, 1);
    }

    public CompletableFuture<List<SearchEntry>> searchUsers(String keyword, int page) {
        return search(keyword, 1002, page);
    }

    public CompletableFuture<PlaylistInfo> playlistDetail(String id) {
        return getJson("/playlist/detail", "id", id).thenCompose(root -> {
            JsonNode playlist = root.path("playlist");
            return fetchAllPlaylistTracks(id).thenApply(tracks -> new PlaylistInfo(
                    playlist.path("id").asText(),
                    playlist.path("name").asText(),
                    playlist.path("creator").path("userId").asText(),
                    playlist.path("creator").path("nickname").asText(),
                    tracks
            ));
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
                JsonNode root = executeJson(baseRequest(
                        baseUrl() + "/user/playlist",
                        new String[]{"uid", userId, "limit", Integer.toString(DETAIL_FETCH_BATCH_SIZE), "offset", Integer.toString(offset)},
                        "application/json,text/plain,*/*"
                ));
                JsonNode playlists = root.path("playlist");
                if (!playlists.isArray() || playlists.isEmpty()) {
                    break;
                }
                for (JsonNode playlist : playlists) {
                    JsonNode creator = playlist.path("creator");
                    if (nickname.isBlank()) {
                        resolvedUserId = creator.path("userId").asText(userId);
                        nickname = creator.path("nickname").asText("");
                        signature = creator.path("signature").asText("");
                    }
                    String playlistId = playlist.path("id").asText();
                    entries.add(new SearchEntry(
                            playlistId,
                            playlist.path("name").asText(),
                            "共 " + playlist.path("trackCount").asInt() + " 首",
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
        }, EXECUTOR);
    }

    public CompletableFuture<ArtistInfo> artistDetail(String artistId) {
        return getJson("/artists", "id", artistId).thenCompose(root -> {
            JsonNode artist = root.path("artist");
            return fetchAllArtistSongs(artistId).thenApply(tracks -> new ArtistInfo(
                    artist.path("id").asText(),
                    artist.path("name").asText(),
                    artist.path("briefDesc").asText(""),
                    tracks
            ));
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
                throw new IllegalStateException("无法获取热门歌单。");
            }

            Collections.shuffle(playlists, random);
            LinkedHashSet<String> songIds = new LinkedHashSet<>();
            int playlistProbeCount = Math.min(playlists.size(), 8);
            for (int index = 0; index < playlistProbeCount && songIds.size() < safeCount * 4; index++) {
                SearchEntry playlist = playlists.get(index);
                List<SearchEntry> tracks = fetchAllPlaylistTracksSync(playlist.id());
                if (tracks.isEmpty()) {
                    continue;
                }
                Collections.shuffle(tracks, random);
                int take = Math.min(tracks.size(), Math.max(12, safeCount));
                for (int trackIndex = 0; trackIndex < take; trackIndex++) {
                    String songId = tracks.get(trackIndex).id();
                    if (songId != null && !songId.isBlank()) {
                        songIds.add(songId);
                    }
                    if (songIds.size() >= safeCount * 4) {
                        break;
                    }
                }
            }

            if (songIds.isEmpty()) {
                throw new IllegalStateException("热门歌单中没有可用歌曲。");
            }

            List<String> selectedSongIds = new ArrayList<>(songIds);
            Collections.shuffle(selectedSongIds, random);
            if (selectedSongIds.size() > safeCount) {
                selectedSongIds = new ArrayList<>(selectedSongIds.subList(0, safeCount));
            }

            List<CompletableFuture<TrackInfo>> futures = selectedSongIds.stream()
                    .map(this::resolveSong)
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

            List<TrackInfo> tracks = new ArrayList<>();
            for (CompletableFuture<TrackInfo> future : futures) {
                try {
                    TrackInfo track = future.join();
                    if (track != null && track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
                        tracks.add(track);
                    }
                } catch (Exception ignored) {
                }
            }
            if (tracks.isEmpty()) {
                throw new IllegalStateException("随机热门歌曲解析失败。");
            }
            return tracks;
        }, EXECUTOR);
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
            JsonNode result = root.path("result");
            JsonNode list = switch (type) {
                case 1 -> result.path("songs");
                case 100 -> result.path("artists");
                case 1000 -> result.path("playlists");
                case 1002 -> result.path("userprofiles");
                default -> MAPPER.createArrayNode();
            };
            if (!list.isArray()) {
                return entries;
            }

            for (JsonNode node : list) {
                if (type == 1) {
                    String songId = node.path("id").asText();
                    String artistId = firstArtistId(node);
                    entries.add(new SearchEntry(
                            songId,
                            node.path("name").asText(),
                            firstArtistName(node),
                            playSongCommand(songId),
                            artistId.isBlank() ? "" : viewArtistCommand(artistId)
                    ));
                } else if (type == 100) {
                    String artistId = node.path("id").asText();
                    entries.add(new SearchEntry(
                            artistId,
                            node.path("name").asText(),
                            "作者",
                            viewArtistCommand(artistId),
                            ""
                    ));
                } else if (type == 1000) {
                    String playlistId = node.path("id").asText();
                    String ownerId = node.path("creator").path("userId").asText("");
                    entries.add(new SearchEntry(
                            playlistId,
                            node.path("name").asText(),
                            node.path("creator").path("nickname").asText(""),
                            viewPlaylistCommand(playlistId),
                            ownerId.isBlank() ? "" : viewUserCommand(ownerId)
                    ));
                } else if (type == 1002) {
                    String foundUserId = node.path("userId").asText();
                    entries.add(new SearchEntry(
                            foundUserId,
                            node.path("nickname").asText(),
                            node.path("signature").asText(""),
                            viewUserCommand(foundUserId),
                            ""
                    ));
                }
            }
            return entries;
        });
    }

    private CompletableFuture<TrackInfo> songDetail(String id) {
        return getJson("/song/detail", "ids", id).thenApply(root -> {
            JsonNode song = root.path("songs").get(0);
            return new TrackInfo(
                    song.path("id").asText(),
                    song.path("name").asText(),
                    firstArtistName(song),
                    firstArtistId(song),
                    songCoverUrl(song),
                    List.of(),
                    song.path("dt").asLong(0L)
            );
        });
    }

    private CompletableFuture<List<String>> songUrls(String id) {
        CompletableFuture<String> vkeys320 = getJsonFromAbsoluteUrl("https://api.vkeys.cn/v2/music/netease", "id", id, "quality", "4")
                .thenApply(root -> root.path("data").path("url").asText(null))
                .exceptionally(throwable -> null);
        CompletableFuture<String> vkeys192 = getJsonFromAbsoluteUrl("https://api.vkeys.cn/v2/music/netease", "id", id, "quality", "3")
                .thenApply(root -> root.path("data").path("url").asText(null))
                .exceptionally(throwable -> null);
        CompletableFuture<String> vkeys128 = getJsonFromAbsoluteUrl("https://api.vkeys.cn/v2/music/netease", "id", id, "quality", "2")
                .thenApply(root -> root.path("data").path("url").asText(null))
                .exceptionally(throwable -> null);
        CompletableFuture<String> byfunsExhigh = getTextFromAbsoluteUrl("https://api.byfuns.top/1/", "id", id, "level", "exhigh")
                .exceptionally(throwable -> null);
        CompletableFuture<String> byfunsHigher = getTextFromAbsoluteUrl("https://api.byfuns.top/1/", "id", id, "level", "higher")
                .exceptionally(throwable -> null);
        CompletableFuture<String> byfunsStandard = getTextFromAbsoluteUrl("https://api.byfuns.top/1/", "id", id, "level", "standard")
                .exceptionally(throwable -> null);
        CompletableFuture<String> standardUrl = getJson("/song/url/v1", "id", id, "level", "standard")
                .thenApply(root -> firstUrl(root.path("data")))
                .exceptionally(throwable -> null);
        CompletableFuture<String> higherUrl = getJson("/song/url/v1", "id", id, "level", "higher")
                .thenApply(root -> firstUrl(root.path("data")))
                .exceptionally(throwable -> null);
        CompletableFuture<String> exhighUrl = getJson("/song/url/v1", "id", id, "level", "exhigh")
                .thenApply(root -> firstUrl(root.path("data")))
                .exceptionally(throwable -> null);
        CompletableFuture<String> legacyUrl = getJson("/song/url", "id", id)
                .thenApply(root -> firstUrl(root.path("data")))
                .exceptionally(throwable -> null);
        CompletableFuture<String> directUrl = CompletableFuture.completedFuture("https://music.163.com/song/media/outer/url?id=" + id + ".mp3");

        return CompletableFuture.allOf(
                vkeys320, vkeys192, vkeys128,
                byfunsExhigh, byfunsHigher, byfunsStandard,
                standardUrl, higherUrl, exhighUrl, legacyUrl, directUrl
        ).thenApply(ignored -> {
            Set<String> urls = new LinkedHashSet<>();
            addCandidate(urls, byfunsExhigh.join());
            addCandidate(urls, byfunsHigher.join());
            addCandidate(urls, byfunsStandard.join());
            addCandidate(urls, vkeys320.join());
            addCandidate(urls, vkeys192.join());
            addCandidate(urls, vkeys128.join());
            addCandidate(urls, standardUrl.join());
            addCandidate(urls, higherUrl.join());
            addCandidate(urls, exhighUrl.join());
            addCandidate(urls, legacyUrl.join());
            addCandidate(urls, directUrl.join());

            if (urls.isEmpty()) {
                throw new IllegalStateException("无法获取任何可用的歌曲播放源，请检查音乐 API 服务。");
            }
            return List.copyOf(urls);
        });
    }

    private CompletableFuture<JsonNode> getJson(String path, String... queryPairs) {
        return getJsonFromAbsoluteUrl(baseUrl() + path, queryPairs);
    }

    private CompletableFuture<List<SearchEntry>> fetchAllPlaylistTracks(String playlistId) {
        return CompletableFuture.supplyAsync(() -> fetchAllPlaylistTracksSync(playlistId), EXECUTOR);
    }

    private CompletableFuture<List<SearchEntry>> fetchAllArtistSongs(String artistId) {
        return CompletableFuture.supplyAsync(() -> {
            List<SearchEntry> tracks = new ArrayList<>();
            int offset = 0;

            while (true) {
                JsonNode root = executeJson(baseRequest(
                        baseUrl() + "/artist/songs",
                        new String[]{"id", artistId, "limit", Integer.toString(DETAIL_FETCH_BATCH_SIZE), "offset", Integer.toString(offset)},
                        "application/json,text/plain,*/*"
                ));
                JsonNode songs = root.path("songs");
                if (!songs.isArray() || songs.isEmpty()) {
                    break;
                }
                for (JsonNode song : songs) {
                    String songId = song.path("id").asText();
                    tracks.add(new SearchEntry(
                            songId,
                            song.path("name").asText(),
                            song.path("al").path("name").asText(""),
                            playSongCommand(songId),
                            ""
                    ));
                }
                if (songs.size() < DETAIL_FETCH_BATCH_SIZE) {
                    break;
                }
                offset += songs.size();
            }

            return tracks;
        }, EXECUTOR);
    }

    private List<String> fetchHotPlaylistCategories() {
        JsonNode root = executeJson(baseRequest(
                baseUrl() + "/playlist/hot",
                new String[0],
                "application/json,text/plain,*/*"
        ));
        JsonNode tags = root.path("tags");
        List<String> categories = new ArrayList<>();
        if (tags.isArray()) {
            for (JsonNode tag : tags) {
                String name = tag.path("name").asText("");
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
        JsonNode root = executeJson(baseRequest(
                baseUrl() + "/top/playlist",
                new String[]{
                        "order", "hot",
                        "cat", category,
                        "limit", Integer.toString(limit),
                        "offset", Integer.toString(offset)
                },
                "application/json,text/plain,*/*"
        ));
        JsonNode playlists = root.path("playlists");
        List<SearchEntry> entries = new ArrayList<>();
        if (!playlists.isArray()) {
            return entries;
        }
        for (JsonNode playlist : playlists) {
            String playlistId = playlist.path("id").asText();
            String ownerId = playlist.path("creator").path("userId").asText("");
            entries.add(new SearchEntry(
                    playlistId,
                    playlist.path("name").asText(""),
                    playlist.path("creator").path("nickname").asText(""),
                    viewPlaylistCommand(playlistId),
                    ownerId.isBlank() ? "" : viewUserCommand(ownerId)
            ));
        }
        return entries;
    }

    private List<SearchEntry> fetchAllPlaylistTracksSync(String playlistId) {
        List<SearchEntry> tracks = new ArrayList<>();
        int offset = 0;

        while (true) {
            JsonNode root = executeJson(baseRequest(
                    baseUrl() + "/playlist/track/all",
                    new String[]{"id", playlistId, "limit", Integer.toString(DETAIL_FETCH_BATCH_SIZE), "offset", Integer.toString(offset)},
                    "application/json,text/plain,*/*"
            ));
            JsonNode songs = root.path("songs");
            if (!songs.isArray() || songs.isEmpty()) {
                break;
            }
            for (JsonNode song : songs) {
                String songId = song.path("id").asText();
                String artistId = firstArtistId(song);
                tracks.add(new SearchEntry(
                        songId,
                        song.path("name").asText(),
                        firstArtistName(song),
                        playSongCommand(songId),
                        artistId.isBlank() ? "" : viewArtistCommand(artistId)
                ));
            }
            if (songs.size() < DETAIL_FETCH_BATCH_SIZE) {
                break;
            }
            offset += songs.size();
        }

        return tracks;
    }

    private CompletableFuture<JsonNode> getJsonFromAbsoluteUrl(String absoluteUrl, String... queryPairs) {
        return CompletableFuture.supplyAsync(() -> {
            Request request = baseRequest(absoluteUrl, queryPairs, "application/json,text/plain,*/*");
            return executeJson(request);
        }, EXECUTOR);
    }

    private CompletableFuture<String> getTextFromAbsoluteUrl(String absoluteUrl, String... queryPairs) {
        return CompletableFuture.supplyAsync(() -> {
            Request request = baseRequest(absoluteUrl, queryPairs, "text/plain,*/*");
            return executeText(request);
        }, EXECUTOR);
    }

    private static Request baseRequest(String absoluteUrl, String[] queryPairs, String accept) {
        HttpUrl.Builder builder = Objects.requireNonNull(HttpUrl.parse(absoluteUrl)).newBuilder();
        for (int index = 0; index + 1 < queryPairs.length; index += 2) {
            builder.addQueryParameter(queryPairs[index], queryPairs[index + 1]);
        }
        return new Request.Builder()
                .url(builder.build())
                .header("User-Agent", "MinecraftMusicPlayer/2.0")
                .header("Accept", accept)
                .get()
                .build();
    }

    private JsonNode executeJson(Request request) {
        OkHttpClient client = HttpClientFactory.create();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            String body = Objects.requireNonNull(response.body()).string();
            return MAPPER.readTree(body);
        } catch (Exception exception) {
            MusicPlayerMod.LOGGER.warn("请求音乐接口失败: {}", request.url(), exception);
            throw new RuntimeException(exception);
        }
    }

    private String executeText(Request request) {
        OkHttpClient client = HttpClientFactory.create();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            String body = Objects.requireNonNull(response.body()).string().trim();
            if (body.isBlank() || body.startsWith("404 ")) {
                return null;
            }
            return body;
        } catch (Exception exception) {
            MusicPlayerMod.LOGGER.warn("请求音乐接口失败: {}", request.url(), exception);
            throw new RuntimeException(exception);
        }
    }

    private static String baseUrl() {
        String raw = MusicPlayerConfigManager.get().neteaseBaseUrl;
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private static void addCandidate(Set<String> urls, String url) {
        if (url == null || url.isBlank() || "null".equalsIgnoreCase(url)) {
            return;
        }
        String normalized = url.trim();
        String lower = normalized.toLowerCase();
        if (lower.contains("musicrep-ts") || lower.contains("jd-musicrep-ts")) {
            return;
        }
        if (!(lower.contains(".mp3") || lower.contains("type=mp3") || lower.contains("encodetype=mp3"))) {
            return;
        }
        urls.add(normalized);
    }

    private static String firstArtistName(JsonNode songNode) {
        JsonNode artists = songNode.path("ar");
        if (artists.isArray() && !artists.isEmpty()) {
            return artists.get(0).path("name").asText("");
        }
        JsonNode artistsAlt = songNode.path("artists");
        if (artistsAlt.isArray() && !artistsAlt.isEmpty()) {
            return artistsAlt.get(0).path("name").asText("");
        }
        return "";
    }

    private static String firstArtistId(JsonNode songNode) {
        JsonNode artists = songNode.path("ar");
        if (artists.isArray() && !artists.isEmpty()) {
            return artists.get(0).path("id").asText("");
        }
        JsonNode artistsAlt = songNode.path("artists");
        if (artistsAlt.isArray() && !artistsAlt.isEmpty()) {
            return artistsAlt.get(0).path("id").asText("");
        }
        return "";
    }

    private static String firstUrl(JsonNode data) {
        if (data.isArray() && !data.isEmpty()) {
            return data.get(0).path("url").asText(null);
        }
        return null;
    }

    private static String songCoverUrl(JsonNode songNode) {
        String albumCover = songNode.path("al").path("picUrl").asText("");
        if (!albumCover.isBlank()) {
            return albumCover;
        }
        String albumCoverAlt = songNode.path("album").path("picUrl").asText("");
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
}
