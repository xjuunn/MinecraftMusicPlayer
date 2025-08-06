package com.junhsiun.core.command.platform;

import com.junhsiun.core.utils.ModLogger;

public class NeteasePlatform extends BasePlatform implements ILoginable {
    @Override
    public String getName() {
        return "网易云音乐";
    }

    @Override
    public void play(String musicID) {
        ModLogger.info(getName() + " " + "播放音乐： " + musicID);
    }

    @Override
    public String[] searchSong(String keyword) {
        ModLogger.info(getName() + " " + "搜索音乐： " + keyword);
        return new String[0];
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

    @Override
    public void login() {
        ModLogger.info(getName() + "登录");
    }

    @Override
    public void logout() {
        ModLogger.info(getName() + "登出");
    }
}
