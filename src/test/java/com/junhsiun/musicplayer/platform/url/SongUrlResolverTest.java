package com.junhsiun.musicplayer.platform.url;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongUrlResolverTest {

    /** 测试用假提供者：按次数返回预设结果，并记录被调用的次数与顺序。 */
    private static final class FakeProvider implements SongUrlProvider {
        private final String name;
        private final List<List<String>> resultsPerAttempt;
        private final long delayMillis;
        private final AtomicInteger calls = new AtomicInteger();
        private final List<String> callLog;

        FakeProvider(String name, List<String> result) {
            this(name, List.of(result), 0L, null);
        }

        FakeProvider(String name, List<List<String>> resultsPerAttempt, long delayMillis, List<String> callLog) {
            this.name = name;
            this.resultsPerAttempt = resultsPerAttempt;
            this.delayMillis = delayMillis;
            this.callLog = callLog;
        }

        /**
         * 每次调用依次返回一项结果，用完后重复最后一项。
         *
         * <p>用静态工厂而不是重载构造器：{@code List<String>} 与 {@code List<List<String>>}
         * 的擦除类型相同，两个构造器会名称冲突。</p>
         */
        @SafeVarargs
        static FakeProvider attempts(String name, List<String>... perAttempt) {
            return new FakeProvider(name, List.of(perAttempt), 0L, null);
        }

        /** 同上，并把每次调用的音源名记进 {@code callLog}。 */
        @SafeVarargs
        static FakeProvider attemptsLogged(String name, List<String> callLog, List<String>... perAttempt) {
            return new FakeProvider(name, List.of(perAttempt), 0L, callLog);
        }

        int calls() {
            return calls.get();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompletableFuture<List<String>> resolve(String songId) {
            return resolve(songId, SongUrlResolver.PROVIDER_TIMEOUT_SECONDS);
        }

        @Override
        public CompletableFuture<List<String>> resolve(String songId, int timeoutSeconds) {
            int index = calls.getAndIncrement();
            if (callLog != null) {
                callLog.add(name);
            }
            List<String> result = resultsPerAttempt.get(Math.min(index, resultsPerAttempt.size() - 1));
            if (delayMillis <= 0L) {
                return CompletableFuture.completedFuture(result);
            }
            // 真实提供者由 submitProbe 在超时后让 future 以空结果收尾；这里同样按超时
            // 判失败，否则慢速提供者"超时后照样返回结果"，测不出解析器的超时行为。
            CompletableFuture<List<String>> future = new CompletableFuture<>();
            CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS)
                    .execute(() -> future.complete(result));
            return future.completeOnTimeout(List.of(), timeoutSeconds, TimeUnit.SECONDS);
        }
    }

    /** 抛异常的提供者，用于测试异常处理与重试。 */
    private static final class ThrowingProvider implements SongUrlProvider {
        private final String name;
        private final AtomicInteger calls = new AtomicInteger();

        ThrowingProvider(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompletableFuture<List<String>> resolve(String songId) {
            calls.incrementAndGet();
            CompletableFuture<List<String>> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("模拟失败"));
            return f;
        }
    }

    private static List<String> await(CompletableFuture<List<String>> future)
            throws ExecutionException, InterruptedException, TimeoutException {
        return future.get(10, TimeUnit.SECONDS);
    }

    // ── 优先级与短路 ──────────────────────────────────────

    @Test
    void 首选音源命中时不再尝试后续音源() throws Exception {
        List<String> callLog = new java.util.ArrayList<>();
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(FakeProvider.attemptsLogged("Qijieya", callLog, List.of("https://q.mp3")));
        resolver.register(FakeProvider.attemptsLogged("Byfuns", callLog, List.of("https://b.mp3")));
        resolver.register(FakeProvider.attemptsLogged("Mycelis", callLog, List.of("https://m.mp3")));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals(List.of("https://q.mp3"), urls);
        assertEquals(List.of("Qijieya"), callLog);
    }

    @Test
    void 按注册顺序选第一个可用的音源() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("Qijieya", List.of()));
        resolver.register(new FakeProvider("Byfuns", List.of("https://b.mp3")));
        resolver.register(new FakeProvider("Direct", List.of("https://d.mp3")));

        assertEquals(List.of("https://b.mp3"), await(resolver.resolve("1")));
    }

    @Test
    void 后续音源也失败时继续往下一个() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("Qijieya", List.of()));
        resolver.register(new FakeProvider("Byfuns", List.of()));
        resolver.register(new FakeProvider("Direct", List.of("https://d.mp3")));

        assertEquals(List.of("https://d.mp3"), await(resolver.resolve("1")));
    }

    // ── 重试 ──────────────────────────────────────────────

    @Test
    void 音源首次失败会重试一次并采用重试结果() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        FakeProvider qijieya = FakeProvider.attempts("Qijieya", List.of(), List.of("https://retry.mp3"));

        resolver.register(qijieya);
        resolver.register(new FakeProvider("Byfuns", List.of("https://b.mp3")));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals(List.of("https://retry.mp3"), urls);
        assertEquals(2, qijieya.calls());
    }

    @Test
    void 重试成功后不再尝试后面的音源() throws Exception {
        List<String> callLog = new java.util.ArrayList<>();
        SongUrlResolver resolver = new SongUrlResolver();

        resolver.register(FakeProvider.attemptsLogged("Qijieya", callLog, List.of(), List.of("https://retry.mp3")));
        resolver.register(FakeProvider.attemptsLogged("Byfuns", callLog, List.of("https://b.mp3")));

        await(resolver.resolve("1"));
        assertEquals(List.of("Qijieya", "Qijieya"), callLog);
    }

    @Test
    void 两次都失败才换下一个音源() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        FakeProvider qijieya = FakeProvider.attempts("Qijieya", List.of());
        resolver.register(qijieya);
        resolver.register(new FakeProvider("Byfuns", List.of("https://b.mp3")));

        List<String> urls = await(resolver.resolve("1"));
        assertEquals(List.of("https://b.mp3"), urls);
        assertEquals(2, qijieya.calls());
    }

    @Test
    void 每个音源最多尝试两次() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        FakeProvider qijieya = FakeProvider.attempts("Qijieya", List.of());
        FakeProvider byfuns = FakeProvider.attempts("Byfuns", List.of());
        resolver.register(qijieya);
        resolver.register(byfuns);

        assertTrue(await(resolver.resolve("1")).isEmpty());
        assertEquals(2, qijieya.calls());
        assertEquals(2, byfuns.calls());
    }

    @Test
    void 抛异常的音源也会重试() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        ThrowingProvider bad = new ThrowingProvider("Bad");
        resolver.register(bad);
        resolver.register(new FakeProvider("Good", List.of("https://good.mp3")));

        assertEquals(List.of("https://good.mp3"), await(resolver.resolve("1")));
        assertEquals(2, bad.calls.get());
    }

    // ── 全部失败 ──────────────────────────────────────────

    @Test
    void 全部音源都失败时返回空列表() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("Qijieya", List.of()));
        resolver.register(new FakeProvider("Byfuns", List.of()));
        resolver.register(new FakeProvider("Direct", List.of()));
        resolver.register(new FakeProvider("Mycelis", List.of()));

        assertTrue(await(resolver.resolve("123")).isEmpty());
    }

    @Test
    void 无注册提供者时返回空列表() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        assertTrue(await(resolver.resolve("123")).isEmpty());
        assertEquals(0, resolver.size());
    }

    @Test
    void register_null不崩溃() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(null);
        resolver.register(new FakeProvider("OK", List.of("https://ok.mp3")));

        assertEquals(List.of("https://ok.mp3"), await(resolver.resolve("1")));
        assertEquals(1, resolver.size());
    }

    @Test
    void 提供者返回null列表时视为失败并重试() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SongUrlProvider nullProvider = new SongUrlProvider() {
            @Override public String name() { return "Null"; }
            @Override public CompletableFuture<List<String>> resolve(String id) {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
        };
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(nullProvider);
        resolver.register(new FakeProvider("OK", List.of("https://ok.mp3")));

        assertEquals(List.of("https://ok.mp3"), await(resolver.resolve("1")));
        assertEquals(2, calls.get());
    }

    @Test
    void 提供者返回含空白项的列表时过滤() throws Exception {
        List<String> mixed = new java.util.ArrayList<>();
        mixed.add("https://good.mp3");
        mixed.add("");
        mixed.add("  ");
        mixed.add(null);
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("Mixed", mixed));

        assertEquals(List.of("https://good.mp3"), await(resolver.resolve("1")));
    }

    // ── 超时 ──────────────────────────────────────────────

    @Test
    void 超时的音源会被跳过() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver(1);
        resolver.register(new FakeProvider("Slow", List.of(List.of("https://slow.mp3")), 5_000L, null));
        resolver.register(new FakeProvider("Fast", List.of("https://fast.mp3")));

        assertEquals(List.of("https://fast.mp3"), await(resolver.resolve("1")));
    }

    @Test
    void 所有音源都超时时返回空列表() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver(1);
        resolver.register(new FakeProvider("Slow1", List.of(List.of("https://1.mp3")), 5_000L, null));
        resolver.register(new FakeProvider("Slow2", List.of(List.of("https://2.mp3")), 5_000L, null));

        assertTrue(await(resolver.resolve("1")).isEmpty());
    }

    @Test
    void 自定义短超时不改变内置超时常量() {
        assertEquals(3, SongUrlResolver.PROVIDER_TIMEOUT_SECONDS);
        assertEquals(2, SongUrlResolver.PROVIDER_ATTEMPTS);
    }

    // ── URL trim ──────────────────────────────────────────

    @Test
    void URL含前后空格时自动trim() throws Exception {
        SongUrlResolver resolver = new SongUrlResolver();
        resolver.register(new FakeProvider("P", List.of("  https://trimmed.mp3  \n")));

        assertEquals(List.of("https://trimmed.mp3"), await(resolver.resolve("1")));
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