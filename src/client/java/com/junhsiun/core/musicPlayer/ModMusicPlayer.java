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
    private ModMusicPlayerStatus status;

    private ModMusicPlayer() {
    }

    public Player getPlayer() {
        return player;
    }

    public ModMusicPlayerStatus getStatus() {
        return status;
    }

    public void setStatus(ModMusicPlayerStatus status) {
        this.status = status;
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

    public void leave() {
        this.status = ModMusicPlayerStatus.Leave;
        this.close();
    }

    public void join() {
        this.status = ModMusicPlayerStatus.Standby;
    }

    public void muteOnce() {
        this.status = ModMusicPlayerStatus.MuteOnce;
    }

    public void play() {
        if (this.player == null) return;
        if (this.status == ModMusicPlayerStatus.Leave) return;
        this.status = ModMusicPlayerStatus.Playing;
        thread = new Thread(() -> {
            try {
                this.player.play();
                if (this.status == ModMusicPlayerStatus.Playing || this.status == ModMusicPlayerStatus.MuteOnce) {
                    this.status = ModMusicPlayerStatus.Standby;
                }
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
