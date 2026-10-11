package com.junhsiun.musicplayer.model;

import com.junhsiun.musicplayer.util.Java8;

import java.util.List;
import java.util.Objects;

public final class UserPlaylistView {
    private final String id;
    private final String name;
    private final String signature;
    private final List<SearchEntry> playlists;

    public UserPlaylistView(String id, String name, String signature, List<SearchEntry> playlists) {
        this.id = id;
        this.name = name;
        this.signature = signature;
        this.playlists = Java8.copyOf(playlists);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String signature() {
        return signature;
    }

    public List<SearchEntry> playlists() {
        return playlists;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserPlaylistView)) {
            return false;
        }
        UserPlaylistView that = (UserPlaylistView) o;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(signature, that.signature)
                && Objects.equals(playlists, that.playlists);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, signature, playlists);
    }

    @Override
    public String toString() {
        return "UserPlaylistView{id='" + id + "', name='" + name + "', signature='" + signature + "', playlists=" + playlists + "}";
    }
}
