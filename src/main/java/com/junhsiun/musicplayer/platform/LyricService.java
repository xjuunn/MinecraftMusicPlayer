package com.junhsiun.musicplayer.platform;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.model.LyricLine;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricService {
    private static final String PRIMARY_URL = "https://api.qijieya.cn/meting/";
    private static final String FALLBACK_URL = "https://mycelis.dpdns.org/lyric";
    private static final Pattern LRC_LINE = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]\\s*(.*)");
    private static final Pattern LRC_TAG = Pattern.compile("\\[(\\w+):(.*)]");

    public CompletableFuture<List<LyricLine>> fetchLyrics(String songId) {
        return fetchFromPrimary(songId)
                .thenCompose(lines -> {
                    if (lines != null) return CompletableFuture.completedFuture(lines);
                    return fetchFromFallback(songId);
                })
                .thenApply(lines -> lines != null ? lines : List.of());
    }

    private CompletableFuture<List<LyricLine>> fetchFromPrimary(String songId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                OkHttpClient client = HttpClientFactory.createApiClient();
                HttpUrl.Builder builder = HttpUrl.parse(PRIMARY_URL).newBuilder()
                        .addQueryParameter("type", "lrc")
                        .addQueryParameter("id", songId);
                Request request = new Request.Builder()
                        .url(builder.build())
                        .header("User-Agent", "MinecraftMusicPlayer/2.0")
                        .get()
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) return null;
                    String body = response.body() != null ? response.body().string() : "";
                    MusicPlayerMod.LOGGER.debug("歌词(primary) 响应长度: {} 字符", body.length());
                    return parseLrc(body);
                }
            } catch (Exception e) {
                MusicPlayerMod.LOGGER.warn("歌词(primary) 请求失败: {}", e.getMessage());
                return null;
            }
        });
    }

    private CompletableFuture<List<LyricLine>> fetchFromFallback(String songId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                OkHttpClient client = HttpClientFactory.createApiClient();
                HttpUrl.Builder builder = HttpUrl.parse(FALLBACK_URL).newBuilder()
                        .addQueryParameter("id", songId);
                Request request = new Request.Builder()
                        .url(builder.build())
                        .header("User-Agent", "MinecraftMusicPlayer/2.0")
                        .header("Accept", "application/json,text/plain,*/*")
                        .get()
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) return null;
                    String body = response.body() != null ? response.body().string() : "";
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    if (root.get("code") == null || !root.get("code").isJsonPrimitive() || root.get("code").getAsInt() != 200) return null;
                    String lyric = "";
                    if (root.has("lrc") && root.get("lrc").isJsonObject()) {
                        JsonElement lrcElement = root.getAsJsonObject("lrc").get("lyric");
                        lyric = lrcElement != null && lrcElement.isJsonPrimitive() ? lrcElement.getAsString() : "";
                    }
                    if (lyric == null || lyric.isBlank()) return null;
                    MusicPlayerMod.LOGGER.debug("歌词(fallback) 响应长度: {} 字符", lyric.length());
                    return parseLrc(lyric);
                }
            } catch (Exception e) {
                MusicPlayerMod.LOGGER.warn("歌词(fallback) 请求失败: {}", e.getMessage());
                return null;
            }
        });
    }

    public static List<LyricLine> parseLrc(String lrcText) {
        if (lrcText == null || lrcText.isBlank()) return List.of();
        List<LyricLine> lines = new ArrayList<>();
        for (String rawLine : lrcText.split("\n")) {
            String line = rawLine.trim();
            if (line.isBlank()) continue;
            if (line.startsWith("[") && !line.contains("]")) continue;
            Matcher matcher = LRC_LINE.matcher(line);
            if (matcher.matches()) {
                try {
                    long minutes = Long.parseLong(matcher.group(1));
                    double seconds = Double.parseDouble(matcher.group(2));
                    long millis = minutes * 60_000L + (long) (seconds * 1000);
                    String text = matcher.group(3).trim();
                    if (!text.isEmpty()) {
                        lines.add(new LyricLine(millis, text));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        lines.sort((a, b) -> Long.compare(a.timeMillis(), b.timeMillis()));
        return lines;
    }

    public static LyricLine findCurrentLine(List<LyricLine> lines, long elapsedMillis) {
        if (lines == null || lines.isEmpty()) return null;
        LyricLine best = null;
        for (LyricLine line : lines) {
            if (line.timeMillis() <= elapsedMillis) {
                best = line;
            } else {
                break;
            }
        }
        return best;
    }
}
