package com.junhsiun.core.command.subcommands.vo;

public class SongVO {
    String id;
    String name;
    String singer;
    String url;

    public SongVO() {
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
        this.name = name;
    }

    public String getSinger() {
        return singer;
    }

    public void setSinger(String singer) {
        this.singer = singer;
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
