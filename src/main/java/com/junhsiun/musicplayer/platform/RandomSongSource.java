package com.junhsiun.musicplayer.platform;

import com.junhsiun.musicplayer.model.TrackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface RandomSongSource {
    CompletableFuture<List<TrackInfo>> fetchRandomTracks(int count);
}
