package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.mixin.client.ClientLevelAccessor;
import com.junhsiun.musicplayer.mixin.client.LevelEventHandlerAccessor;
import com.junhsiun.musicplayer.network.JukeboxMusicPayload;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDeviceBase;
import javazoom.jl.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import okhttp3.OkHttpClient;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.util.ArrayList;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Line;
import javax.sound.sampled.SourceDataLine;

public final class ClientJukeboxController {
    private static final ClientJukeboxController INSTANCE = new ClientJukeboxController();
    private static final double AUDIBLE_RANGE = 64.0D;
    private static final long PENDING_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5);

    private final Map<Long, PlaybackHandle> playbackHandles = new ConcurrentHashMap<>();
    private final Map<Long, Long> pendingInsertions = new ConcurrentHashMap<>();

    private ClientJukeboxController() {
    }

    public static ClientJukeboxController getInstance() {
        return INSTANCE;
    }

    public void handle(JukeboxMusicPayload payload) {
        MusicPlayerMod.LOGGER.info("Received custom jukebox payload: action={}, pos={}", payload.action(), BlockPos.of(payload.jukeboxPos()));
        switch (payload.action()) {
            case "play" -> play(payload.jukeboxPos(), payload.urls(), payload.title(), payload.subtitle(), payload.coverUrl(), payload.offsetMillis());
            case "refresh" -> refresh(payload.jukeboxPos(), payload.urls(), payload.title(), payload.subtitle(), payload.coverUrl(), payload.offsetMillis());
            case "update" -> update(payload.jukeboxPos(), payload.title(), payload.subtitle(), payload.coverUrl());
            case "stop" -> stop(payload.jukeboxPos());
            default -> MusicPlayerMod.LOGGER.warn("Unknown jukebox action: {}", payload.action());
        }
    }

    public void stopAll(String reason) {
        playbackHandles.keySet().forEach(this::stop);
        pendingInsertions.clear();
        if (reason != null && !reason.isBlank()) {
            MusicPlayerMod.LOGGER.info("Stopped all jukebox playback: {}", reason);
        }
    }

    public void tick(Minecraft client) {
        if (playbackHandles.isEmpty() && pendingInsertions.isEmpty()) {
            return;
        }
        if (client.level == null) {
            stopAll("Client level missing.");
            return;
        }

        long now = System.currentTimeMillis();
        pendingInsertions.entrySet().removeIf(entry -> now - entry.getValue() > PENDING_TIMEOUT_MILLIS);

        for (Long jukeboxPos : List.copyOf(playbackHandles.keySet())) {
            PlaybackHandle handle = playbackHandles.get(jukeboxPos);
            if (handle == null) continue;
            if (handle.finishedAtMillis > 0L) {
                continue;
            }
            silenceVanillaJukeboxSound(jukeboxPos, "tick suppression");
            String invalidReason = getInvalidStateReason(client.level, jukeboxPos);
            if (invalidReason == null) {
                continue;
            }
            MusicPlayerMod.LOGGER.info("Stopping custom jukebox playback locally because jukebox state is invalid at {}: {}", BlockPos.of(jukeboxPos), invalidReason);
            playbackHandles.remove(jukeboxPos, handle);
            stopPlaybackOnly(handle);
        }

        if (!playbackHandles.isEmpty() && isPlayerNearAnyActiveJukebox()) {
            if (client.getMusicManager() != null) {
                client.getMusicManager().stopPlaying();
            }
        }
    }

    public void markPendingInsertion(BlockPos pos) {
        pendingInsertions.put(pos.asLong(), System.currentTimeMillis());
        MusicPlayerMod.LOGGER.info("Marked custom jukebox insertion pending at {}", pos);
    }

    public boolean shouldSuppressVanillaSound(BlockPos pos) {
        long key = pos.asLong();
        return playbackHandles.containsKey(key) || pendingInsertions.containsKey(key);
    }

    public boolean isPlayerNearAnyActiveJukebox() {
        if (playbackHandles.isEmpty()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }
        for (Long key : playbackHandles.keySet()) {
            BlockPos pos = BlockPos.of(key);
            double dx = player.getX() - (pos.getX() + 0.5D);
            double dy = player.getY() - (pos.getY() + 0.5D);
            double dz = player.getZ() - (pos.getZ() + 0.5D);
            if (dx * dx + dy * dy + dz * dz <= AUDIBLE_RANGE * AUDIBLE_RANGE) {
                return true;
            }
        }
        return false;
    }

    public List<JukeboxVisualState> getVisualStates() {
        List<JukeboxVisualState> states = new ArrayList<>(playbackHandles.size());
        for (Map.Entry<Long, PlaybackHandle> entry : playbackHandles.entrySet()) {
            PlaybackHandle handle = entry.getValue();
            if (handle == null) continue;
            boolean finished = handle.finishedAtMillis > 0L;
            states.add(new JukeboxVisualState(BlockPos.of(entry.getKey()), handle.coverUrl, handle.startedAtMillis, finished, finished ? handle.finishedAtMillis : 0L));
        }
        return states;
    }

    private void play(long jukeboxPos, List<String> urls, String title, String subtitle, String coverUrl, long offsetMillis) {
        pendingInsertions.remove(jukeboxPos);
        PlaybackHandle handle = playbackHandles.computeIfAbsent(jukeboxPos, ignored -> new PlaybackHandle());
        stopPlaybackOnly(handle);
        handle.finishedAtMillis = 0L;
        handle.urls = urls == null ? List.of() : List.copyOf(urls);
        handle.title = title == null ? "" : title;
        handle.subtitle = subtitle == null ? "" : subtitle;
        handle.coverUrl = coverUrl == null ? "" : coverUrl;
        handle.startedAtMillis = System.currentTimeMillis();
        CoverArtTextureCache.getInstance().request(handle.coverUrl);
        silenceVanillaJukeboxSound(jukeboxPos, "custom play payload");
        showNowPlaying(handle.title, handle.subtitle);
        stopVanillaBackgroundMusic();
        long startOffset = offsetMillis;
        Thread playbackThread = new Thread(() -> playWithFallback(jukeboxPos, handle, urls, title, subtitle, startOffset), "musicplayer-jukebox-" + jukeboxPos);
        playbackThread.setDaemon(true);
        handle.thread = playbackThread;
        playbackThread.start();
    }

    private void refresh(long jukeboxPos, List<String> urls, String title, String subtitle, String coverUrl, long offsetMillis) {
        pendingInsertions.remove(jukeboxPos);
        PlaybackHandle handle = playbackHandles.get(jukeboxPos);
        if (handle == null) {
            play(jukeboxPos, urls, title, subtitle, coverUrl, offsetMillis);
            return;
        }
        if (urls != null && !urls.isEmpty()) {
            handle.urls = List.copyOf(urls);
        }
        if (title != null && !title.isBlank()) {
            handle.title = title;
        }
        if (subtitle != null && !subtitle.isBlank()) {
            handle.subtitle = subtitle;
        }
        if (coverUrl != null && !coverUrl.isBlank() && handle.coverUrl.isBlank()) {
            handle.coverUrl = coverUrl;
            CoverArtTextureCache.getInstance().request(coverUrl);
        }
        silenceVanillaJukeboxSound(jukeboxPos, "custom refresh payload");
        stopVanillaBackgroundMusic();
        Thread thread = handle.thread;
        if (thread == null || !thread.isAlive()) {
            long startOffset = offsetMillis;
            Thread playbackThread = new Thread(
                    () -> playWithFallback(jukeboxPos, handle, handle.urls, handle.title, handle.subtitle, startOffset),
                    "musicplayer-jukebox-" + jukeboxPos
            );
            playbackThread.setDaemon(true);
            handle.thread = playbackThread;
            playbackThread.start();
        }
    }

    private void stop(long jukeboxPos) {
        pendingInsertions.remove(jukeboxPos);
        PlaybackHandle handle = playbackHandles.remove(jukeboxPos);
        silenceVanillaJukeboxSound(jukeboxPos, "custom stop payload");
        if (handle == null) {
            return;
        }
        stopPlaybackOnly(handle);
    }

    private void stopPlaybackOnly(PlaybackHandle handle) {
        if (handle == null) {
            return;
        }
        if (handle.player != null) {
            handle.player.close();
            handle.player = null;
        }
        if (handle.currentCall != null) {
            handle.currentCall.cancel();
            handle.currentCall = null;
        }
        if (handle.thread != null) {
            handle.thread.interrupt();
            handle.thread = null;
        }
    }

    private void update(long jukeboxPos, String title, String subtitle, String coverUrl) {
        PlaybackHandle handle = playbackHandles.get(jukeboxPos);
        if (handle == null) {
            return;
        }
        if (coverUrl != null && !coverUrl.isBlank() && !coverUrl.equals(handle.coverUrl)) {
            handle.coverUrl = coverUrl;
            CoverArtTextureCache.getInstance().request(coverUrl);
        }
    }

    private void playWithFallback(long jukeboxPos, PlaybackHandle handle, List<String> urls, String title, String subtitle, long startOffset) {
        if (urls == null || urls.isEmpty()) {
            MusicPlayerMod.LOGGER.warn("Jukebox {} has no playable urls: {} - {}", jukeboxPos, title, subtitle);
            handle.thread = null;
            handle.player = null;
            return;
        }

        for (String url : urls) {
            if (Thread.currentThread().isInterrupted()) {
                handle.thread = null;
                handle.player = null;
                handle.currentCall = null;
                return;
            }
            try {
                playSingle(jukeboxPos, handle, url, title, subtitle, startOffset);
                handle.finishedAtMillis = completionTime(handle.startedAtMillis);
                handle.thread = null;
                handle.player = null;
                handle.currentCall = null;
                return;
            } catch (IOException | JavaLayerException exception) {
                if (Thread.currentThread().isInterrupted() || exception instanceof InterruptedIOException) {
                    handle.thread = null;
                    handle.player = null;
                    handle.currentCall = null;
                    return;
                }
                logSourceFallback(url, exception);
            }
        }
        handle.thread = null;
        handle.player = null;
        handle.currentCall = null;
    }

    private void playSingle(long jukeboxPos, PlaybackHandle handle, String url, String title, String subtitle, long startOffset) throws IOException, JavaLayerException {
        OkHttpClient client = HttpClientFactory.create();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MinecraftMusicPlayer/2.0")
                .get()
                .build();

        Call call = client.newCall(request);
        handle.currentCall = call;
        try (Response response = call.execute()) {
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
                audioDevice.setSeekPosition(startOffset);
                Player currentPlayer = new Player(inputStream, audioDevice);
                handle.player = currentPlayer;
                MusicPlayerMod.LOGGER.info("Start jukebox playback: {} - {}", title, subtitle);
                currentPlayer.play();
            }
        } finally {
            handle.currentCall = null;
            handle.player = null;
        }
    }

    private static long completionTime(long startedAtMillis) {
        long elapsed = System.currentTimeMillis() - startedAtMillis;
        float degrees = Math.max(0L, elapsed) / 1000.0F * 18.0F;
        float remaining = 360.0F - (degrees % 360.0F);
        if (remaining < 0.001F) remaining = 360.0F;
        return System.currentTimeMillis() + (long) (remaining / 18.0F * 1000.0F);
    }

    private static final class PlaybackHandle {
        private volatile Player player;
        private volatile Thread thread;
        private volatile Call currentCall;
        private volatile List<String> urls = List.of();
        private volatile String title = "";
        private volatile String subtitle = "";
        private volatile String coverUrl = "";
        private volatile long startedAtMillis;
        private volatile long finishedAtMillis;
    }

    public record JukeboxVisualState(BlockPos pos, String coverUrl, long startedAtMillis, boolean finished, long finishedAtMillis) {
    }

    private void logSourceFallback(String url, Exception exception) {
        String message = exception.getMessage();
        if (message != null && (message.contains("HTTP 403") || message.contains("HTTP 404"))) {
            MusicPlayerMod.LOGGER.info("Jukebox source rejected, trying next source: {}", url);
            return;
        }
        if (exception instanceof SocketTimeoutException) {
            MusicPlayerMod.LOGGER.info("Jukebox source timed out, trying next source: {}", url);
            return;
        }
        MusicPlayerMod.LOGGER.warn("Jukebox source failed, trying next source: {}", url, exception);
    }

    private void stopVanillaBackgroundMusic() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getMusicManager() != null) {
            minecraft.getMusicManager().stopPlaying();
        }
    }

    private void showNowPlaying(String title, String subtitle) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null || title == null || title.isBlank()) {
            return;
        }
        String text = subtitle == null || subtitle.isBlank() ? title : title + " - " + subtitle;
        minecraft.gui.hud.setNowPlaying(Component.literal(text));
    }

    private String getInvalidStateReason(ClientLevel level, long jukeboxPos) {
        BlockPos pos = BlockPos.of(jukeboxPos);
        if (!level.isLoaded(pos)) {
            return "chunk not loaded";
        }
        if (!(level.getBlockState(pos).getBlock() instanceof JukeboxBlock)) {
            return "block is no longer a jukebox";
        }
        if (!level.getBlockState(pos).getValue(JukeboxBlock.HAS_RECORD)) {
            return "jukebox no longer has a record";
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof JukeboxBlockEntity jukebox)) {
            return "missing jukebox block entity";
        }
        return null;
    }

    private void silenceVanillaJukeboxSound(long jukeboxPos, String reason) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !(level instanceof ClientLevelAccessor clientLevelAccessor)) {
            return;
        }

        LevelEventHandler levelEventHandler = clientLevelAccessor.musicplayer$getLevelEventHandler();
        if (!(levelEventHandler instanceof LevelEventHandlerAccessor accessor)) {
            return;
        }

        BlockPos pos = BlockPos.of(jukeboxPos);
        Map<BlockPos, SoundInstance> playingJukeboxSongs = accessor.musicplayer$getPlayingJukeboxSongs();
        SoundInstance soundInstance = playingJukeboxSongs.remove(pos);
        if (soundInstance == null) {
            return;
        }

        minecraft.getSoundManager().stop(soundInstance);
        MusicPlayerMod.LOGGER.info("Stopped vanilla jukebox sound at {}: {}", pos, reason);
    }

    private static final class SpatialAudioDevice extends AudioDeviceBase {
        private final BlockPos jukeboxPos;
        private SourceDataLine sourceLine;
        private AudioFormat audioFormat;
        private byte[] byteBuffer;
        private FloatControl gainControl;
        private long seekPositionMillis;
        private long decodedMillis;

        private SpatialAudioDevice(long jukeboxPos) {
            this.jukeboxPos = BlockPos.of(jukeboxPos);
        }

        private void setSeekPosition(long millis) {
            this.seekPositionMillis = millis;
            this.decodedMillis = 0L;
        }

        @Override
        protected void openImpl() throws JavaLayerException {
            // Delay opening until the first decoded frame is available.
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
            audioFormat = getAudioFormat();
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
            Throwable failure = null;
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
                failure = exception;
            }
            if (sourceLine == null) {
                throw new JavaLayerException("Unable to open jukebox audio line", failure);
            }
        }

        private AudioFormat getAudioFormat() {
            if (audioFormat == null) {
                audioFormat = new AudioFormat(
                        getDecoder().getOutputFrequency(),
                        16,
                        getDecoder().getOutputChannels(),
                        true,
                        false
                );
            }
            return audioFormat;
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
            float masterVolume = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
            float recordsVolume = minecraft.options.getSoundSourceVolume(SoundSource.RECORDS);
            double categoryVolume = Math.max(0.0D, masterVolume * recordsVolume);
            if (categoryVolume <= 0.0001D) {
                gainControl.setValue(gainControl.getMinimum());
                return;
            }
            double dx = player.getX() - (jukeboxPos.getX() + 0.5D);
            double dy = player.getY() - (jukeboxPos.getY() + 0.5D);
            double dz = player.getZ() - (jukeboxPos.getZ() + 0.5D);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double normalized = 1.0D - Math.min(1.0D, distance / AUDIBLE_RANGE);
            normalized *= normalized;
            double finalVolume = normalized * categoryVolume;
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
