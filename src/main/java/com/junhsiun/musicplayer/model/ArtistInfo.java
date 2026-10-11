package com.junhsiun.musicplayer.model;

import java.util.Objects;

public final class ArtistInfo {
    private final String id;
    private final String name;
    private final String description;
    private final int songCount;

    public ArtistInfo(String id, String name, String description, int songCount) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.songCount = songCount;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public int songCount() {
        return songCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArtistInfo)) {
            return false;
        }
        ArtistInfo that = (ArtistInfo) o;
        return songCount == that.songCount
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, songCount);
    }

    @Override
    public String toString() {
        return "ArtistInfo{id='" + id + "', name='" + name + "', description='" + description + "', songCount=" + songCount + "}";
    }
}
