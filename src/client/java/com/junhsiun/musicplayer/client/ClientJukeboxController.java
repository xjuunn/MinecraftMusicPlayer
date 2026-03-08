package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.network.JukeboxMusicPayload;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDeviceBase;
import javazoom.jl.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
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
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public final class ClientJukeboxController {
    private static final ClientJukeboxController INSTANCE = new ClientJukeboxController();
    private static final double AUDIBLE_RANGE = 64.0D;

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
                playSingle(jukeboxPos, handle, url, title, subtitle);
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

    private void playSingle(long jukeboxPos, PlaybackHandle handle, String url, String title, String subtitle) throws IOException, JavaLayerException {
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
                SpatialAudioDevice audioDevice = new SpatialAudioDevice(jukeboxPos);
                Player currentPlayer = new Player(inputStream, audioDevice);
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

    private static final class SpatialAudioDevice extends AudioDeviceBase {
        private final BlockPos jukeboxPos;
        private SourceDataLine sourceLine;
        private AudioFormat audioFormat;
        private byte[] byteBuffer;
        private FloatControl gainControl;

        private SpatialAudioDevice(long jukeboxPos) {
            this.jukeboxPos = BlockPos.of(jukeboxPos);
        }

        @Override
        protected void openImpl() throws JavaLayerException {
            audioFormat = new AudioFormat(getDecoder().getOutputFrequency(), 16, getDecoder().getOutputChannels(), true, false);
            try {
                sourceLine = AudioSystem.getSourceDataLine(audioFormat);
                sourceLine.open(audioFormat);
                if (sourceLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    gainControl = (FloatControl) sourceLine.getControl(FloatControl.Type.MASTER_GAIN);
                }
                sourceLine.start();
            } catch (LineUnavailableException exception) {
                throw new JavaLayerException("Unable to open jukebox audio line", exception);
            }
        }

        @Override
        protected void writeImpl(short[] samples, int offs, int len) throws JavaLayerException {
            if (sourceLine == null) {
                return;
            }
            updateVolume();
            byte[] buffer = getByteArray(len * 2);
            int index = 0;
            for (int i = offs; i < offs + len; i++) {
                short sample = samples[i];
                buffer[index++] = (byte) sample;
                buffer[index++] = (byte) (sample >>> 8);
            }
            sourceLine.write(buffer, 0, index);
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
            LocalPlayer player = minecraft.player;
            if (player == null) {
                return;
            }
            double dx = player.getX() - (jukeboxPos.getX() + 0.5D);
            double dy = player.getY() - (jukeboxPos.getY() + 0.5D);
            double dz = player.getZ() - (jukeboxPos.getZ() + 0.5D);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double normalized = 1.0D - Math.min(1.0D, distance / AUDIBLE_RANGE);
            normalized *= normalized;
            if (normalized <= 0.0001D) {
                gainControl.setValue(gainControl.getMinimum());
                return;
            }
            float decibel = (float) (20.0D * Math.log10(normalized));
            decibel = Math.max(gainControl.getMinimum(), Math.min(0.0F, decibel));
            gainControl.setValue(decibel);
        }
    }
}
