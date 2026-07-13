package com.junhsiun.musicplayer.platform;

import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public final class ArtistTopSongSource implements RandomSongSource {
    private static final int ARTIST_FETCH_LIMIT = 100;
    private static final int ARTIST_FETCH_OFFSET = 0;

    private final NeteaseApiClient client;

    public ArtistTopSongSource(NeteaseApiClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<List<TrackInfo>> fetchRandomTracks(int count) {
        int safeCount = Math.max(1, count);
        return client.topArtists(ARTIST_FETCH_LIMIT, ARTIST_FETCH_OFFSET)
                .thenCompose(artists -> {
                    if (artists.isEmpty()) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                    Random random = new Random();
                    List<SearchEntry> shuffled = new ArrayList<>(artists);
                    Collections.shuffle(shuffled, random);
                    int artistLimit = Math.min(shuffled.size(), Math.max(3, safeCount));
                    List<CompletableFuture<List<TrackInfo>>> artistFutures = new ArrayList<>();
                    for (int i = 0; i < artistLimit; i++) {
                        String artistId = shuffled.get(i).id();
                        artistFutures.add(client.artistTopSongs(artistId));
                    }
                    return CompletableFuture.allOf(artistFutures.toArray(CompletableFuture[]::new))
                            .thenApply(ignored -> {
                                List<TrackInfo> allTracks = new ArrayList<>();
                                for (CompletableFuture<List<TrackInfo>> future : artistFutures) {
                                    try {
                                        List<TrackInfo> tracks = future.join();
                                        if (tracks != null) {
                                            allTracks.addAll(tracks);
                                        }
                                    } catch (Exception ex) {
                                    }
                                }
                                Collections.shuffle(allTracks, random);
                                return allTracks.subList(0, Math.min(safeCount, allTracks.size()));
                            });
                });
    }
}
