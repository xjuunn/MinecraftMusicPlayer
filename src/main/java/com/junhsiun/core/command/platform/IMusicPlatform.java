package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.subcommands.vo.SearchVO;

import java.util.ArrayList;

public interface IMusicPlatform {
    String getName();
    void play(String musicID);
    ArrayList<SearchVO> searchSong(String keyword);
    String[] searchPlayList(String keyword);
    String[] searchUser(String keyword);
}
// set