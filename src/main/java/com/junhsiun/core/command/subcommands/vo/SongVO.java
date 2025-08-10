package com.junhsiun.core.command.subcommands.vo;

import java.util.Objects;

public class SongVO {
    String id;
    String name;
    String singer;
    String url;

    public SongVO() {
        this.name = "未知音乐";
        this.singer = "未知歌手";
    }

    public SongVO(String id, String name, String singer, String url) {
        this.id = id;
        this.name = name;
        this.singer = singer;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isEmpty() || name.equals("null")) {
            this.name = "未知音乐";
        } else {
            this.name = name;
        }
    }

    public String getSinger() {
        return singer;
    }

    public void setSinger(String singer) {
        if (Objects.equals(singer, "") || singer == null || name.equals("null")) {
            this.singer = "未知歌手";
        } else {
            this.singer = singer;
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "SongVO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", singer='" + singer + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
