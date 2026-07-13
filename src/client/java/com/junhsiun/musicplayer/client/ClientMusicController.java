package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import com.junhsiun.musicplayer.network.MusicPlaybackReportPayload;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDeviceBase;
import javazoom.jl.player.Player;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;

public final class ClientMusicController {
    private static final ClientMusicController INSTANCE = new ClientMusicController();

    private final BackgroundMusicAudioDevice audioDevice = new BackgroundMusicAudioDevice();
    private Player player;
    private Thread playbackThread;
    private String currentTrackId = "";
    private volatile boolean backgroundMusicPlaying;

    private ClientMusicController() {
    }

    public static ClientMusicController getInstance() {
        return INSTANCE;
    }

    public boolean isBackgroundMusicPlaying() {
        return backgroundMusicPlaying;
    }

    public void handle(MusicControlPayload payload) {
        switch (payload.action()) {
            case "play" -> play(payload.trackId(), payload.urls(), payload.title(), payload.subtitle(), payload.offsetMillis());
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
        audioDevice.reset();
        currentTrackId = "";
        backgroundMusicPlaying = false;
        if (reason != null && !reason.isBlank()) {
            MusicPlayerMod.LOGGER.info("客户端已停止播放: {}", reason);
        }
    }

    private synchronized void play(String trackId, List<String> urls, String title, String subtitle, long offsetMillis) {
        stop(null);
        currentTrackId = trackId == null ? "" : trackId;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getMusicManager() != null) {
            minecraft.getMusicManager().stopPlaying();
        }
        backgroundMusicPlaying = true;
        audioDevice.setSeekPosition(offsetMillis);
        playbackThread = new Thread(() -> playWithFallback(urls, currentTrackId, title, subtitle), "musicplayer-client-playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void playWithFallback(List<String> urls, String trackId, String title, String subtitle) {
        if (urls == null || urls.isEmpty()) {
            MusicPlayerMod.LOGGER.error("没有可用的播放源: {} - {}", title, subtitle);
            reportFailed(trackId, "没有可用的播放源");
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
                if (!Thread.currentThread().isInterrupted()) {
                    reportEnded(trackId);
                }
                return;
            } catch (IOException | JavaLayerException exception) {
                if (Thread.currentThread().isInterrupted() || exception instanceof InterruptedIOException) {
                    return;
                }
                lastException = exception;
                MusicPlayerMod.LOGGER.warn("播放源失败，尝试下一个源: {}", url, exception);
            }
        }

        MusicPlayerMod.LOGGER.error("所有播放源都不可用: {} - {}", title, subtitle, lastException);
        reportFailed(trackId, lastException == null ? "所有播放源都不可用" : lastException.getMessage());
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
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("空响应体");
            }
            byte[] audioBytes = body.bytes();
            if (audioBytes.length == 0) {
                throw new IOException("音频数据为空");
            }

            audioDevice.reset();
            try (BufferedInputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(audioBytes))) {
                Player currentPlayer = new Player(inputStream, audioDevice);
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
                    backgroundMusicPlaying = false;
                }
            }
        }
    }

    private void reportEnded(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return;
        }
        ClientPlayNetworking.send(MusicPlaybackReportPayload.ended(trackId));
    }

    private void reportFailed(String trackId, String message) {
        if (trackId == null || trackId.isBlank()) {
            return;
        }
        ClientPlayNetworking.send(MusicPlaybackReportPayload.failed(trackId, message));
    }

    private static final class BackgroundMusicAudioDevice extends AudioDeviceBase {
        private static final double FADE_OUT_STEP = 0.1D;
        private static final double FADE_IN_STEP = 0.02D;

        private SourceDataLine sourceLine;
        private AudioFormat audioFormat;
        private byte[] byteBuffer;
        private FloatControl gainControl;
        private double jukeboxFadeVolume = 1.0D;
        private long seekPositionMillis;
        private long decodedMillis;

        private void setSeekPosition(long millis) {
            this.seekPositionMillis = millis;
            this.decodedMillis = 0L;
        }

        private void reset() {
            if (sourceLine != null) {
                sourceLine.flush();
                sourceLine.stop();
                sourceLine.close();
                sourceLine = null;
            }
            gainControl = null;
            audioFormat = null;
            jukeboxFadeVolume = 1.0D;
        }

        @Override
        protected void openImpl() {
        }

        @Override
        protected void writeImpl(short[] samples, int offs, int len) throws JavaLayerException {
            if (sourceLine == null) {
                createSource();
            }
            updateVolume();
            if (seekPositionMillis > 0L) {
                float sampleRate = audioFormat.getSampleRate();
                int channels = audioFormat.getChannels();
                float frameMillis = (float) (len / channels) / sampleRate * 1000.0f;
                decodedMillis += (long) frameMillis;
                if (decodedMillis < seekPositionMillis) {
                    return;
                }
                seekPositionMillis = 0L;
            }
            byte[] buffer = getByteArray(len * 2);
            int index = 0;
            for (int i = offs; i < offs + len; i++) {
                short sample = samples[i];
                buffer[index++] = (byte) sample;
                buffer[index++] = (byte) (sample >>> 8);
            }
            sourceLine.write(buffer, 0, index);
        }

        private void createSource() throws JavaLayerException {
            audioFormat = new AudioFormat(
                    getDecoder().getOutputFrequency(),
                    16,
                    getDecoder().getOutputChannels(),
                    true,
                    false
            );
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
            try {
                Line line = AudioSystem.getLine(lineInfo);
                if (line instanceof SourceDataLine dataLine) {
                    sourceLine = dataLine;
                    sourceLine.open(audioFormat);
                    if (sourceLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        gainControl = (FloatControl) sourceLine.getControl(FloatControl.Type.MASTER_GAIN);
                    }
                    sourceLine.start();
                }
            } catch (LineUnavailableException | RuntimeException | LinkageError exception) {
                throw new JavaLayerException("Unable to open audio line for background music", exception);
            }
        }

        @Override
        protected void flushImpl() {
            if (sourceLine != null) {
                sourceLine.drain();
            }
        }

        @Override
        protected void closeImpl() {
            if (sourceLine != null) {
                sourceLine.flush();
                sourceLine.stop();
                sourceLine.close();
                sourceLine = null;
            }
        }

        @Override
        public int getPosition() {
            return sourceLine == null ? 0 : (int) (sourceLine.getMicrosecondPosition() / 1000L);
        }

        private byte[] getByteArray(int length) {
            if (byteBuffer == null || byteBuffer.length < length) {
                byteBuffer = new byte[length];
            }
            return byteBuffer;
        }

        private void updateVolume() {
            if (gainControl == null) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            float musicVolume = minecraft.options.getSoundSourceVolume(SoundSource.MUSIC);
            float masterVolume = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
            double baseVolume = Math.max(0.0D, masterVolume * musicVolume);
            if (baseVolume <= 0.0001D) {
                gainControl.setValue(gainControl.getMinimum());
                return;
            }
            boolean nearJukebox = ClientJukeboxController.getInstance().isPlayerNearAnyActiveJukebox();
            if (nearJukebox) {
                jukeboxFadeVolume = Math.max(0.0D, jukeboxFadeVolume - FADE_OUT_STEP);
            } else {
                jukeboxFadeVolume = Math.min(1.0D, jukeboxFadeVolume + FADE_IN_STEP);
            }
            double finalVolume = baseVolume * jukeboxFadeVolume;
            if (finalVolume <= 0.0001D) {
                gainControl.setValue(gainControl.getMinimum());
                return;
            }
            float decibel = (float) (20.0D * Math.log10(finalVolume));
            decibel = Math.max(gainControl.getMinimum(), Math.min(0.0F, decibel));
            gainControl.setValue(decibel);
        }
    }
}
