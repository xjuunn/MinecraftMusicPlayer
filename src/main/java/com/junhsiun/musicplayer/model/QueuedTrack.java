package com.junhsiun.musicplayer.model;

import com.junhsiun.musicplayer.util.Java8;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 队列中的一首待播放 / 已排队单曲。
 *
 * <p>{@code requesterId} / {@code requesterName} 记录点歌人，用于点歌人直跳与每玩家点歌上限统计。
 * <p>{@code sourceUrls} 缓存点歌时解析到的可播放直链，在播放时优先使用。
 */
public final class QueuedTrack {
    private final String songId;
    private final String title;
    private final String artist;
    private final String artistCommand;
    private final UUID requesterId;
    private final String requesterName;
    private final List<String> sourceUrls;

    public QueuedTrack(String songId, String title, String artist, String artistCommand,
                       UUID requesterId, String requesterName, List<String> sourceUrls) {
        this.songId = songId;
        this.title = title;
        this.artist = artist;
        this.artistCommand = artistCommand;
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.sourceUrls = sourceUrls == null ? Collections.<String>emptyList() : Java8.copyOf(sourceUrls);
    }

    public QueuedTrack(String songId, String title, String artist, String artistCommand,
                       UUID requesterId, String requesterName) {
        this(songId, title, artist, artistCommand, requesterId, requesterName, Collections.<String>emptyList());
    }

    public String songId() {
        return songId;
    }

    public String title() {
        return title;
    }

    public String artist() {
        return artist;
    }

    public String artistCommand() {
        return artistCommand;
    }

    public UUID requesterId() {
        return requesterId;
    }

    public String requesterName() {
        return requesterName;
    }

    public List<String> sourceUrls() {
        return sourceUrls;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueuedTrack)) {
            return false;
        }
        QueuedTrack that = (QueuedTrack) o;
        return Objects.equals(songId, that.songId)
                && Objects.equals(title, that.title)
                && Objects.equals(artist, that.artist)
                && Objects.equals(artistCommand, that.artistCommand)
                && Objects.equals(requesterId, that.requesterId)
                && Objects.equals(requesterName, that.requesterName)
                && Objects.equals(sourceUrls, that.sourceUrls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(songId, title, artist, artistCommand, requesterId, requesterName, sourceUrls);
    }

    @Override
    public String toString() {
        return "QueuedTrack{songId='" + songId + "', title='" + title + "', artist='" + artist + "', artistCommand='" + artistCommand
                + "', requesterId=" + requesterId + ", requesterName='" + requesterName + "', sourceUrls=" + sourceUrls + "}";
    }
}
