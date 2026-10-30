package com.junhsiun.musicplayer.platform.api;

/**
 * 网易云音乐网页版（明文）接口端点常量。
 *
 * <p>这些端点直接返回明文 JSON，不需要 weapi/eapi 的 AES/RSA 加密，
 * 是模组当前用到的全部能力来源。集中在此便于后续调整路径或排查接口变更。
 */
public final class NeteaseEndpoints {
    /** 直连主机；网页版接口在 {@code /api/} 前缀下工作。 */
    public static final String HOST = "https://music.163.com";

    /** 歌单详情，{@code n} 与 {@code s} 需给足才会返回全量 {@code trackIds}。 */
    public static final String PLAYLIST_DETAIL = "/api/v6/playlist/detail";
    /** 批量歌曲详情，POST 表单 {@code c=[{"id":"..."}]}，单次上限约 500 个。 */
    public static final String SONG_DETAIL = "/api/v3/song/detail";
    /** 批量播放地址，{@code ids=[1,2,3]}&{@code br=999000}，单次上限约 400 个。 */
    public static final String SONG_PLAYER_URL = "/api/song/enhance/player/url";
    /** 搜索，type: 1 单曲 / 100 歌手 / 1000 歌单 / 1002 用户 / 1009 播客。 */
    public static final String CLOUD_SEARCH = "/api/cloudsearch/pc";

    public static final String ARTIST_DETAIL = "/api/v1/artist/";
    public static final String ARTIST_SONGS = "/api/v1/artist/songs";
    public static final String ARTIST_TOP_SONG = "/api/artist/top/song";
    public static final String ARTIST_TOP = "/api/artist/top";

    public static final String PLAYLIST_HOT_TAGS = "/api/playlist/hottags";
    public static final String PLAYLIST_LIST = "/api/playlist/list";
    public static final String USER_PLAYLIST = "/api/user/playlist";

    public static final String RADIO_DETAIL = "/api/djradio/v2/get";
    public static final String RADIO_PROGRAMS = "/api/dj/program/byradio";
    public static final String PROGRAM_DETAIL = "/api/dj/program/detail";
    public static final String RADIO_HOT = "/api/djradio/hot/v1";
    public static final String RADIO_CATEGORY_RECOMMEND = "/api/djradio/home/category/recommend";

    /** 网页版接口对移动端 UA 与 Referer 最友好，缺失时部分接口会返回空数据。 */
    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 11; M2012K11AC Build/RKQ1.200826.002; wv) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Mobile Safari/537.36";
    public static final String REFERER = "https://music.163.com";

    private NeteaseEndpoints() {
    }
}
