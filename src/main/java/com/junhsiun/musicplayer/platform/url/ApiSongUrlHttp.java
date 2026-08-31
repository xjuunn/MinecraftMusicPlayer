package com.junhsiun.musicplayer.platform.url;

import com.google.gson.JsonObject;

import java.io.IOException;

/**
 * {@link SongUrlHttp} 的默认实现，委托给 {@link ApiHttp} 发起真实网络请求。
 */
public enum ApiSongUrlHttp implements SongUrlHttp {
    INSTANCE;

    @Override
    public JsonObject getJson(String url, String... queryPairs) throws IOException {
        return ApiHttp.getJson(url, queryPairs);
    }

    @Override
    public String getText(String url, String... queryPairs) throws IOException {
        return ApiHttp.getText(url, queryPairs);
    }

    @Override
    public String getContentType(String url) throws IOException {
        return ApiHttp.getContentType(url);
    }
}
