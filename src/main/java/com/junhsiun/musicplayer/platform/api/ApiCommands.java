package com.junhsiun.musicplayer.platform.api;

/**
 * 列表项点击后执行的命令文本。
 *
 * <p>集中管理命令前缀，避免各处拼接字符串写错路径。
 */
public final class ApiCommands {
    private ApiCommands() {
    }

    public static String playSong(String songId) {
        return "/music play song " + songId;
    }

    public static String viewArtist(String artistId) {
        return "/music view artist " + artistId;
    }

    public static String viewPlaylist(String playlistId) {
        return "/music view playlist " + playlistId;
    }

    public static String viewUser(String userId) {
        return "/music view user " + userId;
    }

    public static String viewRadio(String rid) {
        return "/music view radio " + rid;
    }

    public static String playRadio(String rid) {
        return "/music play radio " + rid;
    }
}
