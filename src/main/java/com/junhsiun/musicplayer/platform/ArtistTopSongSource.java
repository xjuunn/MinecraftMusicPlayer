package com.junhsiun.musicplayer.platform;

import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.platform.api.MusicApi;
import com.junhsiun.musicplayer.util.Java8;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public final class ArtistTopSongSource implements RandomSongSource {
    private static final int ARTIST_FETCH_LIMIT = 100;
    private static final int ARTIST_FETCH_OFFSET = 0;
    /** 参与选曲的作者数量上限，避免一次预取查询过多作者。 */
    private static final int MAX_ARTIST_CANDIDATES = 5;

    private final MusicApi client;

    public ArtistTopSongSource(MusicApi client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<List<TrackInfo>> fetchRandomTracks(int count) {
        int safeCount = Math.max(1, count);
        return client.topArtists(ARTIST_FETCH_LIMIT, ARTIST_FETCH_OFFSET)
                .thenCompose(artists -> {
                    if (artists.isEmpty()) {
                        return CompletableFuture.completedFuture(Java8.listOf());
                    }
                    Random random = new Random();
                    List<SearchEntry> shuffled = new ArrayList<>(artists);
                    Collections.shuffle(shuffled, random);
                    int artistLimit = Math.min(shuffled.size(), Math.max(3, Math.min(safeCount, MAX_ARTIST_CANDIDATES)));

                    // 只拉取作者热门歌曲的元数据，先随机选曲，最后才解析选中歌曲的音源。
                    // 之前直接解析整页（最多 50 首 × 4 个音源）会把共享线程池塞满，
                    // 导致用户歌单/点歌等交互请求长时间没有回显。
                    List<CompletableFuture<List<SearchEntry>>> entryFutures = new ArrayList<>();
                    for (int i = 0; i < artistLimit; i++) {
                        String artistId = shuffled.get(i).id();
                        entryFutures.add(client.artistTopSongEntries(artistId)
                                .exceptionally(ex -> Java8.listOf()));
                    }
                    return CompletableFuture.allOf(entryFutures.toArray(new CompletableFuture[0]))
                            .thenCompose(ignored -> {
                                List<SearchEntry> candidates = new ArrayList<>();
                                for (CompletableFuture<List<SearchEntry>> future : entryFutures) {
                                    try {
                                        List<SearchEntry> entries = future.join();
                                        if (entries != null) {
                                            candidates.addAll(entries);
                                        }
                                    } catch (Exception ex) {
                                        // 单个作者失败不影响其他作者
                                    }
                                }
                                if (candidates.isEmpty()) {
                                    return CompletableFuture.completedFuture(Java8.listOf());
                                }
                                Collections.shuffle(candidates, random);
                                int pick = Math.min(safeCount, candidates.size());
                                List<CompletableFuture<TrackInfo>> resolveFutures = new ArrayList<>();
                                for (int i = 0; i < pick; i++) {
                                    resolveFutures.add(client.resolveSong(candidates.get(i).id())
                                            .exceptionally(ex -> null));
                                }
                                return CompletableFuture.allOf(resolveFutures.toArray(new CompletableFuture[0]))
                                        .thenApply(nil -> {
                                            List<TrackInfo> tracks = new ArrayList<>();
                                            for (CompletableFuture<TrackInfo> future : resolveFutures) {
                                                try {
                                                    TrackInfo track = future.join();
                                                    if (track != null && track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
                                                        tracks.add(track);
                                                    }
                                                } catch (Exception ex) {
                                                    // 忽略解析失败的曲目
                                                }
                                            }
                                            return tracks;
                                        });
                            });
                });
    }
}
