package com.junhsiun.musicplayer.platform.url;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.junhsiun.musicplayer.platform.url.UrlUtil.firstUrl;
import static com.junhsiun.musicplayer.platform.url.UrlUtil.isHttpUrl;

/**
 * Mycelis 官方代理音源（保底）。
 *
 * <p>GET {@code {base}/song/url/v1?id={id}&level={level}} 从 {@code data[].url} 读取直链。
 * 该源无法完整播放 VIP 歌曲（可能仅返回前 30 秒试听），故仅作为最后保底。
 */
public final class MycelisUrlProvider implements SongUrlProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(MycelisUrlProvider.class);
    private static final String[] LEVELS = {"lossless", "exhigh", "higher", "standard"};

    private final SongUrlHttp http;
    private final Supplier<String> baseUrlSupplier;

    public MycelisUrlProvider() {
        this(ApiSongUrlHttp.INSTANCE, () -> {
            String raw = com.junhsiun.musicplayer.config.MusicPlayerConfigManager.get().neteaseBaseUrl;
            return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        });
    }

    /** 供测试注入假的 HTTP 实现与独立的基础地址。 */
    public MycelisUrlProvider(SongUrlHttp http, Supplier<String> baseUrlSupplier) {
        this.http = http;
        this.baseUrlSupplier = baseUrlSupplier;
    }

    @Override
    public String name() {
        return "Mycelis";
    }

    @Override
    public CompletableFuture<List<String>> resolve(String songId) {
        return CompletableFuture.supplyAsync(() -> fetch(songId));
    }

    private List<String> fetch(String songId) {
        String base = baseUrlSupplier.get();
        for (String level : LEVELS) {
            try {
                JsonObject root = http.getJson(base + "/song/url/v1", "id", songId, "level", level);
                String url = firstUrl(root);
                if (isHttpUrl(url)) {
                    return List.of(url.trim());
                }
            } catch (Exception e) {
                LOGGER.trace("Mycelis 音源失败 id={} level={}: {}", songId, level, UrlUtil.rootMessage(e));
            }
        }
        return List.of();
    }
}
