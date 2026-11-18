package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.mixin.client.ClientLevelAccessor;
import com.junhsiun.musicplayer.mixin.client.LevelEventHandlerAccessor;
import com.junhsiun.musicplayer.network.JukeboxMusicPayload;
import com.junhsiun.musicplayer.network.MusicPlaybackReportPayload;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import com.junhsiun.musicplayer.util.Messages;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** 单曲音频下载大小上限（字节）。 */
    private static final long MAX_AUDIO_BYTES = 50L * 1024 * 1024;

    private final Map<Long, PlaybackHandle> playbackHandles = new ConcurrentHashMap<>();
    private final Map<Long, Long> pendingInsertions = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastPositionReportTime = new ConcurrentHashMap<>();

    private ClientJukeboxController() {
    }

    public static ClientJukeboxController getInstance() {
        return INSTANCE;
    }

    public void handle(JukeboxMusicPayload payload) {
        MusicPlayerMod.LOGGER.debug("Received custom jukebox payload: action={}, pos={}", payload.action(), BlockPos.of(payload.jukeboxPos()));
        switch (payload.action()) {
            case "play" -> {
                play(payload.jukeboxPos(), payload.urls(), payload.title(), payload.subtitle(), payload.coverUrl(), payload.offsetMillis());
                setTrackId(payload.jukeboxPos(), payload.trackId());
            }
            case "refresh" -> {
                refresh(payload.jukeboxPos(), payload.urls(), payload.title(), payload.subtitle(), payload.coverUrl(), payload.offsetMillis());
                setTrackId(payload.jukeboxPos(), payload.trackId());
            }
            case "update" -> {
                update(payload.jukeboxPos(), payload.title(), payload.subtitle(), payload.coverUrl());
                setTrackId(payload.jukeboxPos(), payload.trackId());
            }
            case "stop" -> stop(payload.jukeboxPos());
            default -> MusicPlayerMod.LOGGER.warn("Unknown jukebox action: {}", payload.action());
        }
    }

    private void setTrackId(long jukeboxPos, String trackId) {
        PlaybackHandle handle = playbackHandles.get(jukeboxPos);
        if (handle != null && trackId != null) {
            handle.trackId = trackId;
        }
    }

    public void stopAll(String reason) {
        for (Long key : List.copyOf(playbackHandles.keySet())) {
            stop(key);
        }
        pendingInsertions.clear();
        lastPositionReportTime.clear();
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

        long reportInterval = 2000L;
        for (Long jukeboxPos : List.copyOf(playbackHandles.keySet())) {
            PlaybackHandle handle = playbackHandles.get(jukeboxPos);
            if (handle == null) continue;
            if (handle.finishedAtMillis > 0L) {
                if (!handle.endReported && !handle.trackId.isBlank()) {
                    handle.endReported = true;
                    ClientPlayNetworking.send(MusicPlaybackReportPayload.jukeboxEnded(handle.trackId));
                }
                continue;
            }
            silenceVanillaJukeboxSound(jukeboxPos, "tick suppression");

            if (handle.player != null && !handle.trackId.isBlank()) {
                Long lastReport = lastPositionReportTime.get(jukeboxPos);
                if (lastReport == null || now - lastReport >= reportInterval) {
                    lastPositionReportTime.put(jukeboxPos, now);
                    try {
                        int pos = handle.player.getPosition();
                        if (pos > 0) {
                            ClientPlayNetworking.send(MusicPlaybackReportPayload.position(pos, handle.trackId));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            String invalidReason = getInvalidStateReason(client.level, jukeboxPos);
            if (invalidReason == null) {
                continue;
            }
            MusicPlayerMod.LOGGER.debug("Stopping custom jukebox playback locally because jukebox state is invalid at {}: {}", BlockPos.of(jukeboxPos), invalidReason);
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
        MusicPlayerMod.LOGGER.debug("Marked custom jukebox insertion pending at {}", pos);
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
        // 每次都换一个新的 handle：旧线程即使在下载/建流阶段被 stop，也只会看到旧的
        // closed 标记然后退出，不会把新一次播放的 player 字段清掉，也不会在 stop 之后
        // 才把音频流建起来（那种线程不在 playbackHandles 里，退世界也停不掉）。
        PlaybackHandle previous = playbackHandles.remove(jukeboxPos);
        if (previous != null) {
            stopPlaybackOnly(previous);
        }
        PlaybackHandle handle = new PlaybackHandle();
        playbackHandles.put(jukeboxPos, handle);
        handle.finishedAtMillis = 0L;
        handle.endReported = false;
        handle.urls = urls == null ? List.of() : List.copyOf(urls);
        handle.title = title == null ? "" : title;
        handle.subtitle = subtitle == null ? "" : subtitle;
        handle.coverUrl = coverUrl == null ? "" : coverUrl;
        handle.startedAtMillis = System.currentTimeMillis();
        CoverArtTextureCache.getInstance().request(handle.coverUrl);
        if (handle.coverUrl.isBlank()) {
            // 空封面会让唱片只剩黑色盘面。记一行日志，方便区分"没下发网址"和"下载失败"
            // （下载失败由 CoverArtTextureCache 自己打 WARN）
            MusicPlayerMod.LOGGER.info("Jukebox {} is playing without a cover url: {} - {}",
                    jukeboxPos, handle.title, handle.subtitle);
        }
        silenceVanillaJukeboxSound(jukeboxPos, "custom play payload");
        showNowPlaying(handle.title, handle.subtitle);
        stopVanillaBackgroundMusic();
        if (handle.urls.isEmpty()) {
            // 没有直链时先不启动线程：服务端解析完会推 refresh，等那时再开始，
            // 避免线程打一行 warn 就退出、正好和 refresh 错开，最后一直没声音。
            MusicPlayerMod.LOGGER.debug("Jukebox {} playback waits for resolved urls: {} - {}",
                    jukeboxPos, handle.title, handle.subtitle);
            return;
        }
        long startOffset = offsetMillis;
        Thread playbackThread = new Thread(() -> playWithFallback(jukeboxPos, handle, title, subtitle, startOffset), "musicplayer-jukebox-" + jukeboxPos);
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
        boolean urlsChanged = false;
        if (urls != null && !urls.isEmpty()) {
            List<String> refreshed = List.copyOf(urls);
            urlsChanged = !refreshed.equals(handle.urls);
            handle.urls = refreshed;
        }
        if (title != null && !title.isBlank()) {
            handle.title = title;
        }
        if (subtitle != null && !subtitle.isBlank()) {
            handle.subtitle = subtitle;
        }
        // 封面只要<b>变了</b>就更新。早先写成"仅在当前封面为空时才采纳"，
        // 于是唱片机播放列表自动切歌时（服务端推 refresh 而不是 play），
        // 唱片会一直显示上一首的封面，或者在没有封面时一直空着。
        if (coverUrl != null && !coverUrl.isBlank() && !coverUrl.equals(handle.coverUrl)) {
            handle.coverUrl = coverUrl;
            CoverArtTextureCache.getInstance().request(coverUrl);
        }
        silenceVanillaJukeboxSound(jukeboxPos, "custom refresh payload");
        stopVanillaBackgroundMusic();
        if (urlsChanged && handle.player == null) {
            // 新直链到达时还没有真正出声（旧链接下载失败、或一直在等解析）：
            // 用新链接重开一次。旧线程持有的是旧 handle，会看到 closed 标记后退出。
            play(jukeboxPos, handle.urls, handle.title, handle.subtitle, handle.coverUrl, offsetMillis);
            return;
        }
        Thread thread = handle.thread;
        if (thread == null || !thread.isAlive()) {
            long startOffset = offsetMillis;
            Thread playbackThread = new Thread(
                    () -> playWithFallback(jukeboxPos, handle, handle.title, handle.subtitle, startOffset),
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
        // 先置位再关流：播放线程下载完成后会先检查 closed，保证不会在 stop 之后
        // 又建出一条没人持有、退世界也停不掉的音频流。
        handle.closed = true;
        Player player = handle.player;
        handle.player = null;
        if (player != null) {
            player.close();
        }
        Call call = handle.currentCall;
        handle.currentCall = null;
        if (call != null) {
            call.cancel();
        }
        Thread thread = handle.thread;
        handle.thread = null;
        if (thread != null) {
            thread.interrupt();
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

    /**
     * 按 handle 里当前的直链列表依次尝试播放。
     *
     * <p>不把 urls 当参数固定下来：服务端解析出新的直链后会推 refresh，如果这时旧链接
     * 还在 403/超时里打转，原来的实现会一直用启动时那份旧列表，全部失败后线程退出，
     * 而 refresh 又因为线程还活着没有重启——表现就是唱片已经在播（有歌词），客户端却
     * 始终没有声音。这里每轮都重新读取 {@link PlaybackHandle#urls}，刷新到新链接后
     * 继续尝试。</p>
     */
    private void playWithFallback(long jukeboxPos, PlaybackHandle handle, String title, String subtitle, long startOffset) {
        Set<String> failedUrls = new HashSet<>();
        boolean warnedEmpty = false;
        while (true) {
            if (handle.closed || Thread.currentThread().isInterrupted()) {
                clearThreadState(handle);
                return;
            }
            List<String> urls = handle.urls;
            if (urls.isEmpty()) {
                if (!warnedEmpty) {
                    warnedEmpty = true;
                    MusicPlayerMod.LOGGER.warn("Jukebox {} has no playable urls: {} - {}", jukeboxPos, title, subtitle);
                }
                clearThreadState(handle);
                return;
            }
            boolean urlsRefreshed = false;
            for (String url : urls) {
                if (failedUrls.contains(url)) {
                    continue;
                }
                if (handle.closed || Thread.currentThread().isInterrupted()) {
                    clearThreadState(handle);
                    return;
                }
                try {
                    playSingle(jukeboxPos, handle, url, title, subtitle, startOffset);
                    if (handle.closed) {
                        // 播放期间唱片被取走：已经在 stop 里结束，不要再上报"播放结束"，
                        // 否则服务端会按歌单规则切到下一张。
                        clearThreadState(handle);
                        return;
                    }
                    handle.finishedAtMillis = completionTime(handle.startedAtMillis);
                    clearThreadState(handle);
                    return;
                } catch (Exception exception) {
                    if (handle.closed || Thread.currentThread().isInterrupted() || exception instanceof InterruptedIOException) {
                        clearThreadState(handle);
                        return;
                    }
                    failedUrls.add(url);
                    logSourceFallback(url, exception);
                    if (!urls.equals(handle.urls)) {
                        // 失败期间收到 refresh：换用新直链，已失败的地址不再重试。
                        urlsRefreshed = true;
                        break;
                    }
                }
            }
            if (urlsRefreshed) {
                continue;
            }
            clearThreadState(handle);
            return;
        }
    }

    private static void clearThreadState(PlaybackHandle handle) {
        handle.thread = null;
        handle.player = null;
        handle.currentCall = null;
    }

    private void playSingle(long jukeboxPos, PlaybackHandle handle, String url, String title, String subtitle, long startOffset) throws IOException, JavaLayerException {
        OkHttpClient client = HttpClientFactory.create();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MinecraftMusicPlayer/" + com.junhsiun.musicplayer.MusicPlayerMod.VERSION)
                .get()
                .build();

        Call call = client.newCall(request);
        handle.currentCall = call;
        Player currentPlayer = null;
        try (Response response = call.execute()) {
            if (handle.closed) {
                throw new InterruptedIOException("jukebox playback cancelled");
            }
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body");
            }
            long contentLength = body.contentLength();
            if (contentLength > MAX_AUDIO_BYTES) {
                throw new IOException("Audio too large: " + contentLength);
            }
            byte[] audioBytes = body.bytes();
            if (audioBytes.length == 0) {
                throw new IOException("Empty audio data");
            }
            if (audioBytes.length > MAX_AUDIO_BYTES) {
                throw new IOException("Audio too large: " + audioBytes.length);
            }
            if (handle.closed) {
                throw new InterruptedIOException("jukebox playback cancelled");
            }
            try (BufferedInputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(audioBytes))) {
                SpatialAudioDevice audioDevice = new SpatialAudioDevice(jukeboxPos);
                audioDevice.setSeekPosition(startOffset);
                currentPlayer = new Player(inputStream, audioDevice);
                // 下载期间可能已经被 stop：此时绝不能把音频流建起来，
                // 否则这条流不在 playbackHandles 里，退出存档也停不掉。
                if (handle.closed) {
                    throw new InterruptedIOException("jukebox playback cancelled");
                }
                handle.player = currentPlayer;
                if (handle.closed) {
                    throw new InterruptedIOException("jukebox playback cancelled");
                }
                MusicPlayerMod.LOGGER.info("Start jukebox playback: {} - {}", title, subtitle);
                currentPlayer.play();
            }
        } finally {
            handle.currentCall = null;
            if (handle.player == currentPlayer) {
                handle.player = null;
            }
            if (currentPlayer != null) {
                currentPlayer.close();
            }
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
        /** 已被 stop：播放线程在下载/建流的各个阶段都要检查，避免 stop 后冒出孤儿音频流。 */
        private volatile boolean closed;
        private volatile List<String> urls = List.of();
        private volatile String title = "";
        private volatile String subtitle = "";
        private volatile String coverUrl = "";
        private volatile String trackId = "";
        private volatile long startedAtMillis;
        private volatile long finishedAtMillis;
        private volatile boolean endReported;
    }

    public record JukeboxVisualState(BlockPos pos, String coverUrl, long startedAtMillis, boolean finished, long finishedAtMillis) {
    }

    private void logSourceFallback(String url, Exception exception) {
        String message = exception.getMessage();
        if (message != null && (message.contains("HTTP 403") || message.contains("HTTP 404"))) {
            MusicPlayerMod.LOGGER.debug("Jukebox source rejected, trying next source: {}", Messages.sanitizeForLog(url));
            return;
        }
        if (exception instanceof SocketTimeoutException) {
            MusicPlayerMod.LOGGER.debug("Jukebox source timed out, trying next source: {}", Messages.sanitizeForLog(url));
            return;
        }
        MusicPlayerMod.LOGGER.warn("Jukebox source failed, trying next source: {}", Messages.sanitizeForLog(url), exception);
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
        // 不在这里判断 HAS_RECORD / 方块实体内容：唱片是服务端通过 play/stop 载荷
        // 授权的，而方块状态（尤其是自动化写入、播放列表换碟）到客户端的时间可能比
        // play 载荷晚。曾经因为这里读到旧的 HAS_RECORD=false，刚插入的唱片被立刻
        // 判成"已取出"，表现为封面闪一下收回、没有声音，只剩服务端下发的歌词。
        // 取出唱片时服务端会主动发 stop，服务端 tick 也会兜底，客户端不需要抢先判断。
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
        MusicPlayerMod.LOGGER.debug("Stopped vanilla jukebox sound at {}: {}", pos, reason);
    }

    private static final class SpatialAudioDevice extends AudioDeviceBase {
        private final BlockPos jukeboxPos;
        private volatile SourceDataLine sourceLine;
        private AudioFormat audioFormat;
        private byte[] byteBuffer;
        private FloatControl gainControl;
        private long baseSeekMillis;
        private long seekPositionMillis;
        private long decodedMillis;

        private SpatialAudioDevice(long jukeboxPos) {
            this.jukeboxPos = BlockPos.of(jukeboxPos);
        }

        private void setSeekPosition(long millis) {
            this.baseSeekMillis = millis;
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
                        javax.sound.sampled.Control ctrl = sourceLine.getControl(FloatControl.Type.MASTER_GAIN);
                        if (ctrl instanceof FloatControl fc) gainControl = fc;
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
            if (seekPositionMillis > 0L) {
                return (int) baseSeekMillis;
            }
            return (int) (baseSeekMillis + (sourceLine != null ? sourceLine.getMicrosecondPosition() / 1000L : 0L));
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
