package com.junhsiun.musicplayer.platform.api;

import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.util.Java8;

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
        if (Java8.isBlank(raw)) {
            return AUTO;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (CONFIG_DIRECT.equals(normalized)) {
            return DIRECT;
        }
        if (CONFIG_PROXY.equals(normalized)) {
            return PROXY;
        }
        return AUTO;
    }

    /** 写入配置用的字符串值。 */
    public String configValue() {
        if (this == DIRECT) {
            return CONFIG_DIRECT;
        }
        if (this == PROXY) {
            return CONFIG_PROXY;
        }
        return CONFIG_AUTO;
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
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (CONFIG_AUTO.equals(normalized) || CONFIG_DIRECT.equals(normalized) || CONFIG_PROXY.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    /** 命令展示用的翻译键。 */
    public String statusKey() {
        if (this == DIRECT) {
            return "musicplayer.config.api_mode_direct";
        }
        if (this == PROXY) {
            return "musicplayer.config.api_mode_proxy";
        }
        return "musicplayer.config.api_mode_auto";
    }

    /** 命令展示当前服务地址行的翻译键：直连模式无需展示后端地址。 */
    public String statusBaseUrlKey() {
        if (this == DIRECT) {
            return "musicplayer.config.status_base_url_direct";
        }
        if (this == PROXY) {
            return "musicplayer.config.status_base_url";
        }
        return "musicplayer.config.status_base_url_auto";
    }
}
