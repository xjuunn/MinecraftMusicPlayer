package com.junhsiun.musicplayer.model;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 队列中的一首待播放 / 已排队单曲。
 *
 * <p>{@code requesterId} / {@code requesterName} 记录点歌人，用于点歌人直跳与每玩家点歌上限统计。
 * <p>{@code sourceUrls} 缓存点歌时解析到的可播放直链，在播放时优先使用。
 * <p>{@code coverUrl} 只用于界面展示队列列表的封面，不参与播放。
 */
public record QueuedTrack(
        String songId,
        String title,
        String artist,
        String artistCommand,
        UUID requesterId,
        String requesterName,
        List<String> sourceUrls,
        String coverUrl
) {
    public QueuedTrack {
        if (sourceUrls == null) {
            sourceUrls = Collections.emptyList();
        } else {
            sourceUrls = List.copyOf(sourceUrls);
        }
        coverUrl = coverUrl == null ? "" : coverUrl;
    }

    public QueuedTrack(String songId, String title, String artist, String artistCommand,
                       UUID requesterId, String requesterName, List<String> sourceUrls) {
        this(songId, title, artist, artistCommand, requesterId, requesterName, sourceUrls, "");
    }

    public QueuedTrack(String songId, String title, String artist, String artistCommand,
                       UUID requesterId, String requesterName) {
        this(songId, title, artist, artistCommand, requesterId, requesterName, Collections.emptyList(), "");
    }

    public List<String> sourceUrls() {
        return sourceUrls;
    }
}