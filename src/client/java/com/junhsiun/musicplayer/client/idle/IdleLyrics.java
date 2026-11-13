package com.junhsiun.musicplayer.client.idle;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.client.media.MediaBrowserService;
import com.junhsiun.musicplayer.model.LyricLine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 纯享模式的歌词缓存。
 *
 * <p>只在客户端 tick 线程更新，渲染线程只读 {@link #lines()} 与 {@link #indexAt(long)}，
 * 不会在画帧时发起网络请求。切歌时旧请求的结果通过曲目 ID 比对丢弃。</p>
 */
public final class IdleLyrics {

    private static final IdleLyrics INSTANCE = new IdleLyrics();

    // 歌词请求在线程池里完成，渲染在客户端主线程读取：
    // 字段必须是 volatile，否则主线程可能一直读到旧的空列表。
    private volatile String trackId = "";
    private volatile List<LyricLine> lines = List.of();
    private volatile boolean loading;

    private IdleLyrics() {
    }

    public static IdleLyrics getInstance() {
        return INSTANCE;
    }

    /** 当前曲目的歌词；没有歌词时为空列表。 */
    public List<LyricLine> lines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** 曲目变化时重新拉取；同一首歌重复调用是空操作。 */
    public void update(String currentTrackId) {
        String next = currentTrackId == null ? "" : currentTrackId.trim();
        if (next.equals(trackId)) {
            return;
        }
        trackId = next;
        lines = List.of();
        loading = false;
        if (next.isEmpty()) {
            return;
        }
        loading = true;
        CompletableFuture<List<LyricLine>> future;
        try {
            future = MediaBrowserService.getInstance().lyrics(next);
        } catch (RuntimeException exception) {
            loading = false;
            MusicPlayerMod.LOGGER.debug("纯享歌词请求失败: {}", next, exception);
            return;
        }
        future.whenComplete((result, throwable) -> {
            if (!next.equals(trackId)) {
                return;
            }
            loading = false;
            if (throwable != null || result == null) {
                return;
            }
            lines = result;
        });
    }

    /** 命中的歌词行下标；没有命中返回 -1。 */
    public int indexAt(long elapsedMillis) {
        if (lines.isEmpty()) {
            return -1;
        }
        int low = 0;
        int high = lines.size() - 1;
        int found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMillis() <= elapsedMillis) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }

    public void clear() {
        trackId = "";
        lines = List.of();
        loading = false;
    }
}
