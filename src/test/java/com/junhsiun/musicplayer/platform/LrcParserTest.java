package com.junhsiun.musicplayer.platform;

import com.junhsiun.musicplayer.model.LyricLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrcParserTest {

    // ── 基本解析 ──────────────────────────────────────────

    @Test
    void 解析标准LRC行() {
        String lrc = "[00:01.00]第一句\n[00:03.50]第二句\n";
        List<LyricLine> lines = LyricService.parseLrc(lrc);
        assertEquals(2, lines.size());
        assertEquals(new LyricLine(1_000L, "第一句"), lines.get(0));
        assertEquals(new LyricLine(3_500L, "第二句"), lines.get(1));
    }

    @Test
    void 解析分钟级时间戳() {
        String lrc = "[01:02.5]内容\n";
        List<LyricLine> lines = LyricService.parseLrc(lrc);
        assertEquals(1, lines.size());
        assertEquals(62_500L, lines.get(0).timeMillis());
    }

    @Test
    void 解析小时级时间戳() {
        // [01:02:03.00] 有些LRC扩展支持小时
        // 但标准LRC是 [MM:SS.xx]，所以 [01:02:03.00] 会被解析为 MM=01, SS=02, xx=03（截断）
        String lrc = "[01:30.00]测试\n";
        List<LyricLine> lines = LyricService.parseLrc(lrc);
        assertEquals(1, lines.size());
        assertEquals(90_000L, lines.get(0).timeMillis());
    }

    // ── 排序 ──────────────────────────────────────────────

    @Test
    void 时间戳自动排序() {
        String lrc = "[00:03.00]晚\n[00:01.00]早\n";
        List<LyricLine> lines = LyricService.parseLrc(lrc);
        assertEquals(List.of(new LyricLine(1_000L, "早"), new LyricLine(3_000L, "晚")), lines);
    }

    @Test
    void 相同时间戳保持出现顺序() {
        String lrc = "[00:01.00]A\n[00:01.00]B\n";
        List<LyricLine> lines = LyricService.parseLrc(lrc);
        assertEquals(2, lines.size());
        assertEquals("A", lines.get(0).text());
        assertEquals("B", lines.get(1).text());
    }

    // ── 过滤 ──────────────────────────────────────────────

    @Test
    void 忽略空行与标签行() {
        String lrc = "[ti:标题]\n[00:01.00]实际\n\n";
        List<LyricLine> lines = LyricService.parseLrc(lrc);
        assertEquals(1, lines.size());
        assertEquals("实际", lines.get(0).text());
    }

    @Test
    void 忽略各种标签行() {
        String lrc = "[ar:艺术家]\n[al:专辑]\n[by:制作者]\n[00:02.00]歌词\n";
        List<LyricLine> lines = LyricService.parseLrc(lrc);
        assertEquals(1, lines.size());
        assertEquals("歌词", lines.get(0).text());
    }

    @Test
    void 空文本返回空列表() {
        assertTrue(LyricService.parseLrc("").isEmpty());
        assertTrue(LyricService.parseLrc(null).isEmpty());
    }

    @Test
    void 纯标签无歌词返回空列表() {
        String lrc = "[ti:标题]\n[ar:艺术家]\n";
        assertTrue(LyricService.parseLrc(lrc).isEmpty());
    }

    @Test
    void 纯空格文本返回空列表() {
        assertTrue(LyricService.parseLrc("   ").isEmpty());
    }

    // ── 格式错误 ──────────────────────────────────────────

    @Test
    void 格式错误的时间戳被忽略() {
        String lrc = "[abc]无效\n[00:01.00]有效\n";
        List<LyricLine> lines = LyricService.parseLrc(lrc);
        assertEquals(1, lines.size());
        assertEquals("有效", lines.get(0).text());
    }

    @Test
    void 只有左括号没有右括号被忽略() {
        String lrc = "[00:01.00有效\n";
        List<LyricLine> lines = LyricService.parseLrc(lrc);
        assertTrue(lines.isEmpty());
    }

    @Test
    void 空歌词文本被过滤() {
        String lrc = "[00:01.00]\n";
        List<LyricLine> lines = LyricService.parseLrc(lrc);
        assertTrue(lines.isEmpty());
    }

    // ── findCurrentLine ───────────────────────────────────

    @Test
    void findCurrentLine_返回当前行() {
        List<LyricLine> lines = List.of(new LyricLine(1_000L, "a"), new LyricLine(3_000L, "b"));
        assertEquals("a", LyricService.findCurrentLine(lines, 1_500L).text());
        assertEquals("b", LyricService.findCurrentLine(lines, 4_000L).text());
    }

    @Test
    void findCurrentLine_时间在第一句之前返回null() {
        List<LyricLine> lines = List.of(new LyricLine(1_000L, "a"), new LyricLine(3_000L, "b"));
        assertNull(LyricService.findCurrentLine(lines, 500L));
    }

    @Test
    void findCurrentLine_null列表返回null() {
        assertNull(LyricService.findCurrentLine(null, 500L));
    }

    @Test
    void findCurrentLine_空列表返回null() {
        assertNull(LyricService.findCurrentLine(List.of(), 500L));
    }

    @Test
    void findCurrentLine_精确匹配时间戳() {
        List<LyricLine> lines = List.of(new LyricLine(1_000L, "a"), new LyricLine(3_000L, "b"));
        assertEquals("a", LyricService.findCurrentLine(lines, 1_000L).text());
        assertEquals("b", LyricService.findCurrentLine(lines, 3_000L).text());
    }

    @Test
    void findCurrentLine_时间在两句之间取前一句() {
        List<LyricLine> lines = List.of(new LyricLine(1_000L, "a"), new LyricLine(3_000L, "b"), new LyricLine(5_000L, "c"));
        assertEquals("b", LyricService.findCurrentLine(lines, 4_000L).text());
    }

    @Test
    void findCurrentLine_只有单行歌词() {
        List<LyricLine> lines = List.of(new LyricLine(1_000L, "only"));
        assertEquals("only", LyricService.findCurrentLine(lines, 10_000L).text());
        assertNull(LyricService.findCurrentLine(lines, 500L));
    }

    // ── 歌词时间轴（加载期不同步）─────────────────────────

    @Test
    void 客户端还没出声时歌词停在起点() {
        // 服务端墙钟已经走了 2 秒，但客户端还在下载音频
        assertEquals(0L, LyricService.resolveElapsedMillis(false, -1L, 0L, 2_000L, 3_000L));
    }

    @Test
    void 宽限期过后回退到服务端计时_兼容无模组客户端() {
        assertEquals(8_000L, LyricService.resolveElapsedMillis(false, -1L, 0L, 8_000L, 3_000L));
    }

    @Test
    void 客户端出声后只信客户端位置() {
        // 服务端墙钟 12 秒（含 10 秒下载），客户端实际只播了 2 秒
        assertEquals(2_500L, LyricService.resolveElapsedMillis(true, 2_000L, 500L, 12_000L, 3_000L));
    }

    @Test
    void 位置回报之间有间隔时按上报时刻外推() {
        assertEquals(3_000L, LyricService.resolveElapsedMillis(true, 2_000L, 1_000L, 9_999L, 3_000L));
    }

    @Test
    void 已出声但还没收到位置时退回服务端计时() {
        assertEquals(4_000L, LyricService.resolveElapsedMillis(true, -1L, 0L, 4_000L, 3_000L));
    }

    @Test
    void 位置不会算出负数() {
        assertEquals(0L, LyricService.resolveElapsedMillis(true, -5L, -5L, 0L, 3_000L));
    }
}