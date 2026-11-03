package com.junhsiun.musicplayer.platform.url;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 单曲音源解析器（按优先级逐个尝试）。
 *
 * <p>顺序尝试每个 {@link SongUrlProvider}：某个音源失败会先<b>重试一次</b>，两次都失败才换下一个；
 * 一旦拿到可用直链就立刻返回，不再打扰后面的音源。默认顺序：
 * Qijieya、Byfuns、网易云官方直连、Mycelis（保底）。</p>
 *
 * <p>为什么串行而不是并行：第三方音源都是免费公共接口，一首歌同时打四家既容易触发限流，
 * 又要等最慢的那一家；串行在正常情况下只发一次请求就结束。</p>
 *
 * <p>整条链有总预算 {@link #CHAIN_BUDGET_MILLIS}：所有音源都不可达时超时即放弃并返回空列表，
 * 由调用方提示"无法获取可播放的音乐链接"，而不是让玩家一直等。</p>
 */
public final class SongUrlResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(SongUrlResolver.class);
    /** 单个音源单次尝试的超时（秒）。 */
    static final int PROVIDER_TIMEOUT_SECONDS = 3;
    /** 每个音源的尝试次数：首次失败后再试一次。 */
    static final int PROVIDER_ATTEMPTS = 2;
    /** 整条音源链的总预算（毫秒）；超出后不再尝试剩余音源。 */
    static final long CHAIN_BUDGET_MILLIS = 12_000L;

    private final List<SongUrlProvider> providers = new ArrayList<>();
    private final int timeoutSeconds;

    public SongUrlResolver() {
        this(PROVIDER_TIMEOUT_SECONDS);
    }

    /** 供测试指定自定义超时。 */
    public SongUrlResolver(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 注册一个提供者，追加到优先级队列末尾。 */
    public void register(SongUrlProvider provider) {
        if (provider != null) {
            providers.add(provider);
        }
    }

    /** 已注册的音源数量（供测试与调试）。 */
    public int size() {
        return providers.size();
    }

    /**
     * 解析单曲可播放 URL：按优先级逐个尝试，每个音源最多 {@value #PROVIDER_ATTEMPTS} 次，
     * 命中即返回；全部失败返回空列表。
     */
    public CompletableFuture<List<String>> resolve(String songId) {
        if (providers.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CHAIN_BUDGET_MILLIS);
        return tryProvider(songId, 0, deadline);
    }

    private CompletableFuture<List<String>> tryProvider(String songId, int index, long deadline) {
        if (index >= providers.size()) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (System.nanoTime() >= deadline) {
            LOGGER.debug("音源链超出预算，跳过剩余 {} 个音源 id={}", providers.size() - index, songId);
            return CompletableFuture.completedFuture(List.of());
        }
        SongUrlProvider provider = providers.get(index);
        return attempt(provider, songId, 1).thenCompose(first -> {
            if (!first.isEmpty()) {
                return CompletableFuture.completedFuture(first);
            }
            if (System.nanoTime() >= deadline) {
                return tryProvider(songId, index + 1, deadline);
            }
            LOGGER.debug("音源 {} 首次失败，重试一次 id={}", provider.name(), songId);
            return attempt(provider, songId, 2).thenCompose(retried ->
                    retried.isEmpty()
                            ? tryProvider(songId, index + 1, deadline)
                            : CompletableFuture.completedFuture(retried));
        });
    }

    /** 单次尝试；异常与空结果都归一成空列表，交给调用方决定重试或换源。 */
    private CompletableFuture<List<String>> attempt(SongUrlProvider provider, String songId, int attempt) {
        return provider.resolve(songId, timeoutSeconds)
                .exceptionally(throwable -> {
                    LOGGER.debug("音源 {} 第 {} 次尝试失败 id={}: {}",
                            provider.name(), attempt, songId, UrlUtil.rootMessage(throwable));
                    return List.of();
                })
                .thenApply(SongUrlResolver::clean);
    }

    /** 去掉空白项并 trim；解析结果直接给客户端播放，不能带脏数据。 */
    private static List<String> clean(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>(urls.size());
        for (String url : urls) {
            if (url != null && !url.isBlank()) {
                cleaned.add(url.trim());
            }
        }
        return cleaned.isEmpty() ? List.of() : List.copyOf(cleaned);
    }
}
