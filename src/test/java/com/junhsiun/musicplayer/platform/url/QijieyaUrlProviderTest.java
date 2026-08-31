package com.junhsiun.musicplayer.platform.url;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QijieyaUrlProviderTest {

    private static List<String> resolve(SongUrlProvider provider, String id) throws ExecutionException, InterruptedException, TimeoutException {
        return provider.resolve(id).get(5, TimeUnit.SECONDS);
    }

    // ── 基本功能 ──────────────────────────────────────────

    @Test
    void 音频内容类型时返回其URL() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubContentType("https://api.qijieya.cn/meting/?type=url&id=123", "audio/mpeg");
        QijieyaUrlProvider provider = new QijieyaUrlProvider(http);

        assertEquals("https://api.qijieya.cn/meting/?type=url&id=123", resolve(provider, "123").get(0));
    }

    @Test
    void 返回列表只包含一个URL() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubContentType("https://api.qijieya.cn/meting/?type=url&id=1", "audio/mpeg");
        QijieyaUrlProvider provider = new QijieyaUrlProvider(http);

        assertEquals(1, resolve(provider, "1").size());
    }

    @Test
    void 提供者名称为Qijieya() {
        assertEquals("Qijieya", new QijieyaUrlProvider(new FakeSongUrlHttp()).name());
    }

    // ── Content-Type 验证 ─────────────────────────────────

    @Test
    void audioMpeg内容类型返回URL() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubContentType("https://api.qijieya.cn/meting/?type=url&id=1", "audio/mpeg");
        QijieyaUrlProvider provider = new QijieyaUrlProvider(http);

        assertTrue(!resolve(provider, "1").isEmpty());
    }

    @Test
    void audioOgg内容类型也返回URL() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubContentType("https://api.qijieya.cn/meting/?type=url&id=1", "audio/ogg");
        QijieyaUrlProvider provider = new QijieyaUrlProvider(http);

        assertTrue(!resolve(provider, "1").isEmpty());
    }

    @Test
    void 非音频内容类型时不返回() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubContentType("https://api.qijieya.cn/meting/?type=url&id=123", "text/html");
        QijieyaUrlProvider provider = new QijieyaUrlProvider(http);

        assertTrue(resolve(provider, "123").isEmpty());
    }

    @Test
    void 空ContentType不返回() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        // 没有 stub → getContentType 返回 ""
        QijieyaUrlProvider provider = new QijieyaUrlProvider(http);

        assertTrue(resolve(provider, "123").isEmpty());
    }

    @Test
    void applicationOctetStream不视为音频() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubContentType("https://api.qijieya.cn/meting/?type=url&id=1", "application/octet-stream");
        QijieyaUrlProvider provider = new QijieyaUrlProvider(http);

        assertTrue(resolve(provider, "1").isEmpty());
    }

    // ── 异常处理 ──────────────────────────────────────────

    @Test
    void 请求异常时返回空列表() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubError("https://api.qijieya.cn/meting/?type=url&id=123", new RuntimeException("boom"));
        QijieyaUrlProvider provider = new QijieyaUrlProvider(http);

        assertTrue(resolve(provider, "123").isEmpty());
    }

    // ── URL 构造 ──────────────────────────────────────────

    @Test
    void 不同歌曲ID正确拼接URL() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubContentType("https://api.qijieya.cn/meting/?type=url&id=999", "audio/mpeg");
        QijieyaUrlProvider provider = new QijieyaUrlProvider(http);

        assertEquals("https://api.qijieya.cn/meting/?type=url&id=999", resolve(provider, "999").get(0));
    }

    @Test
    void 返回的URL就是请求的探测URL() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        String expectedUrl = "https://api.qijieya.cn/meting/?type=url&id=42";
        http.stubContentType(expectedUrl, "audio/mpeg");
        QijieyaUrlProvider provider = new QijieyaUrlProvider(http);

        assertEquals(expectedUrl, resolve(provider, "42").get(0));
    }

    @Test
    void 空字符串歌曲ID也能构造URL() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubContentType("https://api.qijieya.cn/meting/?type=url&id=", "audio/mpeg");
        QijieyaUrlProvider provider = new QijieyaUrlProvider(http);

        assertEquals("https://api.qijieya.cn/meting/?type=url&id=", resolve(provider, "").get(0));
    }
}
