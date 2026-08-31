package com.junhsiun.musicplayer.platform.url;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongUrlResolverTest {

    /** 测试用假提供者：固定返回预设 URL，可选延迟。 */
    private static final class FakeProvider implements SongUrlProvider {
        private final String name;
        private final List<String> result;
        private final long delayMillis;

        FakeProvider(String name, List<String> result) {
            this(name, result, 0L);
        }

        FakeProvider(String name, List<String> result, long delayMillis) {
            this.name = name;
            this.result = result;
            this.delayMillis = delayMillis;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompletableFuture<List<String>> resolve(String songId) {
            if (delayMillis > 0) {
                return CompletableFuture.supplyAsync(() -> result,
                        CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS));
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    /** 抛异常的提供者，用于测试异常处理。 */
    private static final class ThrowingProvider implements SongUrlProvider {
        private final String name;

        ThrowingProvider(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompletableFuture<List<String>> resolve(String songId) {
            CompletableFuture<List<String>> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("模拟失败"));
            return f;
        }
    }

    private static List<String> await(CompletableFuture<List<String>> future) throws ExecutionException, InterruptedException, TimeoutException {
        return future.get(5, TimeUnit.SECONDS);
    }

    // ── 基本优先级 ────────────────────────────────────────

    @Test
    void 按注册顺序返回去重后的URL_Byfuns优先() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("Byfuns", List.of("https://byfuns.mp3")));
        resolver.register(new FakeProvider("Qijieya", List.of("https://qijieya.mp3")));
        resolver.register(new FakeProvider("Mycelis", List.of("https://mycelis.mp3")));

        List<String> urls = await(resolver.resolve("123"));
        assertEquals(List.of("https://byfuns.mp3", "https://qijieya.mp3", "https://mycelis.mp3"), urls);
    }

    @Test
    void 先注册的提供者URL排在前面() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("First", List.of("https://first.mp3")));
        resolver.register(new FakeProvider("Second", List.of("https://second.mp3")));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals("https://first.mp3", urls.get(0));
        assertEquals("https://second.mp3", urls.get(1));
    }

    @Test
    void 多个提供者每个返回多个URL全部保留且按序() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("A", List.of("https://a1.mp3", "https://a2.mp3")));
        resolver.register(new FakeProvider("B", List.of("https://b1.mp3")));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals(List.of("https://a1.mp3", "https://a2.mp3", "https://b1.mp3"), urls);
    }

    // ── 回退 ──────────────────────────────────────────────

    @Test
    void 前段源失败时回退到后段源() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("Byfuns", List.of()));
        resolver.register(new FakeProvider("Qijieya", List.of("https://qijieya.mp3")));
        resolver.register(new FakeProvider("Mycelis", List.of("https://mycelis.mp3")));

        List<String> urls = await(resolver.resolve("123"));
        assertEquals(List.of("https://qijieya.mp3", "https://mycelis.mp3"), urls);
    }

    @Test
    void 所有提供者都抛异常时返回空列表() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new ThrowingProvider("Bad1"));
        resolver.register(new ThrowingProvider("Bad2"));

        List<String> urls = await(resolver.resolve("1"));
        assertTrue(urls.isEmpty());
    }

    @Test
    void 混合成功与异常提供者() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new ThrowingProvider("Bad"));
        resolver.register(new FakeProvider("Good", List.of("https://good.mp3")));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals(List.of("https://good.mp3"), urls);
    }

    // ── 去重 ──────────────────────────────────────────────

    @Test
    void 重复URL会去重() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("Byfuns", List.of("https://same.mp3")));
        resolver.register(new FakeProvider("Qijieya", List.of("https://same.mp3")));

        List<String> urls = await(resolver.resolve("123"));
        assertEquals(List.of("https://same.mp3"), urls);
    }

    @Test
    void 部分URL重复时保留首次出现的顺序() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("A", List.of("https://a.mp3", "https://shared.mp3")));
        resolver.register(new FakeProvider("B", List.of("https://shared.mp3", "https://b.mp3")));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals(List.of("https://a.mp3", "https://shared.mp3", "https://b.mp3"), urls);
    }

    // ── 空与边界 ──────────────────────────────────────────

    @Test
    void 全部失败时返回空列表() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("Byfuns", List.of()));
        resolver.register(new FakeProvider("Qijieya", List.of()));

        List<String> urls = await(resolver.resolve("123"));
        assertTrue(urls.isEmpty());
    }

    @Test
    void 无注册提供者时返回空列表() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        assertTrue(await(resolver.resolve("123")).isEmpty());
    }

    @Test
    void register_null不崩溃() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(null);
        resolver.register(new FakeProvider("OK", List.of("https://ok.mp3")));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals(List.of("https://ok.mp3"), urls);
    }

    @Test
    void 提供者返回null列表时跳过() throws Exception {
        CompletableFuture<List<String>> nullFuture = CompletableFuture.completedFuture(null);
        SongUrlProvider nullProvider = new SongUrlProvider() {
            @Override public String name() { return "Null"; }
            @Override public CompletableFuture<List<String>> resolve(String id) { return nullFuture; }
        };
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(nullProvider);
        resolver.register(new FakeProvider("OK", List.of("https://ok.mp3")));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals(List.of("https://ok.mp3"), urls);
    }

    @Test
    void 提供者返回含空字符串的列表时过滤() throws Exception {
        List<String> mixed = new java.util.ArrayList<>();
        mixed.add("https://good.mp3");
        mixed.add("");
        mixed.add("  ");
        mixed.add(null);
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("Mixed", mixed));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals(List.of("https://good.mp3"), urls);
    }

    // ── 超时 ──────────────────────────────────────────────

    @Test
    void 超时的提供者会被跳过() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("Slow", List.of("https://slow.mp3"), 10_000L));
        resolver.register(new FakeProvider("Fast", List.of("https://fast.mp3")));

        List<String> urls = await(resolver.resolve("123"));
        assertEquals(List.of("https://fast.mp3"), urls);
    }

    @Test
    void 自定义短超时() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver(1);
        resolver.register(new FakeProvider("Slow", List.of("https://slow.mp3"), 5_000L));
        resolver.register(new FakeProvider("Fast", List.of("https://fast.mp3")));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals(List.of("https://fast.mp3"), urls);
    }

    @Test
    void 所有提供者都超时时返回空列表() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver(1);
        resolver.register(new FakeProvider("Slow1", List.of("https://1.mp3"), 5_000L));
        resolver.register(new FakeProvider("Slow2", List.of("https://2.mp3"), 5_000L));

        List<String> urls = await(resolver.resolve("1"));
        assertTrue(urls.isEmpty());
    }

    // ── URL trim ──────────────────────────────────────────

    @Test
    void URL含前后空格时自动trim() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("P", List.of("  https://trimmed.mp3  \n")));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals(List.of("https://trimmed.mp3"), urls);
    }

    // ── 歌曲 ID 透传 ──────────────────────────────────────

    @Test
    void 歌曲ID正确透传给提供者() throws Exception {
        AtomicReference<String> receivedId = new AtomicReference<>();
        SongUrlProvider spy = new SongUrlProvider() {
            @Override public String name() { return "Spy"; }
            @Override public CompletableFuture<List<String>> resolve(String id) {
                receivedId.set(id);
                return CompletableFuture.completedFuture(List.of("https://x.mp3"));
            }
        };
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(spy);

        await(resolver.resolve("99999"));
        assertEquals("99999", receivedId.get());
    }
}
