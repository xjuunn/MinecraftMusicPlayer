package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.platform.beans.netease.SearchBean;
import com.junhsiun.core.command.platform.beans.netease.SearchPlayListBean;
import com.junhsiun.core.command.platform.beans.netease.SearchUserBean;
import com.junhsiun.core.command.subcommands.vo.SearchVO;
import com.junhsiun.core.utils.ModLogger;
import com.junhsiun.core.utils.OkHttpUtil;

import java.io.IOException;
import java.util.ArrayList;

public class NeteasePlatform extends BasePlatform {
    @Override
    public String getName() {
        return "网易云音乐";
    }

    @Override
    public void play(String musicID) {
        ModLogger.info(getName() + " " + "播放音乐： " + musicID);
    }

    @Override
    public ArrayList<SearchVO> searchSong(String keyword) {
        ModLogger.info(getName() + " " + "搜索音乐： " + keyword);
        try {
            SearchBean searchBean = OkHttpUtil.get("/cloudsearch?limit=10&type=1&keywords=" + keyword, SearchBean.class);
            ArrayList<SearchVO> searchVOS = new ArrayList<>();
            if (searchBean == null || searchBean.result == null || searchBean.result.songs == null || searchBean.result.songs.isEmpty()) {
                return searchVOS;
            }
            searchBean.result.songs.forEach((song) -> {
                SearchVO searchVO = new SearchVO(song.id, song.name, song.al.name);
                searchVOS.add(searchVO);
            });
            return searchVOS;
        } catch (IOException e) {
            ModLogger.error(e.toString());
            return new ArrayList<>();
        }
    }

    @Override
    public ArrayList<SearchVO> searchPlayList(String keyword) {
        ModLogger.info(getName() + " " + "搜索歌单： " + keyword);
        try {
            SearchPlayListBean playListBean = OkHttpUtil.get("/cloudsearch?limit=10&type=1000&keywords=" + keyword, SearchPlayListBean.class);
            ArrayList<SearchVO> searchVOS = new ArrayList<>();
            if (playListBean == null || playListBean.result == null || playListBean.result.playlists == null || playListBean.result.playlists.isEmpty()) {
                return searchVOS;
            }
            playListBean.result.playlists.forEach(playlist -> {
                SearchVO searchVO = new SearchVO(playlist.id, playlist.name, playlist.creator.nickname);
                searchVOS.add(searchVO);
            });
            return searchVOS;
        } catch (IOException e) {
            ModLogger.error(e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public ArrayList<SearchVO> searchUser(String keyword) {
        ModLogger.info(getName() + " " + "搜索用户： " + keyword);
        try {
            SearchUserBean playListBean = OkHttpUtil.get("/cloudsearch?limit=10&type=1002&keywords=" + keyword, SearchUserBean.class);
            ArrayList<SearchVO> searchVOS = new ArrayList<>();
            if (playListBean == null || playListBean.result == null || playListBean.result.userprofiles == null || playListBean.result.userprofiles.isEmpty()) {
                return searchVOS;
            }
            playListBean.result.userprofiles.forEach(user -> {
                SearchVO searchVO = new SearchVO(user.userId, user.nickname, user.signature);
                searchVOS.add(searchVO);
            });
            return searchVOS;
        } catch (IOException e) {
            ModLogger.error(e.toString());
            throw new RuntimeException(e);
        }
    }
}
