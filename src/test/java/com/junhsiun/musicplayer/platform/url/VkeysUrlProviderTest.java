package com.junhsiun.musicplayer.platform.url;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VkeysUrlProviderTest {

    private static List<String> resolve(SongUrlProvider provider, String id) throws ExecutionException, InterruptedException, TimeoutException {
        return provider.resolve(id).get(5, TimeUnit.SECONDS);
    }

    // ── 基本功能 ──────────────────────────────────────────

    @Test
    void 从dataUrl字段读取直链() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"code\":200,\"data\":{\"url\":\"https://vkeys.mp3\"}}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertEquals(List.of("https://vkeys.mp3"), resolve(provider, "1"));
    }

    @Test
    void 提供者名称为VKEYS() {
        assertEquals("VKEYS", new VkeysUrlProvider(new FakeSongUrlHttp()).name());
    }

    // ── 质量档回退 ────────────────────────────────────────

    @Test
    void 高质量返回有效URL时直接使用() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"data\":{\"url\":\"https://quality4.mp3\"}}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertEquals(List.of("https://quality4.mp3"), resolve(provider, "1"));
    }

    // ── data 结构异常 ─────────────────────────────────────

    @Test
    void data为空数组时忽略() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"code\":200,\"data\":[]}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertTrue(resolve(provider, "1").isEmpty());
    }

    @Test
    void data为null时忽略() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"code\":200,\"data\":null}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertTrue(resolve(provider, "1").isEmpty());
    }

    @Test
    void 缺少data字段时返回空() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"code\":200}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertTrue(resolve(provider, "1").isEmpty());
    }

    @Test
    void url字段缺失时忽略() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"data\":{\"other\":\"value\"}}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertTrue(resolve(provider, "1").isEmpty());
    }

    @Test
    void url为null时忽略() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"data\":{\"url\":null}}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertTrue(resolve(provider, "1").isEmpty());
    }

    @Test
    void url为非HTTP协议时忽略() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"data\":{\"url\":\"ftp://file.mp3\"}}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertTrue(resolve(provider, "1").isEmpty());
    }

    @Test
    void url为空字符串时忽略() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"data\":{\"url\":\"\"}}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertTrue(resolve(provider, "1").isEmpty());
    }

    // ── 异常处理 ──────────────────────────────────────────

    @Test
    void 请求异常时返回空列表() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubError("https://api.vkeys.cn/v2/music/netease", new RuntimeException("boom"));
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertTrue(resolve(provider, "1").isEmpty());
    }

    // ── 边界条件 ──────────────────────────────────────────

    @Test
    void JSON根节点非对象时安全处理() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "\"just a string\"");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertTrue(resolve(provider, "1").isEmpty());
    }

    @Test
    void URL含前后空格时trim() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"data\":{\"url\":\"  https://trimmed.mp3  \"}}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);

        assertEquals(List.of("https://trimmed.mp3"), resolve(provider, "1"));
    }

    // ── 质量档位顺序 ──────────────────────────────────────

    @Test
    void 按质量档位降序探测() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"data\":{\"url\":\"https://q.mp3\"}}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);
        resolve(provider, "5");

        List<List<String>> calls = http.callParams("https://api.vkeys.cn/v2/music/netease");
        assertFalse(calls.isEmpty());
        // 第一个请求应为 quality=4（最高质量）
        List<String> first = calls.get(0);
        assertEquals("4", first.get(first.size() - 1));
    }

    @Test
    void 所有质量档失败后不崩溃() throws Exception {
        FakeSongUrlHttp http = new FakeSongUrlHttp();
        http.stubJson("https://api.vkeys.cn/v2/music/netease", "{\"data\":{\"url\":\"ftp://bad.mp3\"}}");
        VkeysUrlProvider provider = new VkeysUrlProvider(http);
        resolve(provider, "5");

        List<List<String>> calls = http.callParams("https://api.vkeys.cn/v2/music/netease");
        // 全部都为无效 URL → 尝试 3 个质量档
        assertEquals(3, calls.size());
    }
}
