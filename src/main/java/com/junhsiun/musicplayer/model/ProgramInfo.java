package com.junhsiun.musicplayer.model;

public record ProgramInfo(String id, String name, String mainTrackId, long durationMillis, String coverUrl, String description, String radioId, String radioName) {
}
