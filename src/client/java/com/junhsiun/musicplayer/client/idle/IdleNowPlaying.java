package com.junhsiun.musicplayer.client.idle;

import com.junhsiun.musicplayer.client.ClientMusicController;
import com.junhsiun.musicplayer.client.ClientMusicUiState;
import net.minecraft.util.Util;

/**
 * 纯享模式读取「当前在放什么」的统一入口。
 *
 * <p>客户端有两种互相独立的播放来源：</p>
 * <ul>
 *   <li><b>队列播放</b>：歌曲由服务端下发，标题 / 艺术家 / 封面 / 时长都在
 *       {@link ClientMusicUiState} 的快照里，进度由收到快照的时刻推算；</li>
 *   <li><b>网络背景音乐</b>：完全由客户端挑选与播放，服务端快照里没有任何信息，
 *       只能从 {@link ClientMusicController} 的播放器元数据拿。</li>
 * </ul>
 *
 * <p>选择顺序：网络 BGM 优先（它一播放就说明抢占了背景音乐），否则只要服务端快照里
 * 有正在播放的曲目就用快照（信息最全，含封面与时长），最后才回退到播放器元数据。
 * 这样纯享画面在两种来源下都能显示正确的曲目信息，不会互相串台。</p>
 *
 * <p><b>切歌空档：</b>一首歌放完到下一首真正出声之间，客户端会短暂地「什么都没有」。
 * 如果此时直接返回空串，标题、歌词与进度条会在换曲瞬间闪一下。所以这里记住最后一次
 * 有效信息，在 {@link #HOLD_MILLIS} 之内继续沿用它，让画面平滑地跨过换曲。</p>
 */
final class IdleNowPlaying {

    /**
     * 换曲空档里继续显示上一首的最长时长。
     *
     * <p>与 {@link IdleController} 的换曲宽容保持一致：换曲空档里标题、封面与进度都停在
     * 上一首上，等新曲目真正开始出声后再整体换过去，避免中途闪一下空白。</p>
     */
    private static final long HOLD_MILLIS = 20_000L;

    /** 最近一次确实有音乐时读到的曲目信息。只在客户端主线程读写。 */
    private static Now hold;

    private IdleNowPlaying() {
    }

    /** 退出世界时清掉记忆，避免下一局刚开始就显示上一局的曲目。 */
    static void clear() {
        hold = null;
    }

    static String trackId() {
        return resolve().trackId();
    }

    static String title() {
        return resolve().title();
    }

    static String artist() {
        return resolve().artist();
    }

    static String coverUrl() {
        return resolve().coverUrl();
    }

    static long elapsedMillis() {
        return resolve().elapsedMillis();
    }

    static long durationMillis() {
        return resolve().durationMillis();
    }

    private static Now resolve() {
        ClientMusicController controller = ClientMusicController.getInstance();
        boolean playing = controller.isBackgroundMusicPlaying() || controller.isBgmPlaying();
        ClientMusicUiState ui = ClientMusicUiState.getInstance();
        // 队列换曲的空档里本地播放器已经停了，但服务端快照仍指向「正在播放」的曲目
        // （或已经换成下一首）。这同样算有内容，直接用快照，别继续显示上一首。
        boolean queueActive = ui.hasState() && ui.playing() && !ui.paused();
        Now live = fromSources(controller);
        if (playing || queueActive) {
            hold = live.capturedNow();
            return live;
        }
        Now cached = hold;
        if (cached != null && Util.getMillis() - cached.capturedAtMillis() < HOLD_MILLIS) {
            return cached;
        }
        return live;
    }

    private static Now fromSources(ClientMusicController controller) {
        boolean snapshot = useSnapshot();
        ClientMusicUiState ui = ClientMusicUiState.getInstance();

        String trackId = snapshot ? safe(ui.trackId()) : "";
        String title = snapshot ? safe(ui.title()) : "";
        String artist = snapshot ? safe(ui.artist()) : "";
        String cover = snapshot ? safe(ui.coverUrl()) : "";
        long elapsed = snapshot ? ui.elapsedMillis() : 0L;
        long duration = snapshot ? ui.durationMillis() : 0L;

        if (trackId.isBlank()) {
            trackId = safe(controller.currentTrackId());
        }
        if (title.isBlank()) {
            title = safe(controller.currentTitle());
        }
        if (artist.isBlank()) {
            artist = safe(controller.currentArtist());
        }
        if (cover.isBlank()) {
            cover = safe(controller.currentCoverUrl());
        }
        if (duration <= 0L) {
            duration = controller.currentDurationMillis();
        }
        // 歌词时间轴以本地声卡位置为准。
        //
        // 服务端快照的 elapsed 是从「发出 play」那一刻起算的墙钟时间，没有扣掉客户端
        // 下载整首音频、解码并丢弃到 seek 起点的那段静默期，直接拿来排歌词会一直快几秒。
        // 而声卡位置（getMicrosecondPosition + seek 起点）就是此刻真正在响的地方。
        // 只要本地确实在放同一首，就无条件覆盖；处在加载静默期时它返回 0，
        // 歌词自然停在第一句之前，也不会提前跑。
        boolean localPlaying = controller.isBackgroundMusicPlaying() || controller.isBgmPlaying();
        if (localPlaying && trackId.equals(safe(controller.currentTrackId()))) {
            elapsed = controller.currentElapsedMillis();
        } else if (elapsed <= 0L) {
            elapsed = controller.currentElapsedMillis();
        }
        return new Now(trackId, title, artist, cover,
                Math.max(0L, elapsed), Math.max(0L, duration), 0L);
    }

    /**
     * 服务端快照是否可用：网络 BGM 播放时不看快照，其余情况以快照里确实有曲目为准。
     *
     * <p>不看 {@code playing()} 是有意的：客户端播放器停下、服务端还没把下一首派下来时，
     * 快照仍然是“上一首在播放”，正好可以让标题与封面停在上一首上，而不是瞬间清空。</p>
     */
    private static boolean useSnapshot() {
        if (ClientMusicController.getInstance().isBgmPlaying()) {
            return false;
        }
        ClientMusicUiState ui = ClientMusicUiState.getInstance();
        return ui.hasState() && !safe(ui.trackId()).isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record Now(String trackId, String title, String artist, String coverUrl,
                       long elapsedMillis, long durationMillis, long capturedAtMillis) {

        Now capturedNow() {
            return new Now(trackId, title, artist, coverUrl, elapsedMillis, durationMillis,
                    Util.getMillis());
        }
    }
}
