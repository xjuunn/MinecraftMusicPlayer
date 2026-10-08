package com.junhsiun.musicplayer.util;

import com.junhsiun.musicplayer.model.QueuedTrack;

import java.util.List;
import java.util.UUID;

/**
 * 每玩家点歌上限策略。
 *
 * <p>统计某玩家在整条单点管道（正在播放的首曲 + 队列）中占用的歌曲数量，
 * 用于限制单个玩家可点播的最大曲目数。纯逻辑、无副作用，便于单元测试。
 */
public final class PlayerSongLimiter {
    private PlayerSongLimiter() {
    }

    /**
     * 计算某玩家当前已占用的歌曲数量。
     *
     * @param requester    当前正在播放歌曲的点歌人（可能为 null）
     * @param queuedTracks 队列中的所有单点歌曲
     * @param player       要统计的玩家
     * @return 该玩家占用的歌曲数量
     */
    public static int occupiedCount(UUID requester, List<QueuedTrack> queuedTracks, UUID player) {
        int count = 0;
        if (requester != null && requester.equals(player)) {
            count++;
        }
        if (queuedTracks != null && player != null) {
            for (QueuedTrack track : queuedTracks) {
                if (player.equals(track.requesterId())) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 判断玩家是否还能再点一首。
     *
     * @param requester    当前正在播放歌曲的点歌人（可能为 null）
     * @param queuedTracks 队列中的所有单点歌曲
     * @param player       要请求点歌的玩家
     * @param limit        每玩家点歌上限（&gt;0）
     * @return 当前占用数 &lt; 上限则允许
     */
    public static boolean canRequest(UUID requester, List<QueuedTrack> queuedTracks, UUID player, int limit) {
        return canRequest(requester, queuedTracks, player, limit, 0);
    }

    /**
     * 判断玩家是否还能再点一首（含尚未入队的在途请求）。
     *
     * <p>点歌校验通过后、异步解析完成入队前存在时间窗口；若仅按已入队歌曲计数，
     * 快速连点会全部通过校验从而突破上限。因此已放行但尚未入队的在途请求
     * 也必须计入占用。
     *
     * @param requester    当前正在播放歌曲的点歌人（可能为 null）
     * @param queuedTracks 队列中的所有单点歌曲
     * @param player       要请求点歌的玩家
     * @param limit        每玩家点歌上限（&gt;0）
     * @param pendingCount 该玩家已放行但尚未入队的在途请求数（负数按 0 处理）
     * @return 占用数 + 在途数 &lt; 上限则允许
     */
    public static boolean canRequest(UUID requester, List<QueuedTrack> queuedTracks, UUID player, int limit, int pendingCount) {
        if (limit <= 0) {
            return false;
        }
        return occupiedCount(requester, queuedTracks, player) + Math.max(0, pendingCount) < limit;
    }
}
