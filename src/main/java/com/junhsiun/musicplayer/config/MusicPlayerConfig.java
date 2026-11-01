package com.junhsiun.musicplayer.config;

/**
 * 模组统一配置模型：<b>客户端与服务端共用一个文件</b>。
 *
 * <p>布局分三类，新增参数请放进对应的{@link Section}，不要继续往根上堆：</p>
 *
 * <pre>
 * {
 *   "neteaseBaseUrl": "...",     // 根：服务端 / 双端共用
 *   "client": { ... },           // 客户端本地设置
 *   "glass":  { ... }            // 液态玻璃外观
 * }
 * </pre>
 *
 * <p><b>为什么根上的服务端字段不搬进子节：</b>老版本配置就长这样，搬动会让已有用户
 * 的配置全部失效。新增参数一律分节，根上只保留历史字段。</p>
 *
 * <p><b>为什么能"共用一个文件"：</b>单人游戏的内置服务端与客户端在同一个 JVM、
 * 同一个类加载器里运行，因此共享同一个 {@code MusicPlayerConfig} 实例，不会出现
 * 两端各写各的、互相覆盖的情况。专用服务器没有客户端类，客户端分节保持默认值，
 * 读入写出都无害。</p>
 *
 * <p>所有分节都实现 {@link Section}：{@link Section#normalize()} 负责把越界/空值
 * 拉回合法范围，{@link Section#resetToDefaults()} 负责一键恢复默认。</p>
 */
public class MusicPlayerConfig {
    public static final String DEFAULT_NETEASE_BASE_URL = "https://mycelis.dpdns.org/";
    public static final String DEFAULT_BGM_PLAYLIST_ID = "18481322122";
    /** 默认取数模式：直连优先、失败回退后端。 */
    public static final String DEFAULT_API_MODE = "auto";

    // ── 根：服务端 / 双端共用（历史字段，保持原位）──────────────────────
    public String neteaseBaseUrl = DEFAULT_NETEASE_BASE_URL;
    /**
     * 取数模式（可选值 {@code auto} / {@code direct} / {@code proxy}）。
     *
     * <p>{@code auto} 直连优先、失败回退后端；{@code direct} 仅直连；{@code proxy} 仅走
     * {@link #neteaseBaseUrl} 后端。直连模式下本字段仅作为直连失败时的保底地址。</p>
     */
    public String apiMode = DEFAULT_API_MODE;
    /** 网络背景音乐歌单 ID，空字符串表示关闭。（仅服务端管理；音量与开关在 {@link ClientSettings}） */
    public String bgmPlaylistId = DEFAULT_BGM_PLAYLIST_ID;
    public String proxy = "";
    public boolean useSystemProxy = true;
    public boolean preferIpv4 = true;
    public boolean allowCustomServer = true;
    public boolean allowSongRequest = true;
    public boolean allowPlaylistRequest = true;
    public boolean autoAdvance = true;
    public boolean announceQueueChanges = true;
    public boolean showLoadingHints = true;
    /** 无玩家个人偏好时是否默认显示歌词。 */
    public boolean showLyrics = true;
    public int connectTimeoutSeconds = 10;
    public int readTimeoutSeconds = 20;
    public int searchLimit = 8;
    public int maxQueueSize = 40;
    /** 每个玩家在同一队列中最多可点播的歌曲数量。 */
    public int maxSongsPerPlayer = 5;
    public int playlistQueueLimit = 20;
    public int queueCacheSize = 3;
    public double voteSkipPercent = 0.6D;
    public boolean enableLootMusicDiscs = true;
    public double lootMusicDiscChance = 0.3D;
    public int lootMusicDiscCount = 1;

    // ── 分节 ────────────────────────────────────────────────────────────
    /** 客户端本地设置。 */
    public ClientSettings client = new ClientSettings();
    /** 液态玻璃外观。 */
    public GlassSettings glass = new GlassSettings();

    /** 可规范化 / 可重置的配置分节。新增分节请实现本接口。 */
    public interface Section {
        /** 把越界、空值拉回合法范围。加载配置后必定调用一次。 */
        void normalize();

        /** 恢复到出厂默认值。 */
        void resetToDefaults();
    }

    /** 客户端本地设置（音量、网络背景音乐开关等）。 */
    public static final class ClientSettings implements Section {
        public static final double DEFAULT_BGM_VOLUME = 0.7D;
        public static final boolean DEFAULT_ENABLE_NETWORK_BGM = true;

        /** 背景音乐音量（原版与网络背景音乐），0.0 - 1.0。 */
        public double bgmVolume = DEFAULT_BGM_VOLUME;
        /** 是否启用网络背景音乐；false 时背景音乐使用原版。 */
        public boolean enableNetworkBgm = DEFAULT_ENABLE_NETWORK_BGM;

        @Override
        public void normalize() {
            if (Double.isNaN(bgmVolume) || bgmVolume < 0.0D || bgmVolume > 1.0D) {
                bgmVolume = DEFAULT_BGM_VOLUME;
            }
        }

        @Override
        public void resetToDefaults() {
            bgmVolume = DEFAULT_BGM_VOLUME;
            enableNetworkBgm = DEFAULT_ENABLE_NETWORK_BGM;
        }
    }

    /**
     * 液态玻璃外观设置。
     *
     * <p>这 9 项<b>只作用于窗口材质</b>（{@code GlassStyle.WINDOW}）；按钮、滑块、开关、
     * 圆珠仍用各自材质里烘焙的值。早期调参层是全局静态覆盖、不分材质，把窗口调好的数值
     * 一并套到了小控件上，导致小控件位移相对自身尺寸失控。改成按材质取用后，
     * 该问题从结构上不再发生。</p>
     *
     * <p>这 6 项光学参数（{@code refraction} 等）走顶点色逐 draw 传入，<b>不参与
     * 管线定义</b>，所以拖动滑块不会触发着色器重编译。</p>
     */
    public static final class GlassSettings implements Section {
        public static final double DEFAULT_OPACITY = 0.0D;
        public static final double DEFAULT_BLUR = 0.20D;
        public static final boolean DEFAULT_SHADOW = false;
        /** 折射幅度（GUI 像素）。 */
        public static final float DEFAULT_REFRACTION = 30.0F;
        public static final float DEFAULT_DISPERSION = 0.65F;
        public static final float DEFAULT_CENTER_DISPERSION = 6.40F;
        /** a 位置（GUI 像素）。 */
        public static final float DEFAULT_A_DISTANCE = 5.0F;
        public static final float DEFAULT_CONTRA_RATIO = 0.62F;
        /** 中心缩小强度（GUI 像素）。 */
        public static final float DEFAULT_SHRINK = 7.34F;

        /** 背景不透明度，0 = 完全透明，1 = 最不透明。同时作用于窗口与全部控件。 */
        public double opacity = DEFAULT_OPACITY;
        /** 背景模糊度，0 = 完全无模糊（只剩折射畸变），1 = 最模糊。 */
        public double blur = DEFAULT_BLUR;
        /** 是否绘制窗口的底部投影。 */
        public boolean shadow = DEFAULT_SHADOW;

        /** 边缘畸变幅度。 */
        public float refraction = DEFAULT_REFRACTION;
        /** 边缘色散强度。 */
        public float dispersion = DEFAULT_DISPERSION;
        /** 面板内横向色差强度。 */
        public float centerDispersion = DEFAULT_CENTER_DISPERSION;
        /** a 位置：边框 → a 是反向段。 */
        public float aDistance = DEFAULT_A_DISTANCE;
        /** 反向畸变相对正向的倍率。 */
        public float contraRatio = DEFAULT_CONTRA_RATIO;
        /** a → 中心逐渐增强的缩小强度。 */
        public float shrink = DEFAULT_SHRINK;

        @Override
        public void normalize() {
            opacity = clamp01(opacity);
            blur = clamp01(blur);
            refraction = clampNonNegative(refraction, DEFAULT_REFRACTION);
            dispersion = (float) clamp01(dispersion);
            centerDispersion = clampNonNegative(centerDispersion, DEFAULT_CENTER_DISPERSION);
            aDistance = clampNonNegative(aDistance, DEFAULT_A_DISTANCE);
            contraRatio = clampNonNegative(contraRatio, DEFAULT_CONTRA_RATIO);
            shrink = clampNonNegative(shrink, DEFAULT_SHRINK);
        }

        @Override
        public void resetToDefaults() {
            opacity = DEFAULT_OPACITY;
            blur = DEFAULT_BLUR;
            shadow = DEFAULT_SHADOW;
            refraction = DEFAULT_REFRACTION;
            dispersion = DEFAULT_DISPERSION;
            centerDispersion = DEFAULT_CENTER_DISPERSION;
            aDistance = DEFAULT_A_DISTANCE;
            contraRatio = DEFAULT_CONTRA_RATIO;
            shrink = DEFAULT_SHRINK;
        }

        private static double clamp01(double value) {
            if (Double.isNaN(value)) {
                return 0.0D;
            }
            return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
        }

        private static float clampNonNegative(float value, float fallback) {
            if (Float.isNaN(value) || value < 0.0F) {
                return fallback;
            }
            return value;
        }
    }

    /**
     * 规范化 API 地址：去除首尾空白，并剥掉命令参数或配置文件中多余的成对引号。
     * {@code greedyString()} 会保留用户输入的引号，直接拼接会导致 URL 解析失败。
     */
    public static String normalizeBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        while (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1).trim();
            } else {
                break;
            }
        }
        return value;
    }
}
