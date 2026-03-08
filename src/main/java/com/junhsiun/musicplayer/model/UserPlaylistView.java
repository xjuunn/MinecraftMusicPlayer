package com.junhsiun.musicplayer.model;

import java.util.List;

public record UserPlaylistView(String id, String name, String signature, List<SearchEntry> playlists) {
}
