package com.junhsiun.musicplayer.model;

public record TrackInfo(String id, String title, String artist, String sourceUrl, long durationMillis) {
}
