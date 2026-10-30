package com.junhsiun.musicplayer.platform.api;

import com.junhsiun.musicplayer.model.ArtistInfo;
import com.junhsiun.musicplayer.model.PlaylistInfo;
import com.junhsiun.musicplayer.model.ProgramInfo;
import com.junhsiun.musicplayer.model.RadioInfo;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.model.UserPlaylistView;
import com.junhsiun.musicplayer.platform.NeteaseApiClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 后端（自建/公共 NeteaseCloudMusicApi 服务）取数实现。
 *
 * <p>适配器模式：把既有的 {@link NeteaseApiClient} 包装成 {@link MusicApi}，
 * 使其可与 {@link DirectMusicApi} 互换。这里不做任何逻辑，只转发调用，
 * 保证原有后端行为不变。
 */
public final class ProxyMusicApi implements MusicApi {
    private final NeteaseApiClient client;

    public ProxyMusicApi(NeteaseApiClient client) {
        this.client = client;
    }

    public NeteaseApiClient client() {
        return client;
    }

    @Override
    public CompletableFuture<TrackInfo> resolveSong(String id) {
        return client.resolveSong(id);
    }

    @Override
    public CompletableFuture<TrackInfo> resolveRadioSong(String id) {
        return client.resolveRadioSong(id);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchSongs(String keyword) {
        return client.searchSongs(keyword);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchSongs(String keyword, int page) {
        return client.searchSongs(keyword, page);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchArtists(String keyword) {
        return client.searchArtists(keyword);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchArtists(String keyword, int page) {
        return client.searchArtists(keyword, page);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword) {
        return client.searchPlaylists(keyword);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword, int page) {
        return client.searchPlaylists(keyword, page);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchUsers(String keyword) {
        return client.searchUsers(keyword);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchUsers(String keyword, int page) {
        return client.searchUsers(keyword, page);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchRadios(String keyword) {
        return client.searchRadios(keyword);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> searchRadios(String keyword, int page) {
        return client.searchRadios(keyword, page);
    }

    @Override
    public CompletableFuture<PlaylistInfo> playlistDetail(String id) {
        return client.playlistDetail(id);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> playlistTracksPage(String playlistId, int offset, int limit) {
        return client.playlistTracksPage(playlistId, offset, limit);
    }

    @Override
    public CompletableFuture<UserPlaylistView> userPlaylists(String userId) {
        return client.userPlaylists(userId);
    }

    @Override
    public CompletableFuture<List<TrackInfo>> artistTopSongs(String artistId) {
        return client.artistTopSongs(artistId);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> artistTopSongEntries(String artistId) {
        return client.artistTopSongEntries(artistId);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> topArtists(int limit, int offset) {
        return client.topArtists(limit, offset);
    }

    @Override
    public CompletableFuture<ArtistInfo> artistDetail(String artistId) {
        return client.artistDetail(artistId);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> artistSongsPage(String artistId, int offset, int limit) {
        return client.artistSongsPage(artistId, offset, limit);
    }

    @Override
    public CompletableFuture<RadioInfo> radioDetail(String rid) {
        return client.radioDetail(rid);
    }

    @Override
    public CompletableFuture<List<ProgramInfo>> radioPrograms(String rid, int limit, int offset, boolean asc) {
        return client.radioPrograms(rid, limit, offset, asc);
    }

    @Override
    public CompletableFuture<ProgramInfo> programDetail(String programId) {
        return client.programDetail(programId);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> hotRadios(int limit, int offset) {
        return client.hotRadios(limit, offset);
    }

    @Override
    public CompletableFuture<List<SearchEntry>> radioCategories() {
        return client.radioCategories();
    }

    @Override
    public CompletableFuture<List<TrackInfo>> randomHotTracks(int count) {
        return client.randomHotTracks(count);
    }
}
