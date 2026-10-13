package com.junhsiun.musicplayer.config;

public class MusicPlayerConfig {
    public static final String DEFAULT_NETEASE_BASE_URL = "https://mycelis.dpdns.org/";
    public static final String DEFAULT_BGM_PLAYLIST_ID = "18481322122";

    public String neteaseBaseUrl = DEFAULT_NETEASE_BASE_URL;
    /** 网络背景音乐歌单 ID，空字符串表示关闭。 */
    public String bgmPlaylistId = DEFAULT_BGM_PLAYLIST_ID;
    /** 背景音乐音量（原版与网络背景音乐），0.0 - 1.0，默认 70%。 */
    public double bgmVolume = 0.7D;
    /** 是否启用网络背景音乐（客户端本地全局开关，在设置界面中切换；false 时背景音乐使用原版）。 */
    public boolean enableNetworkBgm = true;
    public String proxy = "";
    public boolean useSystemProxy = true;
    public boolean preferIpv4 = true;
    public boolean allowCustomServer = true;
    public boolean allowSongRequest = true;
    public boolean allowPlaylistRequest = true;
    public boolean autoAdvance = true;
    public boolean announceQueueChanges = true;
    public boolean showLoadingHints = true;
    public boolean showLyrics = false;
    public int connectTimeoutSeconds = 10;
    public int readTimeoutSeconds = 20;
    public int searchLimit = 8;
    public int maxQueueSize = 40;
    /** 每个玩家在同一队列中最多可点播的歌曲数量。 */
    public int maxSongsPerPlayer = 5;
    public int playlistQueueLimit = 20;
    public int queueCacheSize = 3;
    public double voteSkipPercent = 0.6D;
    public boolean enableLootMusicDiscs = true;
    public double lootMusicDiscChance = 0.3D;
    public int lootMusicDiscCount = 1;
}
