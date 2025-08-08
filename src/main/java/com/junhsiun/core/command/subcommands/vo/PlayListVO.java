package com.junhsiun.core.command.subcommands.vo;

import java.util.ArrayList;

public class PlayListVO {
    private String id;
    private String name;
    private String userId;
    private String username;
    private ArrayList<SearchVO> songsList;

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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public ArrayList<SearchVO> getSongsList() {
        return songsList;
    }

    public void setSongsList(ArrayList<SearchVO> songsList) {
        this.songsList = songsList;
    }

    public void addSong(SearchVO song) {
        this.songsList.add(song);
    }
}
