package com.junhsiun.musicplayer.platform.url;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MycelisUrlProviderTest {

    private static List<String> resolve(SongUrlProvider provider, String id) throws ExecutionException, InterruptedException, TimeoutException {
        return provider.resolve(id).get(5, TimeUnit.SECONDS);
    }

    // ── 基本功能 ──────────────────────────────────────────

    @Test
    void 从data首个元素url读取直链() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://example.com/song/url/v1", "{\"data\":[{\"url\":\"https://mycelis.mp3\"}]}");
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://example.com");

        assertEquals(List.of("https://mycelis.mp3"), resolve(provider, "1"));
    }

    @Test
    void 提供者名称为Mycelis() {
        assertEquals("Mycelis", new MycelisUrlProvider(new FakeSongUrlHttp(), () -> "https://example.com").name());
    }

    // ── Base URL 处理 ─────────────────────────────────────

    @Test
    void supplier返回已去尾斜杠的URL时正常拼接() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://example.com/song/url/v1", "{\"data\":[{\"url\":\"https://m.mp3\"}]}");
        // 模拟默认构造器的逻辑：supplier 负责去掉尾斜杠
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://example.com");

        assertEquals(List.of("https://m.mp3"), resolve(provider, "1"));
    }

    @Test
    void 不同baseURL正确拼接() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://other.host/song/url/v1", "{\"data\":[{\"url\":\"https://o.mp3\"}]}");
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://other.host");

        assertEquals(List.of("https://o.mp3"), resolve(provider, "1"));
    }

    // ── data 结构异常 ─────────────────────────────────────

    @Test
    void url为null时视为不可用() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://example.com/song/url/v1", "{\"data\":[{\"url\":null}]}");
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://example.com");

        assertTrue(resolve(provider, "1").isEmpty());
    }

    @Test
    void data数组为空时返回空() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://example.com/song/url/v1", "{\"data\":[]}");
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://example.com");

        assertTrue(resolve(provider, "1").isEmpty());
    }

    @Test
    void 缺少data字段时返回空() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://example.com/song/url/v1", "{}");
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://example.com");

        assertTrue(resolve(provider, "1").isEmpty());
    }

    @Test
    void url为非HTTP协议时忽略() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://example.com/song/url/v1", "{\"data\":[{\"url\":\"ftp://file.mp3\"}]}");
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://example.com");

        assertTrue(resolve(provider, "1").isEmpty());
    }

    // ── 异常处理 ──────────────────────────────────────────

    @Test
    void 请求异常时返回空列表() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubError("https://example.com/song/url/v1", new RuntimeException("boom"));
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://example.com");

        assertTrue(resolve(provider, "1").isEmpty());
    }

    // ── 边界条件 ──────────────────────────────────────────

    @Test
    void URL含前后空格时trim() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://example.com/song/url/v1", "{\"data\":[{\"url\":\"  https://trimmed.mp3  \"}]}");
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://example.com");

        assertEquals(List.of("https://trimmed.mp3"), resolve(provider, "1"));
    }

    @Test
    void JSON根节点非对象时安全处理() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://example.com/song/url/v1", "\"string\"");
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://example.com");

        assertTrue(resolve(provider, "1").isEmpty());
    }

    @Test
    void data元素非对象时安全处理() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://example.com/song/url/v1", "{\"data\":[\"string\"]}");
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://example.com");

        assertTrue(resolve(provider, "1").isEmpty());
    }

    // ── 音质档位顺序 ──────────────────────────────────────

    @Test
    void 按音质档位降序探测() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://example.com/song/url/v1", "{\"data\":[{\"url\":\"https://m.mp3\"}]}");
        MycelisUrlProvider provider = new MycelisUrlProvider(http, () -> "https://example.com");
        resolve(provider, "9");

        List<List<String>> calls = http.callParams("https://example.com/song/url/v1");
        assertFalse(calls.isEmpty());
        // 第一个请求应为最高档 lossless
        assertEquals("lossless", calls.get(0).get(calls.get(0).size() - 1));
    }
}
