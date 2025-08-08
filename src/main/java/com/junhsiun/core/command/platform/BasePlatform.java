package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.subcommands.vo.SearchVO;

import java.util.ArrayList;

public abstract class BasePlatform implements IMusicPlatform {
    private String BaseUrl;


    public String getBaseUrl() {
        return BaseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        BaseUrl = baseUrl;
    }

    public abstract ArrayList<SearchVO> searchPlayList(String keyword);

    public abstract ArrayList<SearchVO> searchUser(String keyword);
}
