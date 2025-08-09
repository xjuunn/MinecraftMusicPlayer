package com.junhsiun.core.command.subcommands.vo;

import java.util.ArrayList;

public class UserVO {
    String id;
    String name;
    String signature;
    ArrayList<SearchVO> playlist;

    public UserVO() {
    }

    public UserVO(String id, String name, String signature) {
        this.id = id;
        this.name = name;
        this.signature = signature;
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

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public ArrayList<SearchVO> getPlaylist() {
        return playlist;
    }

    public void setPlaylist(ArrayList<SearchVO> playlist) {
        this.playlist = playlist;
    }

    @Override
    public String toString() {
        return "UserVO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", signature='" + signature + '\'' +
                ", playlist=" + playlist +
                '}';
    }
}
