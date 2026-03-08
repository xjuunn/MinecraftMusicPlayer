package com.junhsiun.musicplayer.model;

import java.util.List;

public record ArtistInfo(String id, String name, String description, List<SearchEntry> topSongs) {
}
