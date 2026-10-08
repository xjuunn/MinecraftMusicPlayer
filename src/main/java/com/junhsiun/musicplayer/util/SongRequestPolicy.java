package com.junhsiun.musicplayer.util;

import com.junhsiun.musicplayer.model.QueuedTrack;

import java.util.List;
import java.util.UUID;

/**
 * 点歌请求的命令级决策逻辑（纯逻辑、无副作用、不依赖 MC 类型）。
 *
 * <p>将单曲点播请求的校验步骤（是否启用、队列是否已满、每玩家点歌上限、是否重复）
 * 收敛为单个判定方法，供 {@code MusicQueueService.requestSong} 与单元测试共用，
 * 从而以近似命令行为的方式验证点歌校验。
 */
public final class SongRequestPolicy {
    private SongRequestPolicy() {
    }

    /** 决策类别：REJECT=拒绝（发送红色失败提示），NOTICE=提示（发送黄色提示）。 */
    public enum Kind {
        REJECT, NOTICE
    }

    /** 点歌决策结果。{@code message} 为要展示给玩家的文本。 */
    public record Decision(Kind kind, String message) {
        /** 允许点歌的便捷构造。 */
        public static Decision allowed() {
            return new Decision(Kind.NOTICE, "");
        }
    }

    /**
     * 判定点歌请求的决策结果（无在途请求）。
     *
     * @param allowSongRequest    是否启用点歌
     * @param queueSize           当前单点队列长度
     * @param maxQueueSize        队列长度上限
     * @param playingRequesterId  正在播放歌曲的点歌人（可能为 null）
     * @param queued              队列中的单点歌曲
     * @param player              发起点歌的玩家
     * @param maxSongsPerPlayer   每玩家点歌上限
     * @param songId              请求的歌曲 ID
     * @param songActiveOrQueued  该歌曲是否正在播放/已在队列
     * @return 决策结果（message 为空表示允许点歌）
     */
    public static Decision decide(
            boolean allowSongRequest,
            int queueSize,
            int maxQueueSize,
            UUID playingRequesterId,
            List<QueuedTrack> queued,
            UUID player,
            int maxSongsPerPlayer,
            String songId,
            boolean songActiveOrQueued) {
        return decide(allowSongRequest, queueSize, maxQueueSize, playingRequesterId, queued,
                player, maxSongsPerPlayer, 0, songId, songActiveOrQueued);
    }

    /**
     * 判定点歌请求的决策结果。
     *
     * @param allowSongRequest    是否启用点歌
     * @param queueSize           当前单点队列长度
     * @param maxQueueSize        队列长度上限
     * @param playingRequesterId  正在播放歌曲的点歌人（可能为 null）
     * @param queued              队列中的单点歌曲
     * @param player              发起点歌的玩家
     * @param maxSongsPerPlayer   每玩家点歌上限
     * @param pendingCount        该玩家已放行但尚未入队的在途请求数（防止快速连点突破上限）
     * @param songId              请求的歌曲 ID
     * @param songActiveOrQueued  该歌曲是否正在播放/已在队列
     * @return 决策结果（message 为空表示允许点歌）
     */
    public static Decision decide(
            boolean allowSongRequest,
            int queueSize,
            int maxQueueSize,
            UUID playingRequesterId,
            List<QueuedTrack> queued,
            UUID player,
            int maxSongsPerPlayer,
            int pendingCount,
            String songId,
            boolean songActiveOrQueued) {

        if (!allowSongRequest) {
            return new Decision(Kind.REJECT, "管理员已禁用歌曲点播。");
        }
        if (queueSize >= maxQueueSize) {
            return new Decision(Kind.REJECT, "单点队列已满，请稍后再试。");
        }
        if (!PlayerSongLimiter.canRequest(playingRequesterId, queued, player, maxSongsPerPlayer, pendingCount)) {
            return new Decision(Kind.REJECT, "你一次最多只能点 " + maxSongsPerPlayer + " 首歌曲，请先播放或移除后再试。");
        }
        if (songActiveOrQueued) {
            return new Decision(Kind.NOTICE, "该歌曲正在播放或已在队列中。");
        }
        return Decision.allowed();
    }
}
