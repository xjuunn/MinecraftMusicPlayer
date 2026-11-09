package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.model.QueueEntry;
import com.junhsiun.musicplayer.model.RecentTrack;
import com.junhsiun.musicplayer.network.MusicConfigSnapshotPayload;
import com.junhsiun.musicplayer.network.MusicQueuePayload;
import com.junhsiun.musicplayer.network.MusicRecentPayload;
import com.junhsiun.musicplayer.network.MusicUiStatePayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端侧的播放界面状态缓存。
 *
 * <p>服务端只发「某一刻的状态快照」，进度由客户端按真实时间自己推进，
 * 每两秒左右再被服务端校准一次。这样底部播放栏的进度条不会依赖高频网络包。</p>
 *
 * <p>本类<b>不落盘</b>：最近播放与配置都属于服务端，退出世界后清空，
 * 下次进入时重新从服务端同步。客户端歌单收藏另见 {@link ClientPlaylistStore}。</p>
 */
public final class ClientMusicUiState {

    private static final ClientMusicUiState INSTANCE = new ClientMusicUiState();

    private MusicUiStatePayload state;
    private long receivedAtMillis;
    private List<RecentTrack> recent = List.of();
    private List<QueueEntry> queue = List.of();
    private int queuePlaylistRemaining;
    private boolean queuePlaylistMode;
    private int queueRevision;
    private Map<String, String> config = new LinkedHashMap<>();
    private boolean canEditConfig;
    private boolean hasConfig;
    private int configRevision;
    private int recentRevision;

    private ClientMusicUiState() {
    }

    public static ClientMusicUiState getInstance() {
        return INSTANCE;
    }

    public void update(MusicUiStatePayload payload) {
        this.state = payload;
        this.receivedAtMillis = System.currentTimeMillis();
    }

    public void updateRecent(MusicRecentPayload payload) {
        this.recent = payload == null ? List.of() : payload.entries();
        this.recentRevision++;
    }

    public void updateQueue(MusicQueuePayload payload) {
        if (payload == null) {
            return;
        }
        this.queue = payload.entries();
        this.queuePlaylistRemaining = payload.playlistRemaining();
        this.queuePlaylistMode = payload.playlistMode();
        this.queueRevision++;
    }

    public void updateConfig(MusicConfigSnapshotPayload payload) {
        if (payload == null) {
            return;
        }
        this.config = new LinkedHashMap<>(payload.values());
        this.canEditConfig = payload.canEdit();
        this.hasConfig = true;
        this.configRevision++;
    }

    /** 本地乐观更新一个服务端配置值，等服务端快照回来后再覆盖。 */
    public void setConfigValue(String key, String value) {
        if (key == null) {
            return;
        }
        this.config.put(key, value == null ? "" : value);
    }

    public void clear() {
        state = null;
        receivedAtMillis = 0L;
        recent = List.of();
        queue = List.of();
        queuePlaylistRemaining = 0;
        queuePlaylistMode = false;
        queueRevision++;
        config = new LinkedHashMap<>();
        canEditConfig = false;
        hasConfig = false;
        configRevision++;
        recentRevision++;
    }

    public boolean hasState() {
        return state != null;
    }

    public boolean playing() {
        return state != null && state.playing();
    }

    public boolean paused() {
        return state != null && state.paused();
    }

    public String trackId() {
        return state == null ? "" : state.trackId();
    }

    public String title() {
        return state == null ? "" : state.title();
    }

    public String artist() {
        return state == null ? "" : state.artist();
    }

    public String coverUrl() {
        return state == null ? "" : state.coverUrl();
    }

    public String requester() {
        return state == null ? "" : state.requester();
    }

    public String playOrder() {
        return state == null ? "sequential" : state.playOrder();
    }

    public int queueSize() {
        return state == null ? 0 : state.queueSize();
    }

    public int playlistRemaining() {
        return state == null ? 0 : state.playlistRemaining();
    }

    public long durationMillis() {
        return state == null ? 0L : Math.max(0L, state.durationMillis());
    }

    /** 当前进度（毫秒）：快照 + 从收到快照起经过的真实时间。 */
    public long elapsedMillis() {
        if (state == null) {
            return 0L;
        }
        long elapsed = Math.max(0L, state.elapsedMillis());
        if (state.playing() && !state.paused()) {
            elapsed += Math.max(0L, System.currentTimeMillis() - receivedAtMillis);
        }
        long duration = durationMillis();
        if (duration > 0L) {
            elapsed = Math.min(elapsed, duration);
        }
        return elapsed;
    }

    public List<RecentTrack> recent() {
        return recent;
    }

    public List<QueueEntry> queue() {
        return queue;
    }

    public int queuePlaylistRemaining() {
        return queuePlaylistRemaining;
    }

    public boolean queuePlaylistMode() {
        return queuePlaylistMode;
    }

    public int queueRevision() {
        return queueRevision;
    }

    public boolean hasConfig() {
        return hasConfig;
    }

    public int configRevision() {
        return configRevision;
    }

    public int recentRevision() {
        return recentRevision;
    }

    public boolean canEditConfig() {
        return canEditConfig;
    }

    public String configValue(String key, String fallback) {
        String value = config.get(key);
        return value == null ? fallback : value;
    }

    public boolean configBoolean(String key, boolean fallback) {
        String value = config.get(key);
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value);
    }

    public int configInt(String key, int fallback) {
        try {
            return Integer.parseInt(config.getOrDefault(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public double configDouble(String key, double fallback) {
        try {
            return Double.parseDouble(config.getOrDefault(key, Double.toString(fallback)).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
