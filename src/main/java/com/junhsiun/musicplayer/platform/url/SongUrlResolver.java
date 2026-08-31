package com.junhsiun.musicplayer.platform.url;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 单曲音源解析器（按优先级汇总各第三方提供者）。
 *
 * <p>并行探测所有已注册的 {@link SongUrlProvider}，随后按注册顺序（优先级）
 * 收集去重后的可用直链。默认顺序：Byfuns、Qijieya、VKEYS、Mycelis（保底）。
 * 后续接入新第三方音源时，只需新增提供者并调用 {@link #register(SongUrlProvider)}。
 */
public final class SongUrlResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(SongUrlResolver.class);
    private static final int PROVIDER_TIMEOUT_SECONDS = 3;

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

    /**
     * 解析单曲可播放 URL，按优先级返回去重后的列表（可能为空）。
     */
    public CompletableFuture<List<String>> resolve(String songId) {
        if (providers.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<CompletableFuture<List<String>>> futures = new ArrayList<>(providers.size());
        for (SongUrlProvider provider : providers) {
            futures.add(withTimeout(provider.resolve(songId)));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .exceptionally(ex -> null)
                .thenApply(nil -> collect(futures));
    }

    private CompletableFuture<List<String>> withTimeout(CompletableFuture<List<String>> future) {
        return future.orTimeout(timeoutSeconds, TimeUnit.SECONDS).exceptionally(ex -> List.of());
    }

    private List<String> collect(List<CompletableFuture<List<String>>> futures) {
        Set<String> ordered = new LinkedHashSet<>();
        for (int index = 0; index < futures.size(); index++) {
            try {
                List<String> urls = futures.get(index).get();
                if (urls != null) {
                    for (String url : urls) {
                        if (url != null && !url.isBlank()) {
                            ordered.add(url.trim());
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.trace("音源收集忽略失败: {}", UrlUtil.rootMessage(e));
            }
        }
        return List.copyOf(ordered);
    }
}
