package com.junhsiun.musicplayer.model;

import java.util.List;

public record PlaylistInfo(String id, String title, String ownerId, String ownerName, List<SearchEntry> tracks) {
}
