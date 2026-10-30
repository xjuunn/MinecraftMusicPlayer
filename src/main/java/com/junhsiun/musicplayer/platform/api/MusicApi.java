package com.junhsiun.musicplayer.platform.api;

import com.junhsiun.musicplayer.model.ArtistInfo;
import com.junhsiun.musicplayer.model.PlaylistInfo;
import com.junhsiun.musicplayer.model.ProgramInfo;
import com.junhsiun.musicplayer.model.RadioInfo;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.model.UserPlaylistView;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 音乐数据来源的统一契约。
 *
 * <p>命令、队列与背景音乐只依赖本接口，不关心数据来自网易云直连（{@link DirectMusicApi}）
 * 还是自建/公共后端（{@code NeteaseApiClient}）。新增数据来源时只需再实现一次本接口，
 * 无需改动命令与队列代码。
 *
 * <p>实现约定：所有方法均为异步，失败时以异常完成；调用方线程（服务端主线程）不得被阻塞。
 */
public interface MusicApi {
    // ── 单曲 ────────────────────────────────────────────────────────

    /** 解析单曲详情并附带可播放直链（直链可能为空列表）。 */
    CompletableFuture<TrackInfo> resolveSong(String id);

    /** 解析播客节目所属单曲，语义与 {@link #resolveSong(String)} 相同。 */
    CompletableFuture<TrackInfo> resolveRadioSong(String id);

    // ── 搜索 ────────────────────────────────────────────────────────

    CompletableFuture<List<SearchEntry>> searchSongs(String keyword);

    CompletableFuture<List<SearchEntry>> searchSongs(String keyword, int page);

    CompletableFuture<List<SearchEntry>> searchArtists(String keyword);

    CompletableFuture<List<SearchEntry>> searchArtists(String keyword, int page);

    CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword);

    CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword, int page);

    CompletableFuture<List<SearchEntry>> searchUsers(String keyword);

    CompletableFuture<List<SearchEntry>> searchUsers(String keyword, int page);

    CompletableFuture<List<SearchEntry>> searchRadios(String keyword);

    CompletableFuture<List<SearchEntry>> searchRadios(String keyword, int page);

    // ── 歌单 ────────────────────────────────────────────────────────

    CompletableFuture<PlaylistInfo> playlistDetail(String id);

    /** 按偏移量分页获取歌单曲目，{@code offset} 为歌单内从 0 开始的曲目序号。 */
    CompletableFuture<List<SearchEntry>> playlistTracksPage(String playlistId, int offset, int limit);

    // ── 用户 ────────────────────────────────────────────────────────

    CompletableFuture<UserPlaylistView> userPlaylists(String userId);

    // ── 歌手 ────────────────────────────────────────────────────────

    CompletableFuture<List<TrackInfo>> artistTopSongs(String artistId);

    /** 作者热门歌曲的元数据（不解析直链），随机选曲时先挑少量再解析音源。 */
    CompletableFuture<List<SearchEntry>> artistTopSongEntries(String artistId);

    CompletableFuture<List<SearchEntry>> topArtists(int limit, int offset);

    CompletableFuture<ArtistInfo> artistDetail(String artistId);

    CompletableFuture<List<SearchEntry>> artistSongsPage(String artistId, int offset, int limit);

    // ── 播客 ────────────────────────────────────────────────────────

    CompletableFuture<RadioInfo> radioDetail(String rid);

    CompletableFuture<List<ProgramInfo>> radioPrograms(String rid, int limit, int offset, boolean asc);

    CompletableFuture<ProgramInfo> programDetail(String programId);

    CompletableFuture<List<SearchEntry>> hotRadios(int limit, int offset);

    CompletableFuture<List<SearchEntry>> radioCategories();

    // ── 推荐 ────────────────────────────────────────────────────────

    /** 随机热门歌曲，返回的曲目均已解析出可播放直链。 */
    CompletableFuture<List<TrackInfo>> randomHotTracks(int count);
}
