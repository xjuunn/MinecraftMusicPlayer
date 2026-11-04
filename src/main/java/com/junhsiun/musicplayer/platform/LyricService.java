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
                        .header("User-Agent", "MinecraftMusicPlayer/" + com.junhsiun.musicplayer.MusicPlayerMod.VERSION)
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
        }, NeteaseApiClient.probeExecutor());
    }

    private CompletableFuture<List<LyricLine>> fetchFromFallback(String songId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                OkHttpClient client = HttpClientFactory.createApiClient();
                HttpUrl.Builder builder = HttpUrl.parse(FALLBACK_URL).newBuilder()
                        .addQueryParameter("id", songId);
                Request request = new Request.Builder()
                        .url(builder.build())
                        .header("User-Agent", "MinecraftMusicPlayer/" + com.junhsiun.musicplayer.MusicPlayerMod.VERSION)
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
        }, NeteaseApiClient.probeExecutor());
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

    /**
     * 歌词时间轴该走多远。
     *
     * <p>服务端一发 {@code play} 就开始计时，但客户端还要把整首曲子下载完才出声，
     * 这段静默期里如果照常按墙钟推进，歌词就会比声音先跑。所以：</p>
     * <ul>
     *   <li>客户端已经上报过真实位置 → <b>只信它</b>（它反映的是声卡真实播放进度）；</li>
     *   <li>客户端刚收到 play、还没出声 → 返回 {@code 0}，歌词停在第一句之前；</li>
     *   <li>一直没收到回报（无模组客户端）→ 过了宽限期回退到服务端计时。</li>
     * </ul>
     *
     * @param started         客户端是否已经确认出声
     * @param clientPosition  客户端上报的位置（毫秒），{@code < 0} 表示还没收到
     * @param clientAgeMillis 距上次上报过了多久（毫秒）
     * @param wallClockMillis 服务端从发出 play 算起的墙钟时间（毫秒）
     * @param graceMillis     等不到上报时的宽限时间（毫秒）
     */
    public static long resolveElapsedMillis(boolean started, long clientPosition, long clientAgeMillis,
                                            long wallClockMillis, long graceMillis) {
        if (started && clientPosition >= 0L) {
            return Math.max(0L, clientPosition + Math.max(0L, clientAgeMillis));
        }
        if (!started && wallClockMillis < graceMillis) {
            return 0L;
        }
        if (clientPosition >= 0L) {
            return Math.max(0L, Math.max(wallClockMillis, clientPosition + Math.max(0L, clientAgeMillis)));
        }
        return Math.max(0L, wallClockMillis);
    }
}
