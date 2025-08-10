package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.subcommands.vo.SearchVO;
import com.junhsiun.core.command.subcommands.vo.SongVO;

import java.util.ArrayList;

public interface IMusicPlatform {
    String getName();

    String getMusicUrl(String musicID);

    SongVO getMusicInfo(String musicID);

    ArrayList<SearchVO> searchSong(String keyword);

    ArrayList<SearchVO> searchPlayList(String keyword);

    ArrayList<SearchVO> searchUser(String keyword);
}
// set