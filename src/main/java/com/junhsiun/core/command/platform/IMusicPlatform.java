package com.junhsiun.core.command.platform;

public interface IMusicPlatform {
    String getName();
    void play(String musicID);
    String[] searchSong(String keyword);
    String[] searchPlayList(String keyword);
    String[] searchUser(String keyword);
}
// set