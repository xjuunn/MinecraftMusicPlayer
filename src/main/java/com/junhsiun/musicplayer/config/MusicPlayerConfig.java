package com.junhsiun.musicplayer.config;

public class MusicPlayerConfig {
    public static final String DEFAULT_NETEASE_BASE_URL = "https://mycelis.dpdns.org/";
    public static final String DEFAULT_BGM_PLAYLIST_ID = "18481322122";

    public String neteaseBaseUrl = DEFAULT_NETEASE_BASE_URL;
    /** 网络背景音乐歌单 ID，空字符串表示关闭。（仅服务端管理；音量与开关在客户端独立配置中） */
    public String bgmPlaylistId = DEFAULT_BGM_PLAYLIST_ID;
    public String proxy = "";
    public boolean useSystemProxy = true;
    public boolean preferIpv4 = true;
    public boolean allowCustomServer = true;
    public boolean allowSongRequest = true;
    public boolean allowPlaylistRequest = true;
    public boolean autoAdvance = true;
    public boolean announceQueueChanges = true;
    public boolean showLoadingHints = true;
    /** 无玩家个人偏好时是否默认显示歌词。 */
    public boolean showLyrics = true;
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

    /**
     * 规范化 API 地址：去除首尾空白，并剥掉命令参数或配置文件中多余的成对引号。
     * {@code greedyString()} 会保留用户输入的引号，直接拼接会导致 URL 解析失败。
     */
    public static String normalizeBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        while (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1).trim();
            } else {
                break;
            }
        }
        return value;
    }
}
