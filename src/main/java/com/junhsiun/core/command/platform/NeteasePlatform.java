package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.platform.beans.netease.SearchBean;
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
            searchBean.result.songs.forEach((song) -> {
                SearchVO searchVO = new SearchVO(song.id, song.name, song.al.name, "", false);
                if (song.fee == 1) searchVO.setVip(true);
                searchVOS.add(searchVO);
            });
            return searchVOS;
        } catch (IOException e) {
            ModLogger.error(e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public String[] searchPlayList(String keyword) {
        ModLogger.info(getName() + " " + "搜索歌单： " + keyword);
        return new String[0];
    }

    @Override
    public String[] searchUser(String keyword) {
        ModLogger.info(getName() + " " + "搜索用户： " + keyword);
        return new String[0];
    }
}
