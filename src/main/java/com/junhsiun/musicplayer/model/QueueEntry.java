package com.junhsiun.musicplayer.model;

/**
 * 当前队列里的一首待播放歌曲（用于界面展示）。
 *
 * <p>和点歌队列内部的 {@link QueuedTrack} 相比，这里只保留界面需要的字段：
 * 不需要直链、也不需要点歌人 UUID。队列快照走网络传给客户端时只发这一份轻量数据。</p>
 */
public record QueueEntry(String id, String title, String artist, String requester, String coverUrl) {

    public QueueEntry {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        requester = requester == null ? "" : requester;
        coverUrl = coverUrl == null ? "" : coverUrl;
    }
}