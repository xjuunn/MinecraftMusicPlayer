package com.junhsiun.musicplayer.util;

import com.junhsiun.musicplayer.model.QueuedTrack;

import java.util.List;
import java.util.Objects;
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

    /** 点歌决策结果。{@code key} 为翻译键（为空表示允许点歌），{@code args} 为翻译参数。 */
    public static final class Decision {
        private final Kind kind;
        private final String key;
        private final List<Object> args;

        public Decision(Kind kind, String key, List<Object> args) {
            this.kind = kind;
            this.key = key;
            this.args = args;
        }

        public Kind kind() {
            return kind;
        }

        public String key() {
            return key;
        }

        public List<Object> args() {
            return args;
        }

        /** 允许点歌的便捷构造。 */
        public static Decision allowed() {
            return new Decision(Kind.NOTICE, "", Java8.listOf());
        }

        /** 允许点歌（key 为空）。 */
        public boolean isAllowed() {
            return key == null || key.isEmpty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Decision)) {
                return false;
            }
            Decision that = (Decision) o;
            return kind == that.kind
                    && Objects.equals(key, that.key)
                    && Objects.equals(args, that.args);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, key, args);
        }

        @Override
        public String toString() {
            return "Decision{kind=" + kind + ", key='" + key + "', args=" + args + "}";
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
            return new Decision(Kind.REJECT, "musicplayer.request.disabled", Java8.listOf());
        }
        if (queueSize >= maxQueueSize) {
            return new Decision(Kind.REJECT, "musicplayer.request.queue_full", Java8.listOf());
        }
        if (!PlayerSongLimiter.canRequest(playingRequesterId, queued, player, maxSongsPerPlayer, pendingCount)) {
            return new Decision(Kind.REJECT, "musicplayer.request.player_limit", Java8.listOf(maxSongsPerPlayer));
        }
        if (songActiveOrQueued) {
            return new Decision(Kind.NOTICE, "musicplayer.request.duplicate", Java8.listOf());
        }
        return Decision.allowed();
    }
}
