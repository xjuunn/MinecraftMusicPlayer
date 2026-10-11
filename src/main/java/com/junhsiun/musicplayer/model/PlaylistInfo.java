package com.junhsiun.musicplayer.model;

import com.junhsiun.musicplayer.util.Java8;

import java.util.List;
import java.util.Objects;

public final class PlaylistInfo {
    private final String id;
    private final String title;
    private final String ownerId;
    private final String ownerName;
    private final List<SearchEntry> tracks;
    private final int trackCount;

    public PlaylistInfo(String id, String title, String ownerId, String ownerName, List<SearchEntry> tracks, int trackCount) {
        this.id = id;
        this.title = title;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.tracks = Java8.copyOf(tracks);
        this.trackCount = trackCount;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public List<SearchEntry> tracks() {
        return tracks;
    }

    public int trackCount() {
        return trackCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlaylistInfo)) {
            return false;
        }
        PlaylistInfo that = (PlaylistInfo) o;
        return trackCount == that.trackCount
                && Objects.equals(id, that.id)
                && Objects.equals(title, that.title)
                && Objects.equals(ownerId, that.ownerId)
                && Objects.equals(ownerName, that.ownerName)
                && Objects.equals(tracks, that.tracks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, ownerId, ownerName, tracks, trackCount);
    }

    @Override
    public String toString() {
        return "PlaylistInfo{id='" + id + "', title='" + title + "', ownerId='" + ownerId + "', ownerName='" + ownerName
                + "', tracks=" + tracks + ", trackCount=" + trackCount + "}";
    }
}
