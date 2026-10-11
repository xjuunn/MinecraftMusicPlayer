package com.junhsiun.musicplayer.model;

import java.util.Objects;

public final class ProgramInfo {
    private final String id;
    private final String name;
    private final String mainTrackId;
    private final long durationMillis;
    private final String coverUrl;
    private final String description;
    private final String radioId;
    private final String radioName;

    public ProgramInfo(String id, String name, String mainTrackId, long durationMillis, String coverUrl, String description, String radioId, String radioName) {
        this.id = id;
        this.name = name;
        this.mainTrackId = mainTrackId;
        this.durationMillis = durationMillis;
        this.coverUrl = coverUrl;
        this.description = description;
        this.radioId = radioId;
        this.radioName = radioName;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String mainTrackId() {
        return mainTrackId;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public String coverUrl() {
        return coverUrl;
    }

    public String description() {
        return description;
    }

    public String radioId() {
        return radioId;
    }

    public String radioName() {
        return radioName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProgramInfo)) {
            return false;
        }
        ProgramInfo that = (ProgramInfo) o;
        return durationMillis == that.durationMillis
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(mainTrackId, that.mainTrackId)
                && Objects.equals(coverUrl, that.coverUrl)
                && Objects.equals(description, that.description)
                && Objects.equals(radioId, that.radioId)
                && Objects.equals(radioName, that.radioName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, mainTrackId, durationMillis, coverUrl, description, radioId, radioName);
    }

    @Override
    public String toString() {
        return "ProgramInfo{id='" + id + "', name='" + name + "', mainTrackId='" + mainTrackId + "', durationMillis=" + durationMillis
                + ", coverUrl='" + coverUrl + "', description='" + description + "', radioId='" + radioId + "', radioName='" + radioName + "'}";
    }
}
