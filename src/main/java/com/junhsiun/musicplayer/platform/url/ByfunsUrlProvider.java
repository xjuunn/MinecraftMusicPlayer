package com.junhsiun.musicplayer.platform.url;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.junhsiun.musicplayer.platform.url.UrlUtil.isHttpUrl;

/**
 * Byfuns 第三方音源。
 *
 * <p>GET {@code https://api.byfuns.top/1/?id={id}&level={level}} 直接返回纯文本直链，
 * 支持 VIP 歌曲，优先使用靠前的高音质档位。
 */
public final class ByfunsUrlProvider implements SongUrlProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ByfunsUrlProvider.class);
    private static final String BASE_URL = "https://api.byfuns.top/1/";
    // 不使用 lossless：它返回 FLAC 无损直链，而客户端 JLayer 只能解码 MP3。
    // 使用 exhigh/higher/standard（MP3 档位），与旧实现保持一致以确保可播放。
    private static final String[] LEVELS = {"standard", "higher", "exhigh"};

    private final SongUrlHttp http;

    public ByfunsUrlProvider() {
        this(ApiSongUrlHttp.INSTANCE);
    }

    /** 供测试注入假的 HTTP 实现。 */
    public ByfunsUrlProvider(SongUrlHttp http) {
        this.http = http;
    }

    @Override
    public String name() {
        return "Byfuns";
    }

    @Override
    public CompletableFuture<List<String>> resolve(String songId) {
        return CompletableFuture.supplyAsync(() -> fetch(songId));
    }

    private List<String> fetch(String songId) {
        for (String level : LEVELS) {
            try {
                String url = http.getText(BASE_URL, "id", songId, "level", level);
                if (url != null) {
                    url = url.trim();
                }
                if (isHttpUrl(url)) {
                    return List.of(url);
                }
            } catch (Exception e) {
                LOGGER.trace("Byfuns 音源失败 id={} level={}: {}", songId, level, UrlUtil.rootMessage(e));
            }
        }
        return List.of();
    }
}
