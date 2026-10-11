package com.junhsiun.musicplayer.model;

import com.junhsiun.musicplayer.util.Java8;

import java.util.List;
import java.util.Objects;

public final class TrackInfo {
    private final String id;
    private final String title;
    private final String artist;
    private final String artistId;
    private final String coverUrl;
    private final List<String> sourceUrls;
    private final long durationMillis;

    public TrackInfo(String id, String title, String artist, String artistId, String coverUrl, List<String> sourceUrls, long durationMillis) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.artistId = artistId;
        this.coverUrl = coverUrl;
        this.sourceUrls = Java8.copyOf(sourceUrls);
        this.durationMillis = durationMillis;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String artist() {
        return artist;
    }

    public String artistId() {
        return artistId;
    }

    public String coverUrl() {
        return coverUrl;
    }

    public List<String> sourceUrls() {
        return sourceUrls;
    }

    public long durationMillis() {
        return durationMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrackInfo)) {
            return false;
        }
        TrackInfo that = (TrackInfo) o;
        return durationMillis == that.durationMillis
                && Objects.equals(id, that.id)
                && Objects.equals(title, that.title)
                && Objects.equals(artist, that.artist)
                && Objects.equals(artistId, that.artistId)
                && Objects.equals(coverUrl, that.coverUrl)
                && Objects.equals(sourceUrls, that.sourceUrls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, artist, artistId, coverUrl, sourceUrls, durationMillis);
    }

    @Override
    public String toString() {
        return "TrackInfo{id='" + id + "', title='" + title + "', artist='" + artist + "', artistId='" + artistId
                + "', coverUrl='" + coverUrl + "', sourceUrls=" + sourceUrls + ", durationMillis=" + durationMillis + "}";
    }
}

