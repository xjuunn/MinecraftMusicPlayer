package com.junhsiun.core.command.platform.beans.netease;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VKeysGetUrlBean {

    @JsonProperty("code")
    public Integer code;
    @JsonProperty("message")
    public String message;
    @JsonProperty("data")
    public DataDTO data;
    @JsonProperty("time")
    public String time;
    @JsonProperty("pid")
    public Integer pid;
    @JsonProperty("tips")
    public String tips;

    public static class DataDTO {
        @JsonProperty("id")
        public long id;
        @JsonProperty("song")
        public String song;
        @JsonProperty("singer")
        public String singer;
        @JsonProperty("album")
        public String album;
        @JsonProperty("time")
        public String time;
        @JsonProperty("quality")
        public String quality;
        @JsonProperty("cover")
        public String cover;
        @JsonProperty("interval")
        public String interval;
        @JsonProperty("link")
        public String link;
        @JsonProperty("size")
        public String size;
        @JsonProperty("kbps")
        public String kbps;
        @JsonProperty("url")
        public String url;
    }
}
