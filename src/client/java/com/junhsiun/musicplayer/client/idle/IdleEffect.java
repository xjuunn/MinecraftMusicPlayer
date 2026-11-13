package com.junhsiun.musicplayer.client.idle;

import java.util.Locale;

/**
 * 纯享模式的三种视觉预设。
 *
 * <p>顺序即着色器里的 {@code IDLE_MODE} 常量，也即设置页标签的顺序，
 * 新增预设时三处一起改。</p>
 */
public enum IdleEffect {
    LIQUID("liquid", 0),
    AURORA("aurora", 1),
    CINEMATIC("cinematic", 2);

    private final String key;
    private final int shaderMode;

    IdleEffect(String key, int shaderMode) {
        this.key = key;
        this.shaderMode = shaderMode;
    }

    public String key() {
        return key;
    }

    public int shaderMode() {
        return shaderMode;
    }

    public String translationKey() {
        return "musicplayer.settings.idle.effect." + key;
    }

    public static IdleEffect of(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (IdleEffect effect : values()) {
            if (effect.key.equals(normalized)) {
                return effect;
            }
        }
        return LIQUID;
    }

    public static IdleEffect fromConfig() {
        return of(com.junhsiun.musicplayer.config.MusicPlayerConfigManager.get().idle.effect);
    }

    /** 设置页标签用的下标记。 */
    public static int indexOf(IdleEffect effect) {
        return effect == null ? 0 : effect.ordinal();
    }
}
