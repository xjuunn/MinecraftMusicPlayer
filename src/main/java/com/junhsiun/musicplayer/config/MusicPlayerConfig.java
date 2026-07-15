package com.junhsiun.musicplayer.config;

public class MusicPlayerConfig {
    public static final String DEFAULT_NETEASE_BASE_URL = "https://mycelis.dpdns.org/";

    public String neteaseBaseUrl = DEFAULT_NETEASE_BASE_URL;
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
    public int playlistQueueLimit = 20;
    public int queueCacheSize = 3;
    public double voteSkipPercent = 0.6D;
    public boolean enableLootMusicDiscs = true;
    public double lootMusicDiscChance = 0.3D;
    public int lootMusicDiscCount = 1;
}
