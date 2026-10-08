package com.junhsiun.musicplayer.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueuedTrackTest {

    private static final UUID USER = UUID.randomUUID();

    @Test
    void record字段正确赋值() {
        QueuedTrack track = new QueuedTrack("100", "歌名", "歌手", "/artist 100", USER, "玩家", List.of("url1", "url2"));
        assertEquals("100", track.songId());
        assertEquals("歌名", track.title());
        assertEquals("歌手", track.artist());
        assertEquals("/artist 100", track.artistCommand());
        assertEquals(USER, track.requesterId());
        assertEquals("玩家", track.requesterName());
        assertEquals(List.of("url1", "url2"), track.sourceUrls());
    }

    @Test
    void 相同参数的两个record相等() {
        QueuedTrack a = new QueuedTrack("1", "t", "a", "c", USER, "n", List.of("u1"));
        QueuedTrack b = new QueuedTrack("1", "t", "a", "c", USER, "n", List.of("u1"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void 不同songId的record不等() {
        QueuedTrack a = new QueuedTrack("1", "t", "a", "c", USER, "n");
        QueuedTrack b = new QueuedTrack("2", "t", "a", "c", USER, "n");
        assertNotEquals(a, b);
    }

    @Test
    void 不同requesterId的record不等() {
        UUID other = UUID.randomUUID();
        QueuedTrack a = new QueuedTrack("1", "t", "a", "c", USER, "n");
        QueuedTrack b = new QueuedTrack("1", "t", "a", "c", other, "n");
        assertNotEquals(a, b);
    }

    @Test
    void toString包含所有字段() {
        QueuedTrack track = new QueuedTrack("1", "t", "a", "c", USER, "n");
        String str = track.toString();
        assertTrue(str.contains("1"));
        assertTrue(str.contains("t"));
        assertTrue(str.contains("a"));
        assertTrue(str.contains("n"));
    }

    @Test
    void null字段也能创建record() {
        QueuedTrack track = new QueuedTrack(null, null, null, null, null, null, null);
        assertEquals(null, track.songId());
        assertEquals(null, track.requesterId());
        assertTrue(track.sourceUrls().isEmpty());
    }

    @Test
    void 兼容旧构造函数() {
        QueuedTrack track = new QueuedTrack("1", "t", "a", "c", USER, "n");
        assertEquals("1", track.songId());
        assertTrue(track.sourceUrls().isEmpty());
    }
}
