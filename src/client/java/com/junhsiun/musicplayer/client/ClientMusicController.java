package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;

public final class ClientMusicController {
    private static final ClientMusicController INSTANCE = new ClientMusicController();

    private Player player;
    private Thread playbackThread;

    private ClientMusicController() {
    }

    public static ClientMusicController getInstance() {
        return INSTANCE;
    }

    public void handle(MusicControlPayload payload) {
        switch (payload.action()) {
            case "play" -> play(payload.url(), payload.title(), payload.subtitle());
            case "stop" -> stop(payload.message());
            default -> MusicPlayerMod.LOGGER.warn("未知的音乐控制动作: {}", payload.action());
        }
    }

    public synchronized void stop(String reason) {
        if (player != null) {
            player.close();
            player = null;
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
        if (reason != null && !reason.isBlank()) {
            MusicPlayerMod.LOGGER.info("客户端已停止播放: {}", reason);
        }
    }

    private synchronized void play(String url, String title, String subtitle) {
        stop(null);
        playbackThread = new Thread(() -> {
            try (BufferedInputStream inputStream = new BufferedInputStream(new URL(url).openStream())) {
                player = new Player(inputStream);
                MusicPlayerMod.LOGGER.info("开始播放: {} - {}", title, subtitle);
                player.play();
            } catch (IOException | JavaLayerException exception) {
                MusicPlayerMod.LOGGER.error("播放歌曲失败。", exception);
            } finally {
                synchronized (ClientMusicController.this) {
                    player = null;
                    playbackThread = null;
                }
            }
        }, "musicplayer-client-playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }
}