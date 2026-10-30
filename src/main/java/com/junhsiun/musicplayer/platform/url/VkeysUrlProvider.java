package com.junhsiun.musicplayer.platform.url;

import com.google.gson.JsonObject;
import com.junhsiun.musicplayer.util.Java8;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.junhsiun.musicplayer.platform.url.UrlUtil.isHttpUrl;

/**
 * VKeys 第三方音源。
 *
 * <p>GET {@code https://api.vkeys.cn/v2/music/netease?id={id}&quality={q}} 返回 JSON，
 * 从 {@code data.url} 读取直链。该源稳定性较差，仅作为中间顺位尝试。
 */
public final class VkeysUrlProvider implements SongUrlProvider {
    private static final Logger LOGGER = LogManager.getLogger(VkeysUrlProvider.class);
    private static final String BASE_URL = "https://api.vkeys.cn/v2/music/netease";
    private static final String[] QUALITIES = {"4", "3", "2"};

    private final SongUrlHttp http;

    public VkeysUrlProvider() {
        this(ApiSongUrlHttp.INSTANCE);
    }

    /** 供测试注入假的 HTTP 实现。 */
    public VkeysUrlProvider(SongUrlHttp http) {
        this.http = http;
    }

    @Override
    public String name() {
        return "VKEYS";
    }

    @Override
    public CompletableFuture<List<String>> resolve(String songId) {
        return resolve(songId, SongUrlResolver.PROVIDER_TIMEOUT_SECONDS);
    }

    @Override
    public CompletableFuture<List<String>> resolve(String songId, int timeoutSeconds) {
        return com.junhsiun.musicplayer.platform.NeteaseApiClient.submitProbe(() -> fetch(songId), timeoutSeconds);
    }

    private List<String> fetch(String songId) {
        long deadline = UrlUtil.providerDeadline();
        for (String quality : QUALITIES) {
            if (UrlUtil.isProviderBudgetExhausted(deadline)) {
                break;
            }
            try {
                JsonObject root = http.getJson(BASE_URL, "id", songId, "quality", quality);
                JsonObject data = root != null && root.has("data") && root.get("data").isJsonObject()
                        ? root.getAsJsonObject("data") : null;
                String url = data != null && data.has("url") && data.get("url").isJsonPrimitive()
                        ? data.get("url").getAsString().trim() : null;
                if (isHttpUrl(url)) {
                    return Java8.listOf(url.trim());
                }
            } catch (Exception e) {
                LOGGER.trace("VKEYS 音源失败 id={} quality={}: {}", songId, quality, UrlUtil.rootMessage(e));
            }
        }
        return Java8.listOf();
    }
}
