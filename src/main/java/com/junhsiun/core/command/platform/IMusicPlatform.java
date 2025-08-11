package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.subcommands.vo.SearchVO;
import com.junhsiun.core.command.subcommands.vo.SongVO;
import com.junhsiun.core.utils.HttpCallback;

import java.util.ArrayList;

public interface IMusicPlatform {
    String getName();

    public abstract void getMusicUrl(String musicID, HttpCallback<String> callback);

    public abstract void getMusicInfo(String musicID, HttpCallback<SongVO> callback);

    public abstract void searchSong(String keyword, HttpCallback<ArrayList<SearchVO>> callback);

    public abstract void searchPlayList(String keyword, HttpCallback<ArrayList<SearchVO>> callback);

    public abstract void searchUser(String keyword, HttpCallback<ArrayList<SearchVO>> callback);
}