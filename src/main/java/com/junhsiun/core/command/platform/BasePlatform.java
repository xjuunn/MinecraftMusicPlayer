package com.junhsiun.core.command.platform;

public abstract class BasePlatform implements IMusicPlatform {
    private String BaseUrl;


    public String getBaseUrl() {
        return BaseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        BaseUrl = baseUrl;
    }

    public abstract String[] searchPlayList(String keyword);

    public abstract String[] searchUser(String keyword);
}
