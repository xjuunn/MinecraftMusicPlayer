package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

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
            case "play" -> play(payload.urls(), payload.title(), payload.subtitle());
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

    private synchronized void play(List<String> urls, String title, String subtitle) {
        stop(null);
        playbackThread = new Thread(() -> playWithFallback(urls, title, subtitle), "musicplayer-client-playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void playWithFallback(List<String> urls, String title, String subtitle) {
        if (urls == null || urls.isEmpty()) {
            MusicPlayerMod.LOGGER.error("没有可用的播放源: {} - {}", title, subtitle);
            return;
        }

        Exception lastException = null;
        for (int index = 0; index < urls.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            String url = urls.get(index);
            try {
                MusicPlayerMod.LOGGER.info("开始尝试播放源 {}/{}: {} - {}", index + 1, urls.size(), title, url);
                playSingle(url, title, subtitle);
                return;
            } catch (IOException | JavaLayerException exception) {
                lastException = exception;
                MusicPlayerMod.LOGGER.warn("播放源失败，尝试下一个源: {}", url, exception);
            }
        }

        MusicPlayerMod.LOGGER.error("所有播放源都不可用: {} - {}", title, subtitle, lastException);
    }

    private void playSingle(String url, String title, String subtitle) throws IOException, JavaLayerException {
        OkHttpClient client = HttpClientFactory.create();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MinecraftMusicPlayer/2.0")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            InputStream bodyStream = response.body() == null ? null : response.body().byteStream();
            if (bodyStream == null) {
                throw new IOException("空响应体");
            }

            try (BufferedInputStream inputStream = new BufferedInputStream(bodyStream)) {
                Player currentPlayer = new Player(inputStream);
                synchronized (this) {
                    if (Thread.currentThread().isInterrupted()) {
                        currentPlayer.close();
                        return;
                    }
                    player = currentPlayer;
                }
                MusicPlayerMod.LOGGER.info("开始播放: {} - {}", title, subtitle);
                currentPlayer.play();
            }
        } finally {
            synchronized (this) {
                player = null;
                if (Thread.currentThread() == playbackThread) {
                    playbackThread = null;
                }
            }
        }
    }
}
