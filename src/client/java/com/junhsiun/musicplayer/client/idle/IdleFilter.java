package com.junhsiun.musicplayer.client.idle;

import java.util.Locale;

/**
 * 纯享模式的背景滤镜。
 *
 * <p>滤镜作用在特效最终画面上，用来决定整体色调；顺序即着色器里的滤镜编号，
 * 也即设置页标签的顺序，新增滤镜时三处一起改。</p>
 */
public enum IdleFilter {
    NONE("none", 0),
    SOFT("soft", 1),
    WARM("warm", 2),
    COOL("cool", 3),
    MONO("mono", 4),
    NEON("neon", 5);

    private final String key;
    private final int shaderIndex;

    IdleFilter(String key, int shaderIndex) {
        this.key = key;
        this.shaderIndex = shaderIndex;
    }

    public String key() {
        return key;
    }

    public int shaderIndex() {
        return shaderIndex;
    }

    public String translationKey() {
        return "musicplayer.settings.idle.filter." + key;
    }

    public static IdleFilter of(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (IdleFilter filter : values()) {
            if (filter.key.equals(normalized)) {
                return filter;
            }
        }
        return NONE;
    }

    public static IdleFilter fromConfig() {
        return of(com.junhsiun.musicplayer.config.MusicPlayerConfigManager.get().idle.filter);
    }

    /** 设置页标签用的下标记。 */
    public static int indexOf(IdleFilter filter) {
        return filter == null ? 0 : filter.ordinal();
    }
}
