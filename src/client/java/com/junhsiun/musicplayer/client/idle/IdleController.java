package com.junhsiun.musicplayer.client.idle;

import com.junhsiun.musicplayer.client.ClientMusicController;
import com.junhsiun.musicplayer.client.ClientMusicUiState;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;

/**
 * 挂机「纯享」模式的状态机。
 *
 * <p>只在客户端 tick 里推进：连续 {@code idle.delaySeconds} 秒没有键鼠输入、
 * 玩家位置没有变化、且确实有音乐在播放时，进入纯享模式；任意输入、移动、
 * 打开界面或死亡都会退出。判定与渲染完全解耦，渲染层只读
 * {@link #isActive()} 与 {@link #fade(long)}。</p>
 *
 * <p><b>切歌不退出：</b>队列换曲时本地播放器会先停下，服务端解析、下发下一首，
 * 客户端下载完才重新出声。这期间服务端快照仍是「有曲目在播放」，据此认为音乐还在，
 * 纯享模式不会中断；网络背景音乐没有快照，改用 {@link #TRACK_SWITCH_MILLIS} 的时间窗兜住。
 * 只有真的停播（服务端广播结束、显式停止、窗口超时）才淡出。</p>
 *
 * <p>退出分两种：音乐真的停了、按键、移动走正常淡出；打开界面、死亡、鼠标释放
 * 这类必须立刻把画面交还原版界面的情况直接硬切，不留半透明度。</p>
 *
 * <p>设置页的「预览」会请求一次短时展示：不要求总开关已打开、也不要求有音乐在播放，
 * 等界面关闭后展示 {@link #PREVIEW_MILLIS} 毫秒，任意输入或移动都会提前结束。</p>
 *
 * <p>本模式只隐藏 HUD、叠加视觉特效，<b>不会</b>改变游戏行为、不会模拟输入，
 * 与防挂机踢出无关。</p>
 */
public final class IdleController {

    /** 淡入时长。 */
    private static final long FADE_IN_MILLIS = 1400L;
    /** 淡出时长。 */
    private static final long FADE_OUT_MILLIS = 700L;
    /** 位置变化判定阈值（格），越小越容易被脚步声/载具晃动画打断。 */
    private static final double MOVE_EPSILON_SQ = 1.0E-4D;
    /** 设置页「预览」触发的一次展示时长。 */
    private static final long PREVIEW_MILLIS = 10_000L;
    /**
     * 预览开始后短暂忽略输入的时长。
     *
     * <p>点击预览按钮会先关界面：关闭动画结束时鼠标刚抓回，常会带出一两个位移事件，
     * 不忽略的话预览会在第一帧就被自己关掉。</p>
     */
    private static final long PREVIEW_INPUT_GRACE_MILLIS = 600L;
    /**
     * 预览请求的等待上限。
     *
     * <p>正常点击后界面会在几百毫秒内关完、随后开始预览；超过这个时长还没等到
     * 可用的游戏环境（例如玩家点了预览但界面卡住、人已退出世界），就丢弃这次请求，
     * 免得之后回到世界里时莫名弹出特效。</p>
     */
    private static final long PREVIEW_REQUEST_TIMEOUT_MILLIS = 5_000L;

    /**
     * 换曲宽容时长。
     *
     * <p>只用于<b>网络背景音乐</b>：它换曲时既没有本地播放标志，也没有服务端快照，
     * 只能靠「刚才确实有音乐」这个时间点兜住。队列曲目换曲由服务端快照直接判定，
     * 不会用到这个时长。</p>
     */
    private static final long TRACK_SWITCH_MILLIS = 20_000L;

    private static final IdleController INSTANCE = new IdleController();

    private volatile boolean active;
    private long lastActivityMillis = Util.getMillis();
    private long activatedAtMillis;
    private long deactivatedAtMillis;
    /** 最近一次确认「有音乐」的 tick 时刻；{@code 0} 表示本次会话还没有过音乐。 */
    private long lastPlaybackMillis;
    /** 最近一次确认有音乐时，播的是网络背景音乐（没有服务端快照可用）。 */
    private boolean lastLiveWasBgm;
    private boolean hasPosition;
    private double lastX;
    private double lastY;
    private double lastZ;
    /** 预览请求发出的时刻；{@code -1} 表示没有待处理请求。 */
    private long previewRequestedAtMillis = -1L;
    /** 预览的展示结束时刻；{@code 0} 表示没有待结束的预览。 */
    private long previewUntilMillis;
    /** 预览开始后忽略输入的截止时刻。 */
    private long previewGraceUntilMillis;
    /** 当前这次激活是否来自预览请求。 */
    private boolean previewShowing;
    /**
     * 预览退出后允许淡出的截止时刻。
     *
     * <p>预览不要求总开关打开，所以退出时不能直接用 {@code config.enabled} 决定是否继续渲染：
     * 那样淡出会被环境检查一刀切掉。这里单独给一段收尾窗口，结束即恢复完全按开关判断。</p>
     */
    private long previewCleanupUntilMillis;

    private IdleController() {
    }

    public static IdleController getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    /** 当前淡入淡出系数 0 - 1；淡出期间仍返回大于 0 的值，用于把最后一帧画完。 */
    public float fade(long nowMillis) {
        if (active) {
            float value = (nowMillis - activatedAtMillis) / (float) FADE_IN_MILLIS;
            return Math.max(0.0F, Math.min(1.0F, value));
        }
        float value = 1.0F - (nowMillis - deactivatedAtMillis) / (float) FADE_OUT_MILLIS;
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** 已经进入纯享模式的时长（毫秒）；不在模式中返回 0。 */
    public long activeMillis(long nowMillis) {
        return active ? Math.max(0L, nowMillis - activatedAtMillis) : 0L;
    }

    /**
     * 请求一次纯享预览。
     *
     * <p>设置页点击「预览」时调用。这里不立刻激活，而是等界面真正关闭、鼠标抓回后
     * 再强制进入纯享：渲染层在界面打开时会立刻收掉特效，提前激活只会被它取消。</p>
     *
     * <p>预览期间不要求有音乐在播放，展示 {@link #PREVIEW_MILLIS} 毫秒；
     * 任意输入、移动或再次打开界面都会退出。</p>
     */
    public void requestPreview() {
        previewRequestedAtMillis = Util.getMillis();
    }

    /**
     * 是否应该隐藏 HUD。
     *
     * <p>从特效开始出现的第一帧就隐藏，一直保持到淡出彻底结束。HUD 是由
     * {@code GuiRenderer} 在特效之后画的，如果等到淡入过半才切掉，前 0.7 秒
     * 会出现「HUD 浮在半透明特效上，然后突然消失」的突兀感；一开始就隐藏
     * 则是「HUD 退场、特效浮现」，看起来是有意为之。</p>
     *
     * <p>用 {@link #isActive()} 兜底：刚激活的那一帧 {@code fade} 可能还是 0，
     * 只判 {@code fade} 会让 HUD 多留一帧。</p>
     */
    public boolean shouldHideHud(long nowMillis) {
        return active || fade(nowMillis) > 0.02F;
    }

    /**
     * 原始输入回调：鼠标按键、滚轮、真实位移、任意按键。
     *
     * <p>由输入 Mixin 在方法开头调用，不做任何游戏逻辑修改。</p>
     */
    public void onRawInput() {
        long now = Util.getMillis();
        lastActivityMillis = now;
        // 刚进入预览时忽略关闭界面带出的鼠标余波，否则预览会瞬间结束。
        if (now < previewGraceUntilMillis) {
            return;
        }
        if (active) {
            deactivate();
        }
    }

    /**
     * 立刻收掉特效，不留淡出。渲染层在检测到界面 / 覆盖层打开时调用：
     * 单机暂停时客户端 tick 停摆，光靠 {@link #tick} 会在「暂停菜单」开着的整段时间里
     * 保持激活态，关掉菜单的第一帧特效就会整块弹回来。
     */
    public void cancelImmediate() {
        hardCut(Util.getMillis());
    }

    public void tick(Minecraft client) {
        long now = Util.getMillis();
        MusicPlayerConfig.IdleSettings config = MusicPlayerConfigManager.get().idle;

        // 预览请求：等界面关闭、鼠标抓回后再真正开始，否则会被渲染层的「界面打开」检查立刻收掉。
        if (previewRequestedAtMillis >= 0L) {
            if (canIdleEnvironment(client)) {
                previewRequestedAtMillis = -1L;
                previewUntilMillis = now + PREVIEW_MILLIS;
                previewGraceUntilMillis = now + PREVIEW_INPUT_GRACE_MILLIS;
                previewShowing = true;
                active = true;
                activatedAtMillis = now;
                lastActivityMillis = now;
            } else if (now - previewRequestedAtMillis > PREVIEW_REQUEST_TIMEOUT_MILLIS) {
                // 界面没能正常退出（或玩家直接离开了世界），丢弃这次请求。
                previewRequestedAtMillis = -1L;
            }
        }
        boolean previewing = previewUntilMillis > now;
        if (!previewing && previewUntilMillis > 0L) {
            // 预览时间到：和普通退出一样淡出，不要立刻消失。
            previewUntilMillis = 0L;
            if (previewShowing && active) {
                deactivate();
            }
        }

        // 预览不要求总开关打开：展示期与退出的淡出期都要放行，
        // 否则收尾的那几百毫秒会被环境检查硬切掉。
        boolean previewWindow = previewing || now < previewCleanupUntilMillis;
        boolean environmentOk = (config.enabled || previewWindow) && canIdleEnvironment(client);
        trackPosition(client, now, environmentOk);

        if (!environmentOk) {
            // 打开界面、死亡、鼠标没锁定、或者用户刚关掉开关：直接收掉，
            // 否则界面会被还没淡完的特效压花。
            hardCut(now);
            return;
        }
        boolean live = liveMusicPlaying();
        if (live) {
            lastPlaybackMillis = now;
            lastLiveWasBgm = ClientMusicController.getInstance().isBgmPlaying();
        }
        // 已经在纯享里时，允许在「换曲空档」内继续：队列换曲靠服务端快照判断队列还没结束，
        // 网络背景音乐没有快照，改用刚才确实有音乐的时间窗兜住。
        // 不在纯享里时不享受这个宽容，否则一首歌放完很久后再点下一首会一响就瞬间进纯享。
        boolean playing = live || previewing || (active && inTrackSwitch(now));
        // 提前拉取歌词：进入纯享的第一帧就应有歌词，而不是等它联网加载完。
        // 同一首歌重复调用是空操作，代价只有一次字符串比较；关掉歌词显示时不必发请求。
        if (config.showLyrics) {
            IdleLyrics.getInstance().update(IdleNowPlaying.trackId());
        }
        if (anyKeyHeld(client)) {
            lastActivityMillis = now;
            if (active && now >= previewGraceUntilMillis) {
                deactivate();
            }
            return;
        }
        if (!playing) {
            // 没有音乐就不该进入纯享：把正在显示的特效淡出，并刷新空闲计时。
            if (active) {
                deactivate();
            }
            lastActivityMillis = now;
            return;
        }
        if (!active) {
            int delayMillis = Math.max(MusicPlayerConfig.IdleSettings.MIN_DELAY_SECONDS,
                    config.delaySeconds) * 1000;
            if (now - lastActivityMillis >= delayMillis) {
                active = true;
                activatedAtMillis = now;
            }
        }
    }

    /** 退出世界 / 关闭客户端时彻底清空，避免下一局带着上一局的状态。 */
    public void reset() {
        active = false;
        hasPosition = false;
        lastPlaybackMillis = 0L;
        lastLiveWasBgm = false;
        lastActivityMillis = Util.getMillis();
        deactivatedAtMillis = 0L;
        activatedAtMillis = 0L;
        previewRequestedAtMillis = -1L;
        previewUntilMillis = 0L;
        previewGraceUntilMillis = 0L;
        previewCleanupUntilMillis = 0L;
        previewShowing = false;
        IdleLyrics.getInstance().clear();
        IdleNowPlaying.clear();
    }

    private void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        deactivatedAtMillis = Util.getMillis();
        lastActivityMillis = deactivatedAtMillis;
        previewUntilMillis = 0L;
        previewGraceUntilMillis = 0L;
        // 预览不依赖总开关：给它留出完整的淡出窗口，别让下一帧的环境检查硬切。
        previewCleanupUntilMillis = previewShowing ? deactivatedAtMillis + FADE_OUT_MILLIS : 0L;
        previewShowing = false;
    }

    /** 立刻结束，不留淡出：用于界面打开、死亡、鼠标释放这类必须马上让位给原版界面的情况。 */
    private void hardCut(long now) {
        active = false;
        activatedAtMillis = 0L;
        deactivatedAtMillis = 0L;
        lastActivityMillis = now;
        previewUntilMillis = 0L;
        previewGraceUntilMillis = 0L;
        previewCleanupUntilMillis = 0L;
        previewShowing = false;
    }

    private boolean canIdleEnvironment(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return false;
        }
        if (client.gui.screen() != null || client.gui.overlay() != null) {
            return false;
        }
        LocalPlayer player = client.player;
        if (player.isDeadOrDying() || !player.isAlive()) {
            return false;
        }
        if (client.mouseHandler == null || !client.mouseHandler.isMouseGrabbed()) {
            return false;
        }
        return true;
    }

    /**
     * 本地播放器（队列点播 / 网络背景音乐）此刻是否真的在出声。
     *
     * <p>这里只认播放器本身的状态，不看服务端快照：玩家退出播放（{@code /music leave}）、
     * 被静音或关掉背景音乐时，服务端快照可能还停留在「有曲目」，只有本地标志会立刻变 false。</p>
     */
    private static boolean liveMusicPlaying() {
        ClientMusicController controller = ClientMusicController.getInstance();
        return controller.isBackgroundMusicPlaying() || controller.isBgmPlaying();
    }

    /**
     * 是否正处于「上一首已结束、下一首还没接上」的换曲空档。
     *
     * <p>分两种来源判断：</p>
     * <ul>
     *   <li><b>队列曲目</b>：完全以服务端快照为准。换曲期间服务端只是还没把下一首派下来，
     *       快照仍是「有曲目在播放」；队列真的放完或用户停止时，服务端会立刻广播
     *       {@code playing=false}，此处随之返回 false，纯享立刻开始收尾。</li>
     *   <li><b>网络背景音乐</b>：没有任何快照，只能靠「距上一首结束还不到
     *       {@link #TRACK_SWITCH_MILLIS}」这个时间窗兜住。</li>
     * </ul>
     *
     * <p>若期间收到过显式停止（暂停、停止、退出播放、关掉网络背景音乐），说明是人为停下，
     * 不属于换曲，不享受宽容。</p>
     */
    private boolean inTrackSwitch(long now) {
        if (lastPlaybackMillis == 0L) {
            return false;
        }
        if (ClientMusicController.getInstance().lastExplicitStopMillis() > lastPlaybackMillis) {
            return false;
        }
        if (!lastLiveWasBgm) {
            return queueStillHasTrack();
        }
        return now - lastPlaybackMillis < TRACK_SWITCH_MILLIS;
    }

    /** 服务端快照是否仍指向一首「正在播放」的曲目。 */
    private static boolean queueStillHasTrack() {
        ClientMusicUiState ui = ClientMusicUiState.getInstance();
        return ui.hasState() && ui.playing() && !ui.paused();
    }

    private void trackPosition(Minecraft client, long now, boolean ready) {
        LocalPlayer player = client.player;
        if (player == null || !ready) {
            hasPosition = false;
            return;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        if (hasPosition) {
            double dx = x - lastX;
            double dy = y - lastY;
            double dz = z - lastZ;
            if (dx * dx + dy * dy + dz * dz > MOVE_EPSILON_SQ) {
                lastActivityMillis = now;
                // 与 onRawInput 一致：预览刚开始时忽略关闭界面带出的位置抖动。
                if (active && now >= previewGraceUntilMillis) {
                    deactivate();
                }
            }
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        hasPosition = true;
    }

    private static boolean anyKeyHeld(Minecraft client) {
        KeyMapping[] mappings = client.options.keyMappings;
        if (mappings == null) {
            return false;
        }
        for (KeyMapping mapping : mappings) {
            if (mapping != null && mapping.isDown()) {
                return true;
            }
        }
        return false;
    }
}
