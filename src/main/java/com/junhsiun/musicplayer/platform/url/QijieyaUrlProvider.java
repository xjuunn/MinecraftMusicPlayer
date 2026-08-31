package com.junhsiun.musicplayer.platform.url;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Qijieya 第三方音源。
 *
 * <p>GET {@code https://api.qijieya.cn/meting/?type=url&id={id}} 直接返回音频流
 * （Content-Type 为 audio/mpeg），因此将原始 URL 作为可播放源返回，支持 VIP 歌曲。
 */
public final class QijieyaUrlProvider implements SongUrlProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(QijieyaUrlProvider.class);
    private static final String BASE_URL = "https://api.qijieya.cn/meting/";

    private final SongUrlHttp http;

    public QijieyaUrlProvider() {
        this(ApiSongUrlHttp.INSTANCE);
    }

    /** 供测试注入假的 HTTP 实现。 */
    public QijieyaUrlProvider(SongUrlHttp http) {
        this.http = http;
    }

    @Override
    public String name() {
        return "Qijieya";
    }

    @Override
    public CompletableFuture<List<String>> resolve(String songId) {
        return CompletableFuture.supplyAsync(() -> {
            String url = BASE_URL + "?type=url&id=" + songId;
            try {
                if (http.getContentType(url).startsWith("audio/")) {
                    return List.of(url);
                }
            } catch (Exception e) {
                LOGGER.trace("Qijieya 音源失败 id={}: {}", songId, UrlUtil.rootMessage(e));
            }
            return List.of();
        });
    }
}
