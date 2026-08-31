package com.junhsiun.musicplayer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaFormatUtilTest {

    // ── MP3 可播放 ────────────────────────────────────────

    @Test
    void mp3直链可播放() {
        assertTrue(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/audio/123.mp3"));
    }

    @Test
    void 大写扩展名也识别() {
        assertTrue(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/audio/123.MP3"));
    }

    @Test
    void 无扩展名的直链默认可播放() {
        assertTrue(MediaFormatUtil.isJlayerPlayable("https://api.qijieya.cn/stream?id=123"));
    }

    @Test
    void URL带查询参数的mp3可播放() {
        assertTrue(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/audio/123.mp3?token=abc&exp=123"));
    }

    @Test
    void 常见mp3带端口与路径() {
        assertTrue(MediaFormatUtil.isJlayerPlayable("https://host:8080/music/song.mp3"));
    }

    // ── 非 MP3 不可播放（JLayer 仅支持 MP3）───────────────

    @Test
    void flac直链不可播放() {
        assertFalse(MediaFormatUtil.isJlayerPlayable("http://m701.music.126.net/20260831/xyz.flac"));
    }

    @Test
    void 各类无损其他格式不可播放() {
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.flac"));
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.m4a"));
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.aac"));
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.wav"));
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.ogg"));
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.opus"));
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.wma"));
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.ape"));
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.mp4"));
    }

    @Test
    void 带查询参数的flac不可播放() {
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.flac?token=x"));
    }

    @Test
    void 大写flac扩展名不可播放() {
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.FLAC"));
    }

    // ── 边界 ──────────────────────────────────────────────

    @Test
    void null返回不可播放() {
        assertFalse(MediaFormatUtil.isJlayerPlayable(null));
    }

    @Test
    void 空字符串返回不可播放() {
        assertFalse(MediaFormatUtil.isJlayerPlayable(""));
        assertFalse(MediaFormatUtil.isJlayerPlayable("   "));
    }

    @Test
    void 扩展名不在黑名单时按可播放处理() {
        assertTrue(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.xyz"));
        assertTrue(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.radio"));
    }

    @Test
    void 域名中的点不会误判为扩展名() {
        // host 部分含点，但路径无扩展名 → 应看作无扩展名（可播放）
        assertTrue(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/play"));
    }

    @Test
    void 路径尾部是目录带点但非文件扩展名() {
        assertTrue(MediaFormatUtil.isJlayerPlayable("https://example.com/music.m3u8"));
    }

    @Test
    void 片段标识不影响扩展名判断() {
        assertTrue(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.mp3#t=10"));
        assertFalse(MediaFormatUtil.isJlayerPlayable("https://cdn.example.com/a.flac#t=10"));
    }
}
