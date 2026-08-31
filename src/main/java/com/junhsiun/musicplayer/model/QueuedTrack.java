package com.junhsiun.musicplayer.model;

import java.util.UUID;

/**
 * 队列中的一首待播 / 已排队单曲。
 *
 * <p>{@code requesterId} / {@code requesterName} 记录点歌人，用于点歌人直跳与每玩家点歌上限统计。
 */
public record QueuedTrack(
        String songId,
        String title,
        String artist,
        String artistCommand,
        UUID requesterId,
        String requesterName
) {
}
