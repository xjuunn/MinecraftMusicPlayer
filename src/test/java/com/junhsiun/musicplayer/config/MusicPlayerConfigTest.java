package com.junhsiun.musicplayer.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicPlayerConfigTest {

    @Test
    void 默认baseURL正确() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertEquals("https://mycelis.dpdns.org/", config.neteaseBaseUrl);
    }

    @Test
    void 默认代理为空字符串() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertEquals("", config.proxy);
    }

    @Test
    void 默认超时值正确() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertEquals(10, config.connectTimeoutSeconds);
        assertEquals(20, config.readTimeoutSeconds);
    }

    @Test
    void 默认搜索与队列上限正确() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertEquals(8, config.searchLimit);
        assertEquals(40, config.maxQueueSize);
    }

    @Test
    void 默认每玩家点歌上限为5() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertEquals(5, config.maxSongsPerPlayer);
    }

    @Test
    void 默认歌单导入上限正确() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertEquals(20, config.playlistQueueLimit);
    }

    @Test
    void 默认投票切歌阈值正确() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertEquals(0.6D, config.voteSkipPercent, 0.001);
    }

    @Test
    void 默认战利品音乐唱片配置正确() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertTrue(config.enableLootMusicDiscs);
        assertEquals(0.3D, config.lootMusicDiscChance, 0.001);
        assertEquals(1, config.lootMusicDiscCount);
    }

    @Test
    void 默认布尔开关正确() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertTrue(config.allowCustomServer);
        assertTrue(config.allowSongRequest);
        assertTrue(config.allowPlaylistRequest);
        assertTrue(config.autoAdvance);
        assertTrue(config.announceQueueChanges);
        assertTrue(config.showLoadingHints);
        assertFalse(config.showLyrics);
        assertTrue(config.useSystemProxy);
        assertTrue(config.preferIpv4);
    }

    @Test
    void 默认预缓存数量正确() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertEquals(3, config.queueCacheSize);
    }

    @Test
    void 字段可修改() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        config.maxSongsPerPlayer = 10;
        config.maxQueueSize = 100;
        config.searchLimit = 15;
        assertEquals(10, config.maxSongsPerPlayer);
        assertEquals(100, config.maxQueueSize);
        assertEquals(15, config.searchLimit);
    }

    @Test
    void 多个实例字段独立() {
        MusicPlayerConfig a = new MusicPlayerConfig();
        MusicPlayerConfig b = new MusicPlayerConfig();
        a.maxSongsPerPlayer = 99;
        assertEquals(5, b.maxSongsPerPlayer);
    }

    @Test
    void 常量DEFAULT_NETEASE_BASE_URL与字段一致() {
        MusicPlayerConfig config = new MusicPlayerConfig();
        assertEquals(MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL, config.neteaseBaseUrl);
    }
}
