package com.junhsiun.musicplayer.platform.url;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 音源探测超时计时器。
 *
 * <p>Java 8 没有 {@code CompletableFuture.orTimeout}，这里用单个守护线程的调度器
 * 提供超时回调。只做定时回调，不执行业务逻辑。
 */
final class ProbeTimeouts {
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "song-url-timeout");
                thread.setDaemon(true);
                return thread;
            });

    private ProbeTimeouts() {
    }

    static ScheduledExecutorService scheduler() {
        return SCHEDULER;
    }
}
