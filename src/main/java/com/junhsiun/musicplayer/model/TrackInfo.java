package com.junhsiun.musicplayer.model;

import java.util.List;

public record TrackInfo(String id, String title, String artist, String artistId, String coverUrl, List<String> sourceUrls, long durationMillis) {
}
