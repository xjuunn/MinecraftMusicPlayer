package com.junhsiun.musicplayer.model;

import java.util.List;

public record TrackInfo(String id, String title, String artist, String artistId, List<String> sourceUrls, long durationMillis) {
}
