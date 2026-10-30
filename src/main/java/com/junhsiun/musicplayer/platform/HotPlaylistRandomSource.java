package com.junhsiun.musicplayer.platform;

import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.platform.api.MusicApi;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class HotPlaylistRandomSource implements RandomSongSource {
    private final MusicApi client;

    public HotPlaylistRandomSource(MusicApi client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<List<TrackInfo>> fetchRandomTracks(int count) {
        return client.randomHotTracks(count);
    }
}
