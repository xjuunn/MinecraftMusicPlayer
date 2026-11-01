package com.junhsiun.musicplayer.client.config;

import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;

/**
 * 客户端配置门面。
 *
 * <p>配置本体已经并入模组统一文件（见 {@link MusicPlayerConfig#client}），本类只保留
 * 客户端侧习惯的访问方式，避免调用点到处改。所有读写都转发到
 * {@link MusicPlayerConfigManager}，因此不会再出现第二个管理器写盘的问题。</p>
 */
public final class MusicPlayerClientConfigManager {

    private MusicPlayerClientConfigManager() {
    }

    /** 与统一配置的加载同步，无需单独动作。 */
    public static void load() {
        MusicPlayerConfigManager.load();
    }

    public static void save() {
        MusicPlayerConfigManager.save();
    }

    public static void markDirty() {
        MusicPlayerConfigManager.markDirty();
    }

    public static void tick() {
        MusicPlayerConfigManager.tick();
    }

    public static void flushDirty() {
        MusicPlayerConfigManager.flushDirty();
    }

    /** 客户端设置分节。 */
    public static MusicPlayerConfig.ClientSettings get() {
        return MusicPlayerConfigManager.get().client;
    }
}
