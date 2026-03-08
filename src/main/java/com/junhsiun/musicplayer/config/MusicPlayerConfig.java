package com.junhsiun.musicplayer.config;

public class MusicPlayerConfig {
    public String neteaseBaseUrl = "http://127.0.0.1:3000";
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
