package com.junhsiun.core.musicPlayer;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class MusicPlayer {
    private Player player;

    private MusicPlayer() {
    }

    public Player getPlayer() {
        return player;
    }

    private static class Holder {
        private static final MusicPlayer INSTANCE = new MusicPlayer();
    }

    public static MusicPlayer getInstance() {
        return Holder.INSTANCE;
    }

    public void loadLoaclMusic(String url) throws FileNotFoundException, JavaLayerException {
        FileInputStream fileInputStream = new FileInputStream(url);
        this.player = new Player(fileInputStream);
    }

    public void play() throws JavaLayerException {
        this.player.play();
    }

    public void close() {
        this.player.close();
    }
}
