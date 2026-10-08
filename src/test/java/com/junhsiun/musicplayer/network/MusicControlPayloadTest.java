package com.junhsiun.musicplayer.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicControlPayloadTest {

    @Test
    void play构造过滤无效URL() {
        List<String> urls = new ArrayList<>();
        urls.add("http://valid.mp3");
        urls.add("invalid");
        urls.add(null);
        urls.add("ftp://bad.mp3");
        MusicControlPayload payload = MusicControlPayload.play("123", urls, "title", "subtitle", 5000L);
        assertEquals("play", payload.action());
        assertEquals("123", payload.trackId());
        assertEquals(1, payload.urls().size());
        assertEquals("http://valid.mp3", payload.urls().get(0));
    }

    @Test
    void play过长URL被过滤() {
        String longUrl = "http://" + "a".repeat(30000) + ".mp3";
        MusicControlPayload payload = MusicControlPayload.play("1", List.of(longUrl), "t", "s", 0L);
        assertTrue(payload.urls().isEmpty());
    }

    @Test
    void stop构造() {
        MusicControlPayload payload = MusicControlPayload.stop("reason");
        assertEquals("stop", payload.action());
        assertEquals("reason", payload.message());
        assertTrue(payload.urls().isEmpty());
    }
}
