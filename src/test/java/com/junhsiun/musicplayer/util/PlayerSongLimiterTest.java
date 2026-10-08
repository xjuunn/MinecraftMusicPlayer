package com.junhsiun.musicplayer.util;

import com.junhsiun.musicplayer.model.QueuedTrack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSongLimiterTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();

    private static QueuedTrack track(UUID requester) {
        return new QueuedTrack("1", "t", "a", "", requester, "p");
    }

    private static QueuedTrack track(UUID requester, String songId) {
        return new QueuedTrack(songId, "t", "a", "", requester, "p");
    }

    // ── occupiedCount 基本测试 ────────────────────────────

    @Test
    void 空状态时玩家占用为零() {
        assertEquals(0, PlayerSongLimiter.occupiedCount(null, List.of(), ALICE));
    }

    @Test
    void 正在播放的歌曲计入占用() {
        List<QueuedTrack> queued = List.of(track(ALICE));
        assertEquals(2, PlayerSongLimiter.occupiedCount(ALICE, queued, ALICE));
    }

    @Test
    void 只有播放中没有队列时占用为1() {
        assertEquals(1, PlayerSongLimiter.occupiedCount(ALICE, List.of(), ALICE));
    }

    @Test
    void 只有队列没有播放中时只计队列() {
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE));
        assertEquals(2, PlayerSongLimiter.occupiedCount(null, queued, ALICE));
    }

    // ── canRequest 基本测试 ──────────────────────────────

    @Test
    void 空状态时可点播() {
        assertTrue(PlayerSongLimiter.canRequest(null, List.of(), ALICE, 5));
    }

    @Test
    void 正在播放时允许再点() {
        List<QueuedTrack> queued = List.of(track(ALICE));
        assertTrue(PlayerSongLimiter.canRequest(ALICE, queued, ALICE, 5));
    }

    // ── 上限边界 ──────────────────────────────────────────

    @Test
    void 达到上限后禁止再点() {
        // 正在播放 ALICE 的 1 首 + 队列 4 首 = 5，达到上限
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE), track(ALICE), track(ALICE));
        assertFalse(PlayerSongLimiter.canRequest(ALICE, queued, ALICE, 5));
        assertEquals(5, PlayerSongLimiter.occupiedCount(ALICE, queued, ALICE));
    }

    @Test
    void 有四首时可以再点一首() {
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE), track(ALICE));
        // 正在播放 + 3 队列 = 4，仍可点 1 首
        assertTrue(PlayerSongLimiter.canRequest(ALICE, queued, ALICE, 5));
        assertEquals(4, PlayerSongLimiter.occupiedCount(ALICE, queued, ALICE));
    }

    @Test
    void 上限为1时只能点一首() {
        assertTrue(PlayerSongLimiter.canRequest(null, List.of(), ALICE, 1));
        // 加一首到队列
        List<QueuedTrack> queued = List.of(track(ALICE));
        assertFalse(PlayerSongLimiter.canRequest(null, queued, ALICE, 1));
    }

    @Test
    void 上限为1正在播放时不能再点() {
        assertFalse(PlayerSongLimiter.canRequest(ALICE, List.of(), ALICE, 1));
    }

    @Test
    void 上限为2时可以点两首() {
        List<QueuedTrack> queued = List.of(track(ALICE));
        assertTrue(PlayerSongLimiter.canRequest(null, queued, ALICE, 2));
        // 占用 = 播放0 + 队列1 = 1，还差 1 首到上限
    }

    @Test
    void 上限为2时三首被禁止() {
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE));
        assertFalse(PlayerSongLimiter.canRequest(null, queued, ALICE, 2));
    }

    @Test
    void 上限为100时可点很多首() {
        List<QueuedTrack> queued = new ArrayList<>();
        for (int i = 0; i < 99; i++) {
            queued.add(track(ALICE, "s" + i));
        }
        assertTrue(PlayerSongLimiter.canRequest(null, queued, ALICE, 100));
        assertEquals(99, PlayerSongLimiter.occupiedCount(null, queued, ALICE));
    }

    @Test
    void 上限为0时不可点播() {
        assertFalse(PlayerSongLimiter.canRequest(null, List.of(), ALICE, 0));
    }

    @Test
    void 上限为负数时不可点播() {
        assertFalse(PlayerSongLimiter.canRequest(null, List.of(), ALICE, -1));
        assertFalse(PlayerSongLimiter.canRequest(null, List.of(), ALICE, -100));
    }

    // ── 在途请求（已放行、尚未入队） ────────────────────

    @Test
    void 在途请求计入上限() {
        // 已入队 0 首，在途 5 首 → 占用 5 = 上限，禁止
        assertFalse(PlayerSongLimiter.canRequest(null, List.of(), ALICE, 5, 5));
        // 在途 4 首 → 占用 4 < 5，允许
        assertTrue(PlayerSongLimiter.canRequest(null, List.of(), ALICE, 5, 4));
    }

    @Test
    void 在途请求与已入队歌曲合并计数() {
        // 播放中 1 首 + 队列 3 首 + 在途 1 首 = 5 = 上限
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE), track(ALICE));
        assertFalse(PlayerSongLimiter.canRequest(ALICE, queued, ALICE, 5, 1));
        // 在途 0 首 → 占用 4 < 5，允许
        assertTrue(PlayerSongLimiter.canRequest(ALICE, queued, ALICE, 5, 0));
    }

    @Test
    void 在途请求只计入被请求玩家() {
        // 队列全是 BOB 的歌，ALICE 在途 5 首仍受自己的上限约束
        List<QueuedTrack> queued = List.of(track(BOB), track(BOB));
        assertFalse(PlayerSongLimiter.canRequest(null, queued, ALICE, 5, 5));
        assertTrue(PlayerSongLimiter.canRequest(null, queued, ALICE, 5, 4));
        // 占用统计本身不含在途（在途只影响 canRequest）
        assertEquals(0, PlayerSongLimiter.occupiedCount(null, queued, ALICE));
    }

    @Test
    void 负数在途按零处理() {
        assertTrue(PlayerSongLimiter.canRequest(null, List.of(), ALICE, 5, -1));
        assertTrue(PlayerSongLimiter.canRequest(ALICE, List.of(), ALICE, 2, -100));
    }

    @Test
    void 上限为0时即使无在途也不可点播() {
        assertFalse(PlayerSongLimiter.canRequest(null, List.of(), ALICE, 0, 5));
    }

    @Test
    void 四参重载等价于在途为零() {
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE));
        assertEquals(
                PlayerSongLimiter.canRequest(ALICE, queued, ALICE, 5),
                PlayerSongLimiter.canRequest(ALICE, queued, ALICE, 5, 0));
        assertEquals(
                PlayerSongLimiter.canRequest(null, List.of(), ALICE, 0),
                PlayerSongLimiter.canRequest(null, List.of(), ALICE, 0, 3));
    }

    // ── 多玩家独立计数 ────────────────────────────────────

    @Test
    void 其他玩家的歌曲不计入() {
        List<QueuedTrack> queued = List.of(track(BOB), track(BOB), track(BOB));
        assertTrue(PlayerSongLimiter.canRequest(ALICE, queued, ALICE, 5));
        assertEquals(0, PlayerSongLimiter.occupiedCount(null, queued, ALICE));
    }

    @Test
    void 混合玩家各自独立计数() {
        List<QueuedTrack> queued = List.of(
                track(ALICE), track(ALICE), track(ALICE),  // ALICE: 3首
                track(BOB), track(BOB),                     // BOB: 2首
                track(CAROL)                                // CAROL: 1首
        );
        // ALICE: 3首，未达上限
        assertTrue(PlayerSongLimiter.canRequest(null, queued, ALICE, 5));
        assertEquals(3, PlayerSongLimiter.occupiedCount(null, queued, ALICE));
        // BOB: 2首，未达上限
        assertTrue(PlayerSongLimiter.canRequest(null, queued, BOB, 5));
        assertEquals(2, PlayerSongLimiter.occupiedCount(null, queued, BOB));
        // CAROL: 1首，未达上限
        assertTrue(PlayerSongLimiter.canRequest(null, queued, CAROL, 5));
        assertEquals(1, PlayerSongLimiter.occupiedCount(null, queued, CAROL));
    }

    @Test
    void 混合玩家达到各自上限() {
        List<QueuedTrack> queued = List.of(
                track(ALICE), track(ALICE), track(ALICE), track(ALICE),  // ALICE: 4首
                track(BOB), track(BOB), track(BOB), track(BOB),          // BOB: 4首
                track(CAROL)                                              // CAROL: 1首
        );
        // 上限为5：ALICE 播放1 + 队列4 = 5 → 禁止
        assertFalse(PlayerSongLimiter.canRequest(ALICE, queued, ALICE, 5));
        assertEquals(5, PlayerSongLimiter.occupiedCount(ALICE, queued, ALICE));
        // BOB 没有播放中的，只有队列4 → 允许
        assertTrue(PlayerSongLimiter.canRequest(null, queued, BOB, 5));
        assertEquals(4, PlayerSongLimiter.occupiedCount(null, queued, BOB));
        // CAROL 只有1首 → 允许
        assertTrue(PlayerSongLimiter.canRequest(null, queued, CAROL, 5));
    }

    @Test
    void ALICE的歌曲不影响BOB的计数() {
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE), track(ALICE));
        assertEquals(0, PlayerSongLimiter.occupiedCount(null, queued, BOB));
        assertTrue(PlayerSongLimiter.canRequest(null, queued, BOB, 5));
    }

    // ── null 安全 ─────────────────────────────────────────

    @Test
    void player为null时不累计队列() {
        assertEquals(0, PlayerSongLimiter.occupiedCount(null, List.of(track(ALICE)), null));
    }

    @Test
    void player为null时occupiedCount为零() {
        assertEquals(0, PlayerSongLimiter.occupiedCount(null, List.of(), null));
    }

    @Test
    void requester为null时只计队列中的() {
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE));
        assertEquals(2, PlayerSongLimiter.occupiedCount(null, queued, ALICE));
    }

    @Test
    void 队列为null时安全处理() {
        assertEquals(1, PlayerSongLimiter.occupiedCount(ALICE, null, ALICE));
    }

    // ── 边界：播放中与队列交叉 ────────────────────────────

    @Test
    void 播放中是ALICE但队列全是BOB() {
        List<QueuedTrack> queued = List.of(track(BOB), track(BOB), track(BOB));
        assertEquals(1, PlayerSongLimiter.occupiedCount(ALICE, queued, ALICE));
        assertTrue(PlayerSongLimiter.canRequest(ALICE, queued, ALICE, 5));
    }

    @Test
    void 播放中是BOB但队列是ALICE() {
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE), track(ALICE));
        // 播放中是BOB → ALICE不计入播放中
        assertEquals(3, PlayerSongLimiter.occupiedCount(BOB, queued, ALICE));
        assertTrue(PlayerSongLimiter.canRequest(BOB, queued, ALICE, 5));
    }

    @Test
    void 请求者UUID变更后重新计数() {
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE));
        // ALICE 不在播放中
        assertEquals(2, PlayerSongLimiter.occupiedCount(BOB, queued, ALICE));
        // ALICE 在播放中
        assertEquals(3, PlayerSongLimiter.occupiedCount(ALICE, queued, ALICE));
    }
}
