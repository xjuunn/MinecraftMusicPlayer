package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.platform.beans.netease.*;
import com.junhsiun.core.command.subcommands.vo.SearchVO;
import com.junhsiun.core.utils.ModLogger;
import com.junhsiun.core.utils.OkHttpUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class NeteasePlatform extends BasePlatform {
    @Override
    public String getName() {
        return "网易云音乐";
    }

    @Override
    public String getMusicUrl(String musicID) {
        ModLogger.info(getName() + " " + "播放音乐： " + musicID);
        String url = getMusicNetease2(musicID);
        if (url == null) url = getMusicNetease1(musicID);
        if (url == null) url = getMusicUrl1(musicID);
        ModLogger.info("获取到了音乐链接：" + url);
        return url;
    }

    // https://doc.vkeys.cn/api-doc/v2/%E9%9F%B3%E4%B9%90%E6%A8%A1%E5%9D%97/%E7%BD%91%E6%98%93%E4%BA%91%E9%9F%B3%E4%B9%90/1-netease.html
    // 速度慢 有时候接口出错
    private String getMusicUrl1(String musicId) {
        try {
            VKeysGetUrlBean vKeysGetUrlBean = OkHttpUtil.get("https://api.vkeys.cn/v2/music/netease?id=" + musicId + "&quality=4", VKeysGetUrlBean.class, true);
            return vKeysGetUrlBean.data.url;
        } catch (IOException e) {
            ModLogger.info(e.toString());
            return null;
        }
    }

    // 网易云旧版音乐接口
    private String getMusicNetease1(String musicId) {
        try {
            NeteaseGetUrlBean neteaseGetUrlBean = OkHttpUtil.get("/song/url?id=" + musicId, NeteaseGetUrlBean.class);
            return neteaseGetUrlBean.data.get(0).url;
        } catch (IOException e) {
            ModLogger.info(e.toString());
            return null;
        }
    }

    // 网易云直链获取 速度快 但是可能会重定向到404
    private String getMusicNetease2(String musicId) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL("http://music.163.com/song/media/outer/url?id=" + musicId + ".mp3").openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(5000);
            String location = conn.getHeaderField("location");
            if (location.contains("music.163.com/404")) {
                return null;
            }
            return location;
        } catch (IOException e) {
            ModLogger.info(e.toString());
            return null;
        }
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
