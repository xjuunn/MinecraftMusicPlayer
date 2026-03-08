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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    private final OkHttpClient client = new OkHttpClient.Builder().build();

    public CompletableFuture<TrackInfo> resolveSong(String id) {
        return songDetail(id).thenCompose(detail ->
                songUrl(id).thenApply(url -> new TrackInfo(detail.id(), detail.title(), detail.artist(), url, detail.durationMillis()))
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
                    "",
                    song.path("dt").asLong(0L)
            );
        });
    }

    private CompletableFuture<String> songUrl(String id) {
        return getJson("/song/url/v1", "id", id, "level", "standard")
                .thenApply(root -> firstUrl(root.path("data")))
                .thenCompose(url -> {
                    if (url != null && !url.isBlank() && !"null".equals(url)) {
                        return CompletableFuture.completedFuture(url);
                    }
                    return getJson("/song/url", "id", id).thenApply(root -> firstUrl(root.path("data")));
                })
                .thenApply(url -> {
                    if (url == null || url.isBlank() || "null".equals(url)) {
                        throw new IllegalStateException("无法获取歌曲播放地址，请检查网易云 API 服务。");
                    }
                    return url;
                });
    }

    private CompletableFuture<JsonNode> getJson(String path, String... queryPairs) {
        return CompletableFuture.supplyAsync(() -> {
            HttpUrl.Builder builder = Objects.requireNonNull(HttpUrl.parse(baseUrl() + path)).newBuilder();
            for (int index = 0; index + 1 < queryPairs.length; index += 2) {
                builder.addQueryParameter(queryPairs[index], queryPairs[index + 1]);
            }
            Request request = new Request.Builder().url(builder.build()).get().build();
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
        }, EXECUTOR);
    }

    private static String baseUrl() {
        String raw = MusicPlayerConfigManager.get().neteaseBaseUrl;
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
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