package com.junhsiun.musicplayer.config;

public class MusicPlayerConfig {
    public static final String DEFAULT_NETEASE_BASE_URL = "https://odlimemusicapi.vercel.app";

    public String neteaseBaseUrl = DEFAULT_NETEASE_BASE_URL;
    public boolean allowCustomServer = true;
    public boolean allowSongRequest = true;
    public boolean allowPlaylistRequest = true;
    public boolean autoAdvance = true;
    public boolean announceQueueChanges = true;
    public boolean showLoadingHints = true;
    public int searchLimit = 8;
    public int maxQueueSize = 40;
    public int playlistQueueLimit = 20;
    public double voteSkipPercent = 0.6D;
}
