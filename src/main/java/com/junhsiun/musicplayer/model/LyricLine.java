package com.junhsiun.musicplayer.model;

import java.util.Objects;

public final class LyricLine {
    private final long timeMillis;
    private final String text;

    public LyricLine(long timeMillis, String text) {
        this.timeMillis = timeMillis;
        this.text = text;
    }

    public long timeMillis() {
        return timeMillis;
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LyricLine)) {
            return false;
        }
        LyricLine that = (LyricLine) o;
        return timeMillis == that.timeMillis && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeMillis, text);
    }

    @Override
    public String toString() {
        return "LyricLine{timeMillis=" + timeMillis + ", text='" + text + "'}";
    }
}
