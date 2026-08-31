package com.junhsiun.musicplayer.util;

import com.junhsiun.musicplayer.model.QueuedTrack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.junhsiun.musicplayer.util.SongRequestPolicy.Decision;
import static com.junhsiun.musicplayer.util.SongRequestPolicy.Kind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 点歌请求命令级决策策略测试。
 *
 * <p>覆盖真实 {@code requestSong} 命令中执行的全部校验分支：
 * 是否启用、队列满、每玩家上限、重复歌曲，以及放行路径。
 */
class SongRequestPolicyTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private static QueuedTrack track(UUID requester) {
        return new QueuedTrack("1", "t", "a", "", requester, "p");
    }

    // ── 放行路径 ──────────────────────────────────────────

    @Test
    void 全部条件满足时允许点歌() {
        Decision d = SongRequestPolicy.decide(true, 0, 40, null, List.of(), ALICE, 5, "100", false);
        assertEquals(Kind.NOTICE, d.kind());
        assertEquals("", d.message());
    }

    @Test
    void allowed工厂返回空消息() {
        Decision d = Decision.allowed();
        assertEquals("", d.message());
        assertEquals(Kind.NOTICE, d.kind());
    }

    // ── 禁用点歌 ──────────────────────────────────────────

    @Test
    void 点歌被禁用时拒绝() {
        Decision d = SongRequestPolicy.decide(false, 0, 40, null, List.of(), ALICE, 5, "100", false);
        assertEquals(Kind.REJECT, d.kind());
        assertEquals("管理员已禁用歌曲点播。", d.message());
    }

    @Test
    void 点歌被禁用优先于其他校验() {
        // 即使队列满，禁用点歌也应返回禁用消息
        Decision d = SongRequestPolicy.decide(false, 100, 40, null, List.of(), ALICE, 5, "100", false);
        assertEquals("管理员已禁用歌曲点播。", d.message());
    }

    // ── 队列满 ────────────────────────────────────────────

    @Test
    void 队列已满时拒绝() {
        Decision d = SongRequestPolicy.decide(true, 40, 40, null, List.of(), ALICE, 5, "100", false);
        assertEquals(Kind.REJECT, d.kind());
        assertEquals("单点队列已满，请稍后再试。", d.message());
    }

    @Test
    void 队列未满时允许() {
        Decision d = SongRequestPolicy.decide(true, 39, 40, null, List.of(), ALICE, 5, "100", false);
        assertEquals("", d.message());
    }

    @Test
    void 队列紧贴上限时拒绝() {
        Decision d = SongRequestPolicy.decide(true, 40, 40, null, List.of(), ALICE, 5, "100", false);
        assertEquals(Kind.REJECT, d.kind());
    }

    @Test
    void 队列上限极大时大量歌曲也允许() {
        Decision d = SongRequestPolicy.decide(true, 999, 1000, null, List.of(), ALICE, 5, "100", false);
        assertEquals("", d.message());
    }

    // ── 每玩家点歌上限 ────────────────────────────────────

    @Test
    void 每玩家达到上限时拒绝() {
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE), track(ALICE), track(ALICE));
        Decision d = SongRequestPolicy.decide(true, 0, 40, ALICE, queued, ALICE, 5, "100", false);
        assertEquals(Kind.REJECT, d.kind());
        assertEquals("你一次最多只能点 5 首歌曲，请先播放或移除后再试。", d.message());
    }

    @Test
    void 每玩家未达上限时允许() {
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE), track(ALICE));
        Decision d = SongRequestPolicy.decide(true, 0, 40, ALICE, queued, ALICE, 5, "100", false);
        assertEquals("", d.message());
    }

    @Test
    void 其他玩家点歌不影响上限判定() {
        List<QueuedTrack> queued = List.of(track(BOB), track(BOB), track(BOB), track(BOB));
        // ALICE 未点任何歌，即使队列被 BOB 占满，ALICE 仍可点
        Decision d = SongRequestPolicy.decide(true, 4, 40, BOB, queued, ALICE, 5, "100", false);
        assertEquals("", d.message());
    }

    @Test
    void 上限为1时正在播放就不能再点() {
        Decision d = SongRequestPolicy.decide(true, 0, 40, ALICE, List.of(), ALICE, 1, "100", false);
        assertEquals(Kind.REJECT, d.kind());
    }

    @Test
    void 上限消息包含实际上限值() {
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE), track(ALICE), track(ALICE));
        Decision d = SongRequestPolicy.decide(true, 0, 40, ALICE, queued, ALICE, 10, "100", false);
        // 占用 1(播放) + 4(队列) = 5 < 10，应允许
        assertEquals("", d.message());
    }

    @Test
    void 上限为0时始终拒绝() {
        Decision d = SongRequestPolicy.decide(true, 0, 40, null, List.of(), ALICE, 0, "100", false);
        assertEquals(Kind.REJECT, d.kind());
    }

    // ── 重复歌曲 ──────────────────────────────────────────

    @Test
    void 歌曲正在播放或已在队列时给出提示() {
        Decision d = SongRequestPolicy.decide(true, 0, 40, null, List.of(), ALICE, 5, "100", true);
        assertEquals(Kind.NOTICE, d.kind());
        assertEquals("该歌曲正在播放或已在队列中。", d.message());
    }

    @Test
    void 歌曲未重复时允许() {
        Decision d = SongRequestPolicy.decide(true, 0, 40, null, List.of(), ALICE, 5, "100", false);
        Decision d2 = SongRequestPolicy.decide(true, 0, 40, null, List.of(), ALICE, 5, "200", false);
        assertEquals("", d.message());
        assertEquals("", d2.message());
    }

    // ── 校验顺序 ──────────────────────────────────────────

    @Test
    void 队列满优先于上限判定() {
        // 队列已满（40/40），即使 ALICE 未达上限也应返回队列满消息
        Decision d = SongRequestPolicy.decide(true, 40, 40, null, List.of(), ALICE, 5, "100", false);
        assertEquals("单点队列已满，请稍后再试。", d.message());
    }

    @Test
    void 上限判定优先于重复判定() {
        // 队列未满，ALICE 达到上限，且歌曲已重复 → 应返回上限消息
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE), track(ALICE), track(ALICE));
        Decision d = SongRequestPolicy.decide(true, 4, 40, ALICE, queued, ALICE, 5, "100", true);
        assertEquals("你一次最多只能点 5 首歌曲，请先播放或移除后再试。", d.message());
    }

    // ── 边界与安全 ────────────────────────────────────────

    @Test
    void 队列为null时不崩溃() {
        Decision d = SongRequestPolicy.decide(true, 0, 40, null, null, ALICE, 5, "100", false);
        assertEquals("", d.message());
    }

    @Test
    void null歌曲ID不崩溃() {
        Decision d = SongRequestPolicy.decide(true, 0, 40, null, List.of(), ALICE, 5, null, true);
        assertEquals("该歌曲正在播放或已在队列中。", d.message());
    }

    @Test
    void 允许时的Decision不等于拒绝时的Decision() {
        Decision allowed = SongRequestPolicy.decide(true, 0, 40, null, List.of(), ALICE, 5, "100", false);
        Decision rejected = SongRequestPolicy.decide(false, 0, 40, null, List.of(), ALICE, 5, "100", false);
        assertNotEquals(allowed, rejected);
    }

    @Test
    void Decision是record可比较() {
        Decision a = new Decision(Kind.REJECT, "x");
        Decision b = new Decision(Kind.REJECT, "x");
        assertEquals(a, b);
    }

    @Test
    void 播放中的点歌人也计入玩家上限() {
        // ALICE 正在播放自己的歌（占用1），队列中还有3首 → 占用4，仍可点1首
        List<QueuedTrack> queued = List.of(track(ALICE), track(ALICE), track(ALICE));
        Decision d = SongRequestPolicy.decide(true, 3, 40, ALICE, queued, ALICE, 5, "100", false);
        assertEquals("", d.message());
    }

    @Test
    void 正在播放BOB的歌时ALICE可继续点() {
        // 播放中的是 BOB 的歌，ALICE 只在队列有1首 → 占用1，ALICE 仍可点
        List<QueuedTrack> queued = List.of(track(ALICE));
        Decision d = SongRequestPolicy.decide(true, 1, 40, BOB, queued, ALICE, 5, "100", false);
        assertEquals("", d.message());
    }
}
