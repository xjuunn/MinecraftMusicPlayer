package com.junhsiun.musicplayer.platform.api;

import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;

import java.util.Locale;

/**
 * 音乐接口取数模式。
 *
 * <p>决定 {@link MusicApiRouter} 是否优先走网易云直连：
 * <ul>
 *   <li>{@link #AUTO}：默认值，直连优先，失败自动回退配置的后端地址；</li>
 *   <li>{@link #DIRECT}：只走直连，便于排查后端或明确要求不依赖第三方服务；</li>
 *   <li>{@link #PROXY}：只走后端，保留原有行为，适合直连被网络环境阻断的场景。</li>
 * </ul>
 */
public enum MusicApiMode {
    AUTO,
    DIRECT,
    PROXY;

    public static final String CONFIG_AUTO = "auto";
    public static final String CONFIG_DIRECT = "direct";
    public static final String CONFIG_PROXY = "proxy";

    /** 读取当前配置的模式；无法识别时按 {@link #AUTO} 处理，保证配置损坏也能正常取数。 */
    public static MusicApiMode current() {
        return fromConfig(MusicPlayerConfigManager.get().apiMode);
    }

    public static MusicApiMode fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case CONFIG_DIRECT -> DIRECT;
            case CONFIG_PROXY -> PROXY;
            default -> AUTO;
        };
    }

    /** 写入配置用的字符串值。 */
    public String configValue() {
        return switch (this) {
            case DIRECT -> CONFIG_DIRECT;
            case PROXY -> CONFIG_PROXY;
            case AUTO -> CONFIG_AUTO;
        };
    }

    /** 直连失败时是否回退后端。 */
    public boolean fallsBackToProxy() {
        return this == AUTO;
    }

    /** 规范化用户输入；非法值返回 null。 */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case CONFIG_AUTO -> CONFIG_AUTO;
            case CONFIG_DIRECT -> CONFIG_DIRECT;
            case CONFIG_PROXY -> CONFIG_PROXY;
            default -> null;
        };
    }

    /** 命令展示用的翻译键。 */
    public String statusKey() {
        return switch (this) {
            case DIRECT -> "musicplayer.config.api_mode_direct";
            case PROXY -> "musicplayer.config.api_mode_proxy";
            case AUTO -> "musicplayer.config.api_mode_auto";
        };
    }
}
