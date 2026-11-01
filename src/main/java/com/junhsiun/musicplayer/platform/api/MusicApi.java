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

    /**
     * 一页搜索结果。
     *
     * @param entries 本页条目
     * @param total   该关键词的总命中数；{@code <= 0} 表示数据来源没有提供，调用方不应据此显示总数
     */
    record SearchPage(List<SearchEntry> entries, int total) {
        public SearchPage {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        /** 数据来源是否给出了总数。 */
        public boolean hasTotal() {
            return total > 0;
        }
    }

    // ── 单曲 ────────────────────────────────────────────────────────

    /** 解析单曲详情并附带可播放直链（直链可能为空列表）。 */
    CompletableFuture<TrackInfo> resolveSong(String id);

    /** 解析播客节目所属单曲，语义与 {@link #resolveSong(String)} 相同。 */
    CompletableFuture<TrackInfo> resolveRadioSong(String id);

    /**
     * 只取封面地址，不解析可播放直链。
     *
     * <p>直链解析要探测多个音源，慢的时候要好几秒；而封面是唱片一插进唱片机就要用的东西。
     * 单独给封面一条短路径，避免它被直链解析拖住。默认实现退化成整曲解析。</p>
     */
    default CompletableFuture<String> songCover(String id) {
        return resolveSong(id).thenApply(TrackInfo::coverUrl);
    }

    // ── 搜索 ────────────────────────────────────────────────────────

    CompletableFuture<List<SearchEntry>> searchSongs(String keyword);

    CompletableFuture<List<SearchEntry>> searchSongs(String keyword, int page);

    /**
     * 分页搜索歌曲，并带回总命中数。
     *
     * <p>默认实现退化为"只有本页条目、总数未知"（{@code total = -1}）。不是所有数据来源都能
     * 给出总数，调用方必须用 {@link SearchPage#hasTotal()} 判断能否显示"共 N 首"，
     * 拿不到就不要显示，而不是用本页条数冒充。</p>
     */
    default CompletableFuture<SearchPage> searchSongPage(String keyword, int page) {
        return searchSongs(keyword, page).thenApply(entries -> new SearchPage(entries, -1));
    }

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
