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
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.junhsiun.musicplayer.util.HttpClientFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NeteaseApiClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "musicplayer-http");
        thread.setDaemon(true);
        return thread;
    });

    public CompletableFuture<TrackInfo> resolveSong(String id) {
        return songDetail(id).thenCompose(detail ->
                songUrls(id).thenApply(urls -> new TrackInfo(detail.id(), detail.title(), detail.artist(), urls, detail.durationMillis()))
        );
    }

    public CompletableFuture<List<SearchEntry>> searchSongs(String keyword) {
        return search(keyword, 1);
    }

    public CompletableFuture<List<SearchEntry>> searchArtists(String keyword) {
        return search(keyword, 100);
    }

    public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword) {
        return search(keyword, 1000);
    }

    public CompletableFuture<List<SearchEntry>> searchUsers(String keyword) {
        return search(keyword, 1002);
    }

    public CompletableFuture<PlaylistInfo> playlistDetail(String id) {
        return getJson("/playlist/detail", "id", id).thenApply(root -> {
            JsonNode playlist = root.path("playlist");
            List<SearchEntry> tracks = new ArrayList<>();
            JsonNode trackNodes = playlist.path("tracks");
            int limit = Math.min(trackNodes.size(), MusicPlayerConfigManager.get().playlistQueueLimit);
            for (int index = 0; index < limit; index++) {
                JsonNode track = trackNodes.get(index);
                tracks.add(new SearchEntry(
                        track.path("id").asText(),
                        track.path("name").asText(),
                        firstArtist(track)
                ));
            }
            return new PlaylistInfo(
                    playlist.path("id").asText(),
                    playlist.path("name").asText(),
                    playlist.path("creator").path("userId").asText(),
                    playlist.path("creator").path("nickname").asText(),
                    tracks
            );
        });
    }

    public CompletableFuture<UserPlaylistView> userPlaylists(String userId) {
        return getJson("/user/playlist", "uid", userId).thenApply(root -> {
            JsonNode playlists = root.path("playlist");
            if (!playlists.isArray() || playlists.isEmpty()) {
                return new UserPlaylistView(userId, "", "", List.of());
            }
            JsonNode creator = playlists.get(0).path("creator");
            List<SearchEntry> entries = new ArrayList<>();
            for (JsonNode playlist : playlists) {
                entries.add(new SearchEntry(
                        playlist.path("id").asText(),
                        playlist.path("name").asText(),
                        "共 " + playlist.path("trackCount").asInt() + " 首"
                ));
            }
            return new UserPlaylistView(
                    creator.path("userId").asText(),
                    creator.path("nickname").asText(),
                    creator.path("signature").asText(""),
                    entries
            );
        });
    }

    public CompletableFuture<ArtistInfo> artistDetail(String artistId) {
        return getJson("/artists", "id", artistId).thenApply(root -> {
            JsonNode artist = root.path("artist");
            JsonNode hotSongs = root.path("hotSongs");
            List<SearchEntry> tracks = new ArrayList<>();
            int limit = Math.min(hotSongs.size(), 10);
            for (int index = 0; index < limit; index++) {
                JsonNode song = hotSongs.get(index);
                tracks.add(new SearchEntry(
                        song.path("id").asText(),
                        song.path("name").asText(),
                        song.path("al").path("name").asText("")
                ));
            }
            return new ArtistInfo(
                    artist.path("id").asText(),
                    artist.path("name").asText(),
                    artist.path("briefDesc").asText(""),
                    tracks
            );
        });
    }

    private CompletableFuture<List<SearchEntry>> search(String keyword, int type) {
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        return getJson("/cloudsearch", "limit", Integer.toString(config.searchLimit), "type", Integer.toString(type), "keywords", keyword)
                .thenApply(root -> {
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
                            entries.add(new SearchEntry(node.path("id").asText(), node.path("name").asText(), firstArtist(node)));
                        } else if (type == 100) {
                            entries.add(new SearchEntry(node.path("id").asText(), node.path("name").asText(), "作者"));
                        } else if (type == 1000) {
                            entries.add(new SearchEntry(node.path("id").asText(), node.path("name").asText(), node.path("creator").path("nickname").asText("")));
                        } else if (type == 1002) {
                            entries.add(new SearchEntry(node.path("userId").asText(), node.path("nickname").asText(), node.path("signature").asText("")));
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
                    firstArtist(song),
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

        return CompletableFuture.allOf(vkeys320, vkeys192, vkeys128, byfunsExhigh, byfunsHigher, byfunsStandard,
                        standardUrl, higherUrl, exhighUrl, legacyUrl, directUrl)
                .thenApply(ignored -> {
                    Set<String> urls = new LinkedHashSet<>();
                    addCandidate(urls, vkeys320.join());
                    addCandidate(urls, vkeys192.join());
                    addCandidate(urls, vkeys128.join());
                    addCandidate(urls, byfunsExhigh.join());
                    addCandidate(urls, byfunsHigher.join());
                    addCandidate(urls, byfunsStandard.join());
                    addCandidate(urls, standardUrl.join());
                    addCandidate(urls, higherUrl.join());
                    addCandidate(urls, exhighUrl.join());
                    addCandidate(urls, legacyUrl.join());
                    addCandidate(urls, directUrl.join());

                    if (urls.isEmpty()) {
                        throw new IllegalStateException("无法获取任何可用的歌曲播放源，请检查网易云 API 服务。");
                    }
                    return List.copyOf(urls);
                });
    }

    private CompletableFuture<JsonNode> getJson(String path, String... queryPairs) {
        return getJsonFromAbsoluteUrl(baseUrl() + path, queryPairs);
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
            MusicPlayerMod.LOGGER.warn("请求网易云接口失败: {}", request.url(), exception);
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
            MusicPlayerMod.LOGGER.warn("请求网易云接口失败: {}", request.url(), exception);
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
        if (!(lower.contains(".mp3") || lower.contains("type=mp3") || lower.contains("encodeType=mp3"))) {
            return;
        }
        urls.add(normalized);
    }

    private static String firstArtist(JsonNode songNode) {
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

    private static String firstUrl(JsonNode data) {
        if (data.isArray() && !data.isEmpty()) {
            return data.get(0).path("url").asText(null);
        }
        return null;
    }
}
