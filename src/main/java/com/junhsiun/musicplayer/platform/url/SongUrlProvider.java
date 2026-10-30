package com.junhsiun.musicplayer.platform.url;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 第三方音源 URL 提供者。
 *
 * <p>每个提供者负责从某个第三方音乐源解析单曲的直链。返回的列表通常为 0 或 1 项，
 * 空列表表示该音源当前不可用。多个提供者由 {@link SongUrlResolver} 按优先级依次收集。
 */
public interface SongUrlProvider {
    /** 提供者名称，用于日志与调试。 */
    String name();

    /**
     * 解析单曲可播放 URL。
     *
     * @param songId 网易云音乐单曲 ID
     * @return 可播放 URL 列表（可能为空），按可用性降序排列
     */
    CompletableFuture<List<String>> resolve(String songId);

    /**
     * 解析单曲可播放 URL，并限制执行耗时。
     *
     * <p>默认实现从调用时开始计时。自行调度线程的提供者应覆盖此方法，
     * 在实际开始执行后再计时，避免排队等待被误判为超时。
     *
     * @param songId         网易云音乐单曲 ID
     * @param timeoutSeconds 执行超时（秒）
     * @return 可播放 URL 列表（可能为空）
     */
    default CompletableFuture<List<String>> resolve(String songId, int timeoutSeconds) {
        return resolve(songId).orTimeout(timeoutSeconds, TimeUnit.SECONDS);
    }
}
