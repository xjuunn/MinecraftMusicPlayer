package com.junhsiun.musicplayer.platform.url;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Qijieya 第三方音源（首选）。
 *
 * <p>GET {@code https://api.qijieya.cn/meting/?type=url&id={id}} 会 <b>302 跳到网易云 CDN</b>，
 * 最终返回音频流（Content-Type 为 audio/mpeg）。客户端下载时会跟随跳转，
 * 因此这里直接把原始 URL 作为可播放源返回。</p>
 *
 * <p>该源是本项目实测唯一<b>稳定返回完整时长</b>的免费音源（VIP 曲也给整首），
 * 故排在音源链第一位。</p>
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
        return resolve(songId, SongUrlResolver.PROVIDER_TIMEOUT_SECONDS);
    }

    @Override
    public CompletableFuture<List<String>> resolve(String songId, int timeoutSeconds) {
        return com.junhsiun.musicplayer.platform.NeteaseApiClient.submitProbe(() -> probe(songId), timeoutSeconds);
    }

    /**
     * 探测一次：Content-Type 以 {@code audio/} 开头才认为可用。
     *
     * <p>接口偶发 5xx 或返回 HTML 错误页，这时 Content-Type 是 text/html，
     * 直接判失败（由解析器负责重试），避免把错误页当成音源塞给客户端。</p>
     */
    private List<String> probe(String songId) {
        String url = BASE_URL + "?type=url&id=" + songId;
        try {
            if (http.getContentType(url).startsWith("audio/")) {
                return List.of(url);
            }
        } catch (Exception e) {
            LOGGER.trace("Qijieya 音源失败 id={}: {}", songId, UrlUtil.rootMessage(e));
        }
        return List.of();
    }
}
