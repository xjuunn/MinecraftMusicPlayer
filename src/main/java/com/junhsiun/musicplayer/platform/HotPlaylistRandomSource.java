package com.junhsiun.musicplayer.platform;

import com.junhsiun.musicplayer.model.TrackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class HotPlaylistRandomSource implements RandomSongSource {
    private final NeteaseApiClient client;

    public HotPlaylistRandomSource(NeteaseApiClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<List<TrackInfo>> fetchRandomTracks(int count) {
        return client.randomHotTracks(count);
    }
}
