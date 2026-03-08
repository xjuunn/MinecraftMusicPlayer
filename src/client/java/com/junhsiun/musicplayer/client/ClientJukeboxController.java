package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.network.JukeboxMusicPayload;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientJukeboxController {
    private static final ClientJukeboxController INSTANCE = new ClientJukeboxController();

    private final Map<Long, PlaybackHandle> playbackHandles = new ConcurrentHashMap<>();

    private ClientJukeboxController() {
    }

    public static ClientJukeboxController getInstance() {
        return INSTANCE;
    }

    public void handle(JukeboxMusicPayload payload) {
        switch (payload.action()) {
            case "play" -> play(payload.jukeboxPos(), payload.urls(), payload.title(), payload.subtitle());
            case "stop" -> stop(payload.jukeboxPos());
            default -> MusicPlayerMod.LOGGER.warn("Unknown jukebox action: {}", payload.action());
        }
    }

    public void stopAll(String reason) {
        playbackHandles.keySet().forEach(this::stop);
        if (reason != null && !reason.isBlank()) {
            MusicPlayerMod.LOGGER.info("Stopped all jukebox playback: {}", reason);
        }
    }

    private void play(long jukeboxPos, List<String> urls, String title, String subtitle) {
        stop(jukeboxPos);
        PlaybackHandle handle = new PlaybackHandle();
        Thread playbackThread = new Thread(() -> playWithFallback(jukeboxPos, handle, urls, title, subtitle), "musicplayer-jukebox-" + jukeboxPos);
        playbackThread.setDaemon(true);
        handle.thread = playbackThread;
        playbackHandles.put(jukeboxPos, handle);
        playbackThread.start();
    }

    private void stop(long jukeboxPos) {
        PlaybackHandle handle = playbackHandles.remove(jukeboxPos);
        if (handle == null) {
            return;
        }
        if (handle.player != null) {
            handle.player.close();
            handle.player = null;
        }
        if (handle.thread != null) {
            handle.thread.interrupt();
            handle.thread = null;
        }
    }

    private void playWithFallback(long jukeboxPos, PlaybackHandle handle, List<String> urls, String title, String subtitle) {
        if (urls == null || urls.isEmpty()) {
            MusicPlayerMod.LOGGER.warn("Jukebox {} has no playable urls: {} - {}", jukeboxPos, title, subtitle);
            playbackHandles.remove(jukeboxPos, handle);
            return;
        }

        for (String url : urls) {
            if (Thread.currentThread().isInterrupted()) {
                playbackHandles.remove(jukeboxPos, handle);
                return;
            }
            try {
                playSingle(handle, url, title, subtitle);
                playbackHandles.remove(jukeboxPos, handle);
                return;
            } catch (IOException | JavaLayerException exception) {
                if (Thread.currentThread().isInterrupted() || exception instanceof InterruptedIOException) {
                    playbackHandles.remove(jukeboxPos, handle);
                    return;
                }
                MusicPlayerMod.LOGGER.warn("Jukebox source failed, trying next source: {}", url, exception);
            }
        }
        playbackHandles.remove(jukeboxPos, handle);
    }

    private void playSingle(PlaybackHandle handle, String url, String title, String subtitle) throws IOException, JavaLayerException {
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
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body");
            }
            byte[] audioBytes = body.bytes();
            if (audioBytes.length == 0) {
                throw new IOException("Empty audio data");
            }
            try (BufferedInputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(audioBytes))) {
                Player currentPlayer = new Player(inputStream);
                handle.player = currentPlayer;
                MusicPlayerMod.LOGGER.info("Start jukebox playback: {} - {}", title, subtitle);
                currentPlayer.play();
            }
        } finally {
            handle.player = null;
        }
    }

    private static final class PlaybackHandle {
        private volatile Player player;
        private volatile Thread thread;
    }
}
