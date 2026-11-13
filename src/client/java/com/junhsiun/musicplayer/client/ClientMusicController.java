package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.client.idle.AudioAnalyzer;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import com.junhsiun.musicplayer.network.MusicPlaybackReportPayload;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import com.junhsiun.musicplayer.util.MediaFormatUtil;
import com.junhsiun.musicplayer.util.Messages;
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
    /** 单曲音频下载大小上限（字节），超出视为异常源。 */
    private static final long MAX_AUDIO_BYTES = 50L * 1024 * 1024;
    /** 网络背景音乐（BGM）播放结果回调，全部在播放线程触发。 */
    public interface BgmListener {
        void onBgmEnded();

        void onBgmFailed(String message);
    }

    private static final ClientMusicController INSTANCE = new ClientMusicController();

    private final BackgroundMusicAudioDevice audioDevice = new BackgroundMusicAudioDevice();
    private volatile Player player;
    private volatile Thread playbackThread;
    private volatile String currentTrackId = "";
    private volatile boolean backgroundMusicPlaying;
    private volatile boolean bgmPlaying;
    /** 最近一次「带原因的停止」时刻：用户暂停 / 停止 / 退出播放、关掉背景音乐等。 */
    private volatile long explicitStopMillis;
    private volatile BgmListener bgmListener;
    private long lastPositionReportTime;
    /** 当前曲目的展示信息（队列播放与网络 BGM 共用），供纯享模式等界面读取。 */
    private volatile String currentTitle = "";
    private volatile String currentArtist = "";
    private volatile String currentCoverUrl = "";
    private volatile long currentDurationMillis;

    private ClientMusicController() {
    }

    public static ClientMusicController getInstance() {
        return INSTANCE;
    }

    public boolean isBackgroundMusicPlaying() {
        return backgroundMusicPlaying;
    }

    /** 网络背景音乐是否正在播放（与队列播放标志相互独立）。 */
    public boolean isBgmPlaying() {
        return bgmPlaying;
    }

    /** 当前曲目 ID（队列播放与网络 BGM 共用），用于取歌词。 */
    public String currentTrackId() {
        return currentTrackId;
    }

    /** 当前曲目标题。 */
    public String currentTitle() {
        return currentTitle;
    }

    /** 当前曲目艺术家 / 副标题。 */
    public String currentArtist() {
        return currentArtist;
    }

    /** 当前曲目封面地址；未知时为空串。 */
    public String currentCoverUrl() {
        return currentCoverUrl;
    }

    /** 当前曲目时长（毫秒）；未知为 0。 */
    public long currentDurationMillis() {
        return currentDurationMillis;
    }

    /**
     * 最近一次「显式停止」的时刻（{@code Util.getMillis()} 时间轴）。
     *
     * <p>换曲时的内部 {@code stop(null)} 不算显式停止：它只是把上一首收掉，
     * 紧接着就会播放下一首。只有玩家暂停 / 停止 / 退出、服务端主动停止、
     * 关闭网络背景音乐这类带原因的停止才更新。挂机纯享用它区分「换曲空档」与「真的停了」。</p>
     */
    public long lastExplicitStopMillis() {
        return explicitStopMillis;
    }

    /** 当前播放进度（毫秒）：直接来自声卡位置，未在播放返回 0。 */
    public long currentElapsedMillis() {
        if (!backgroundMusicPlaying && !bgmPlaying) {
            return 0L;
        }
        try {
            int position = audioDevice.getPosition();
            return position <= 0 ? 0L : position;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    public void tick() {
        if (!backgroundMusicPlaying || currentTrackId.isBlank() || player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastPositionReportTime < 2000) return;
        lastPositionReportTime = now;
        try {
            int pos = audioDevice.getPosition();
            if (pos > 0) {
                ClientPlayNetworking.send(MusicPlaybackReportPayload.position(pos, currentTrackId));
            }
        } catch (Exception ignored) {
        }
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
        AudioAnalyzer.reset();
        currentTrackId = "";
        currentTitle = "";
        currentArtist = "";
        currentCoverUrl = "";
        currentDurationMillis = 0L;
        backgroundMusicPlaying = false;
        bgmPlaying = false;
        bgmListener = null;
        lastPositionReportTime = 0L;
        if (reason != null && !reason.isBlank()) {
            // 只有带原因的停止才算「真的停了」，供纯享模式区分换曲空档。
            explicitStopMillis = net.minecraft.util.Util.getMillis();
            MusicPlayerMod.LOGGER.info("客户端已停止播放: {}", reason);
        }
    }

    private synchronized void play(String trackId, List<String> urls, String title, String subtitle, long offsetMillis) {
        stop(null);
        currentTrackId = trackId == null ? "" : trackId;
        currentTitle = title == null ? "" : title;
        currentArtist = subtitle == null ? "" : subtitle;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getMusicManager() != null) {
            minecraft.getMusicManager().stopPlaying();
        }
        backgroundMusicPlaying = true;
        audioDevice.setSeekPosition(offsetMillis);
        playbackThread = new Thread(() -> playWithFallback(urls, currentTrackId, title, subtitle, false, null), "musicplayer-client-playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    /**
     * 静默播放网络背景音乐：不上报服务端、不触发歌词与提示，
     * 播放结束/失败通过 {@code listener} 回调给 BGM 控制器。
     */
    public synchronized void playBgm(String trackId, List<String> urls, String title, String subtitle,
                                     String coverUrl, long durationMillis, BgmListener listener) {
        stop(null);
        currentTrackId = trackId == null ? "" : trackId;
        currentTitle = title == null ? "" : title;
        currentArtist = subtitle == null ? "" : subtitle;
        currentCoverUrl = coverUrl == null ? "" : coverUrl;
        currentDurationMillis = Math.max(0L, durationMillis);
        bgmPlaying = true;
        bgmListener = listener;
        audioDevice.setSeekPosition(0L);
        playbackThread = new Thread(() -> playWithFallback(urls, currentTrackId, title, subtitle, true, listener), "musicplayer-client-bgm");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void playWithFallback(List<String> urls, String trackId, String title, String subtitle, boolean bgm, BgmListener listener) {
        if (urls == null || urls.isEmpty()) {
            MusicPlayerMod.LOGGER.warn("没有可用的播放源: {} - {}", title, subtitle);
            if (bgm) {
                if (listener != null) {
                    listener.onBgmFailed("musicplayer.playback.no_source");
                }
            } else {
                reportFailed(trackId, "musicplayer.playback.no_source");
            }
            return;
        }

        Exception lastException = null;
        for (int index = 0; index < urls.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            String url = urls.get(index);
            // JLayer 只能解码 MP3：跳过已知无损/其他格式的直链（如 FLAC），避免解码立刻失败
            if (!MediaFormatUtil.isJlayerPlayable(url)) {
                MusicPlayerMod.LOGGER.debug("跳过 JLayer 无法解码的播放源 {}/{}: {}", index + 1, urls.size(), Messages.sanitizeForLog(url));
                continue;
            }
            try {
                MusicPlayerMod.LOGGER.debug("开始尝试播放源 {}/{}: {} - {}", index + 1, urls.size(), title, Messages.sanitizeForLog(url));
                playSingle(url, title, subtitle, !bgm);
                if (!Thread.currentThread().isInterrupted()) {
                    if (bgm) {
                        if (listener != null) {
                            listener.onBgmEnded();
                        }
                    } else {
                        reportEnded(trackId);
                    }
                }
                return;
            } catch (Exception exception) {
                if (Thread.currentThread().isInterrupted() || exception instanceof InterruptedIOException) {
                    return;
                }
                lastException = exception;
                MusicPlayerMod.LOGGER.warn("播放源失败，尝试下一个源: {}", Messages.sanitizeForLog(url), exception);
            }
        }

        MusicPlayerMod.LOGGER.warn("所有播放源都不可用: {} - {}", title, subtitle, lastException);
        if (bgm) {
            if (listener != null) {
                listener.onBgmFailed(lastException == null ? "musicplayer.playback.all_sources_failed" : lastException.getMessage());
            }
        } else {
            reportFailed(trackId, lastException == null ? "musicplayer.playback.all_sources_failed" : lastException.getMessage());
        }
    }

    private void playSingle(String url, String title, String subtitle, boolean notifyStart) throws IOException, JavaLayerException {
        OkHttpClient client = HttpClientFactory.create();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MinecraftMusicPlayer/" + com.junhsiun.musicplayer.MusicPlayerMod.VERSION)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("musicplayer.playback.empty_response");
            }
            long contentLength = body.contentLength();
            if (contentLength > MAX_AUDIO_BYTES) {
                throw new IOException("musicplayer.playback.audio_too_large");
            }
            byte[] audioBytes = body.bytes();
            if (audioBytes.length == 0) {
                throw new IOException("musicplayer.playback.empty_audio");
            }
            if (audioBytes.length > MAX_AUDIO_BYTES) {
                throw new IOException("musicplayer.playback.audio_too_large");
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
                // 下载完成的这一刻才是"真正开始出声"：先告诉服务端，再真正播放，
                // 免得服务端从发出 play 包起就算时间，歌词比声音先跑。
                if (notifyStart) {
                    // 注意：这里不能动 audioDevice 的 seek 偏移——seek/resume 正是靠它跳转的
                    reportStarted(currentTrackId);
                }
                currentPlayer.play();
            }
        } finally {
            synchronized (this) {
                player = null;
                if (Thread.currentThread() == playbackThread) {
                    playbackThread = null;
                    backgroundMusicPlaying = false;
                    bgmPlaying = false;
                }
            }
        }
    }

    /** 告诉服务端"现在才真正开始出声"，服务端据此重新对齐歌词时间轴。 */
    private void reportStarted(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return;
        }
        ClientPlayNetworking.send(MusicPlaybackReportPayload.started(trackId));
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

        private volatile SourceDataLine sourceLine;
        private AudioFormat audioFormat;
        private byte[] byteBuffer;
        private FloatControl gainControl;
        private double jukeboxFadeVolume = 1.0D;
        private long seekPositionMillis;
        /** 本次播放的起始偏移（毫秒），进度上报要加回来。 */
        private long seekStartMillis;
        private long decodedMillis;

        private void setSeekPosition(long millis) {
            this.seekPositionMillis = millis;
            // 记录本次播放的起始偏移：getPosition 要把它算进去
            this.seekStartMillis = Math.max(0L, millis);
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
            // 声卡位置归零了，起始偏移也必须归零，否则下一次上报会带着上一条的偏移
            seekStartMillis = 0L;
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
                // 声道数兜底为 1：解码器正常只会给 1/2，但这里一旦为 0 就是除零，
                // 而这段跑在音频线程上，异常会让整条播放线程直接死掉。
                int channels = Math.max(1, audioFormat.getChannels());
                float frameMillis = (float) (len / channels) / sampleRate * 1000.0f;
                decodedMillis += (long) frameMillis;
                if (decodedMillis < seekPositionMillis) {
                    return;
                }
                seekPositionMillis = 0L;
            }
            AudioAnalyzer.feed(samples, offs, len,
                    Math.max(1, audioFormat.getChannels()), audioFormat.getSampleRate());
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
                        javax.sound.sampled.Control ctrl = sourceLine.getControl(FloatControl.Type.MASTER_GAIN);
                        if (ctrl instanceof FloatControl fc) gainControl = fc;
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
        /**
         * 当前播放位置（毫秒）。
         *
         * <p>{@code sourceLine} 的位置只统计<b>真正写进声卡</b>的音频；seek / resume 时
         * 起始那一段是被解码后直接丢弃的（没有 write），声卡位置自然从 0 开始。
         * 所以必须把 seek 起点加回来，否则歌词会比声音慢一整段。
         */
        public int getPosition() {
            long played = sourceLine == null ? 0L : sourceLine.getMicrosecondPosition() / 1000L;
            return (int) (seekStartMillis + played);
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
            float musicVolume;
            if (getInstance().isBgmPlaying()) {
                musicVolume = minecraft.options.getSoundSourceVolume(SoundSource.MUSIC);
            } else {
                musicVolume = minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get().floatValue();
            }
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
