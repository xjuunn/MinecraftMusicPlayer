package com.junhsiun.musicplayer.client.gui.media;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * 详情页封面：从列表里的小图（模糊）平滑过渡到高清大图。
 *
 * <p><b>设计目标：任何一帧都不允许出现空白或硬切。</b>之前有两个会“闪一下”的来源：</p>
 * <ul>
 *   <li>详情接口回来得比共享元素动画早时，页面封面 URL 会从列表快照换成详情里的地址；
 *       旧逻辑按 URL 重置状态，新地址的纹理还没下载完，于是先画了一帧占位块。</li>
 *   <li>过渡结束那一帧直接换纹理：小图（64px）一步变成大图（256px），即便只是一帧，
 *       看起来也是一次跳变，而不是「由虚到实」。</li>
 * </ul>
 *
 * <p>这里把它们统一成一条规则：<b>先记住“现在屏幕上是什么”，之后任何变化都从它淡过去。</b>
 * 只要画过一张图，就永远继续画那张图，直到新图真的可用；新图出现后，用小图铺底、大图按
 * alpha 叠上去，做一次很短的交叉淡入——这正是“模糊 → 清晰”的观感。</p>
 *
 * <p>共享元素动画结束时通过 {@link #handoff(MediaThumbnails.Thumb)} 把飞行层最后那张纹理
 * 直接交接过来（传引用，不做二次查表，因此不怕缓存淘汰），落地首帧与飞行末帧是同一张图，
 * 中间不会插入任何占位或重置。</p>
 */
public final class CoverFade {

    /** 交叉淡入时长（毫秒）。够短不拖沓，又足以让人看清清晰度是连续变化的。 */
    private static final long FADE_MILLIS = 180L;

    /** 当前画面上实际显示的那张图；{@code null} 表示还没有任何图可画。 */
    private MediaThumbnails.Thumb shown;
    /** 正在进行的交叉淡入：起点与终点。 */
    private MediaThumbnails.Thumb fadeFrom;
    private MediaThumbnails.Thumb fadeTo;
    private long fadeStartMillis;

    /**
     * 由页面在共享元素过渡结束的那一帧调用，交还飞行层<b>真正显示过</b>的那张纹理。
     *
     * <p>这里直接接收缩略图引用而不是地址：飞行层已经持有这张纹理，传引用可以保证落地
     * 首帧与飞行末帧一定是同一张图；如果改成按地址重新查表，恰好碰上缓存淘汰就会查不到，
     * 又退回占位块闪一下。</p>
     *
     * @param thumb 飞行层最后显示的纹理；{@code null} 时保持当前画面不变
     */
    public void handoff(MediaThumbnails.Thumb thumb) {
        if (thumb != null) {
            shown = thumb;
        }
        // 落地首帧完全以飞行末帧为准：丢弃还在进行的淡入，避免两段画面叠加。
        fadeFrom = null;
        fadeTo = null;
    }

    /**
     * 画封面；返回 {@code false} 表示当前没有任何可用纹理，调用方应画占位块。
     *
     * <p>只有“从未显示过任何图，且目标图也没下载好”这一种情况才会返回 {@code false}。
     * 一旦显示过图，后续 URL 变化、高清图迟到，都只会从旧图淡入新图。</p>
     */
    public boolean draw(GuiGraphicsExtractor graphics, String url,
                        int x0, int y0, int x1, int y1) {
        MediaThumbnails thumbs = MediaThumbnails.getInstance();
        String targetUrl = url == null ? "" : url;
        // 每帧查询一次：大图优先，未就绪时退小图；两个查询都会顺带触发下载。
        MediaThumbnails.Thumb best = targetUrl.isBlank() ? null : thumbs.getLarge(targetUrl);
        if (best == null && !targetUrl.isBlank()) {
            best = thumbs.get(targetUrl);
        }

        long now = Util.getMillis();

        // ① 正在淡入：先把这一小段走完再考虑新的目标，避免中途换目标导致画面回跳。
        if (fadeTo != null) {
            long elapsed = now - fadeStartMillis;
            if (elapsed < FADE_MILLIS) {
                drawFull(graphics, fadeFrom, x0, y0, x1, y1);
                drawFaded(graphics, fadeTo, x0, y0, x1, y1, elapsed / (float) FADE_MILLIS);
                return true;
            }
            shown = fadeTo;
            fadeFrom = null;
            fadeTo = null;
        }

        // ② 有更好的图可用（新地址的小图 / 同一地址的高清图）：从当前画面淡过去。
        if (best != null && (shown == null || !sameTexture(best, shown))) {
            if (shown == null) {
                shown = best;
                drawFull(graphics, shown, x0, y0, x1, y1);
            } else {
                fadeFrom = shown;
                fadeTo = best;
                fadeStartMillis = now;
                drawFull(graphics, fadeFrom, x0, y0, x1, y1);
            }
            return true;
        }

        // ③ 没有新图：继续画当前这张（哪怕目标暂时还没下载好，也绝不回落到占位）。
        if (shown != null) {
            drawFull(graphics, shown, x0, y0, x1, y1);
            return true;
        }
        return false;
    }

    /** 两张纹理是否是同一张（同一地址同一档纹理的 Identifier 相同）。 */
    private static boolean sameTexture(MediaThumbnails.Thumb first, MediaThumbnails.Thumb second) {
        return first == second || first.id().equals(second.id());
    }

    private static void drawFull(GuiGraphicsExtractor graphics, MediaThumbnails.Thumb thumb,
                                 int x0, int y0, int x1, int y1) {
        if (thumb != null) {
            MediaThumbnails.drawBounded(graphics, thumb, x0, y0, x1, y1);
        }
    }

    private static void drawFaded(GuiGraphicsExtractor graphics, MediaThumbnails.Thumb thumb,
                                  int x0, int y0, int x1, int y1, float amount) {
        if (thumb == null) {
            return;
        }
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        // smoothstep：两端斜率更柔和，淡入不会在开始/结束时“顶”一下。
        float eased = clamped * clamped * (3.0F - 2.0F * clamped);
        int alpha = Math.round(255.0F * eased);
        if (alpha <= 0) {
            return;
        }
        MediaThumbnails.drawBounded(graphics, thumb, x0, y0, x1, y1, (alpha << 24) | 0xFFFFFF);
    }
}