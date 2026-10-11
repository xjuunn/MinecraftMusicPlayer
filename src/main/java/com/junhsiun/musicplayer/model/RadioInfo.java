package com.junhsiun.musicplayer.model;

import java.util.Objects;

public final class RadioInfo {
    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final String secondCategory;
    private final String coverUrl;
    private final int programCount;
    private final int subCount;
    private final long playCount;
    private final int radioFeeType;

    public RadioInfo(String id, String name, String description, String category, String secondCategory, String coverUrl, int programCount, int subCount, long playCount, int radioFeeType) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.secondCategory = secondCategory;
        this.coverUrl = coverUrl;
        this.programCount = programCount;
        this.subCount = subCount;
        this.playCount = playCount;
        this.radioFeeType = radioFeeType;
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

    public String category() {
        return category;
    }

    public String secondCategory() {
        return secondCategory;
    }

    public String coverUrl() {
        return coverUrl;
    }

    public int programCount() {
        return programCount;
    }

    public int subCount() {
        return subCount;
    }

    public long playCount() {
        return playCount;
    }

    public int radioFeeType() {
        return radioFeeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RadioInfo)) {
            return false;
        }
        RadioInfo that = (RadioInfo) o;
        return programCount == that.programCount
                && subCount == that.subCount
                && playCount == that.playCount
                && radioFeeType == that.radioFeeType
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(category, that.category)
                && Objects.equals(secondCategory, that.secondCategory)
                && Objects.equals(coverUrl, that.coverUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, category, secondCategory, coverUrl, programCount, subCount, playCount, radioFeeType);
    }

    @Override
    public String toString() {
        return "RadioInfo{id='" + id + "', name='" + name + "', description='" + description + "', category='" + category
                + "', secondCategory='" + secondCategory + "', coverUrl='" + coverUrl + "', programCount=" + programCount
                + ", subCount=" + subCount + ", playCount=" + playCount + ", radioFeeType=" + radioFeeType + "}";
    }
}
