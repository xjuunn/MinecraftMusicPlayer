package com.junhsiun.core.musicPlayer;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class ModMusicPlayer {
    private Player player;
    private Thread thread;

    private ModMusicPlayer() {
    }

    public Player getPlayer() {
        return player;
    }

    private static class Holder {
        private static final ModMusicPlayer INSTANCE = new ModMusicPlayer();
    }

    public static ModMusicPlayer getInstance() {
        return Holder.INSTANCE;
    }

    public void loadLoaclMusic(String url) throws FileNotFoundException, JavaLayerException {
        FileInputStream fileInputStream = new FileInputStream(url);
        this.player = new Player(fileInputStream);
    }

    public void loadNetworkMusic(String url) throws IOException, JavaLayerException {
        BufferedInputStream bis = new BufferedInputStream(new URL(url).openStream());
        this.player = new Player(bis);

    }

    public void play() throws JavaLayerException {
        if (this.player == null) return;

        thread = new Thread(() -> {
            try {
                this.player.play();
            } catch (JavaLayerException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
    }

    public void close() {
        if (this.player != null) {
            this.player.close();
            this.player = null;
        }

        if (this.thread != null) {
            this.thread.interrupt();
            this.thread = null;
        }
    }
}
