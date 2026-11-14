package com.junhsiun.musicplayer.client.gui.animation;

import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import com.junhsiun.musicplayer.client.gui.media.MediaThumbnails;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * 一次性的「共享元素过渡 / Hero 动画」控制器，前进、返回两个方向共用。
 *
 * <p>列表/详情页的封面与标题会被当成同一个元素：动画期间由本类在<b>所有页面之上</b>
 * 单独画一份，从 {@code from} 插值到 {@code to}；两端各自的真实元素先隐藏，等动画落点
 * 完全重合后再交还。这样既不会出现重影，也不会出现「目标已经好了、东西还在飘」。</p>
 *
 * <p><b>为什么用时间轴而不是弹簧：</b>飞行是一次性、有明确终点的位移，缓出曲线
 * （ease-out）在起点速度最高、终点平滑收住，观感最利落；弹簧用于可打断的交互（按压、
 * 悬停）更合适，这里不采用，避免落点轻微过冲后还要回弹一下。</p>
 *
 * <p><b>平滑度：</b>所有几何都按浮点位插值，再以亚像素平移提交（不是逐帧取整后跳变），
 * 因此 60/120Hz 下都是一格一格连续移动；缩略图只在开始后取到第一张时查一次缓存，
 * 之后整段动画复用同一个纹理引用，避免每帧哈希查找与可能的下载触发。</p>
 */
public final class SharedElementTransition {

    /** 总时长（毫秒）。保持短促利落：一次性飞行不需要慢慢铺陈，太长会显得拖沓。 */
    private static final long DURATION_MILLIS = 280L;
    /** 其余内容（歌手、专辑、歌词…）延迟多久开始淡入，占总时长的比例。 */
    private static final float CONTENT_DELAY = 0.16F;
    /** 其余内容淡入持续多久，占总时长的比例。 */
    private static final float CONTENT_SPAN = 0.66F;
    /**
     * 封面显示尺寸达到这个像素值（详情头部 44px、歌曲详情 84px）时，飞行图层用高清纹理；
     * 更小的（列表行 24px）用小图，与点击前那一帧完全一致。
     */
    private static final int LARGE_COVER_MIN_PIXELS = 40;

    private boolean active;
    private long startMillis;
    private HeroElement from;
    private HeroElement to;
    private Component titleText = Component.empty();
    /** 飞行中的副标题 / 专辑 / 末尾文字；只在两端都有对应元素时非空。 */
    private Component subtitleText = Component.empty();
    private Component albumText = Component.empty();
    private Component metaText = Component.empty();
    private boolean subtitleFlying;
    private boolean albumFlying;
    private boolean metaFlying;
    /** 飞行期间复用同一张缩略图引用；取到后不再查表。 */
    private MediaThumbnails.Thumb cachedThumb;
    /**
     * 本帧统一的时间戳。
     *
     * <p>页面与最上层 overlay 是两次独立的渲染调用：如果各自调用 {@code Util.getMillis()}，
     * 一旦两次判断之间跨过了动画结束的那一毫秒，就会出现「页面认为还在飞、不画封面」而
     * 「overlay 认为已经结束、也不画」的一帧——也就是那一下闪。这里由外壳每帧开头采样
     * 一次，所有判断都用同一个时间点，谁画谁不画永远一致。</p>
     */
    private long frameMillis;

    /**
     * 从 {@code from} 飞向 {@code to}。任一端为空时直接视为无动画，调用方应走普通跳转。
     */
    public void start(HeroElement from, HeroElement to) {
        if (from == null || to == null) {
            cancel();
            return;
        }
        this.from = from;
        this.to = to;
        this.cachedThumb = null;
        // 文字以目标端为准：列表行/头部块会把长标题截断，若沿用起点那串，落地瞬间会
        // 换成目标页的文本——看起来就是「字突然变多/变少」。全程用目标文本就没有这一跳。
        this.titleText = literal(to.title(), from.title());
        // 副标题 / 末尾文字只有在两端都提供了对应槽位时才参与飞行；否则交给页面自己处理。
        this.subtitleFlying = hasPair(from.subtitle(), to.subtitle());
        this.albumFlying = hasPair(from.album(), to.album());
        this.metaFlying = hasPair(from.meta(), to.meta());
        this.subtitleText = subtitleFlying ? literal(to.subtitle().text(), from.subtitle().text()) : Component.empty();
        this.albumText = albumFlying ? literal(to.album().text(), from.album().text()) : Component.empty();
        this.metaText = metaFlying ? literal(to.meta().text(), from.meta().text()) : Component.empty();
        this.startMillis = Util.getMillis();
        this.frameMillis = this.startMillis;
        this.active = true;
    }

    /**
     * 由外壳在每帧渲染最开头调用：锁住本帧时间，页面与 overlay 共用。
     *
     * <p>必须在 {@code super.extractRenderState(...)} 之前调用，否则页面控件已经先用旧时间
     * 判断过一次，起不到统一的作用。</p>
     */
    public void beginFrame() {
        this.frameMillis = Util.getMillis();
    }

    private long now() {
        return frameMillis == 0L ? Util.getMillis() : frameMillis;
    }

    private static boolean hasPair(HeroElement.HeroText from, HeroElement.HeroText to) {
        return from != null && to != null && to.text() != null && !to.text().isBlank();
    }

    private static Component literal(String preferred, String fallback) {
        String text = preferred == null || preferred.isBlank() ? fallback : preferred;
        return Component.literal(text == null ? "" : text);
    }

    /**
     * 把时间轴重置到"现在"。
     *
     * <p>{@link #start(HeroElement, HeroElement)} 通常发生在点击事件里，之后还要同步重建整棵
     * 控件树（新页面的列表、详情控件等）；这段重建如果耗时几十毫秒，等第一帧真正画出来时
     * 动画已经走掉一截，看起来就是"刚点下去就猛地跳了一下"。在重建结束后调一次本方法，
     * 让动画从重建完成的那一帧重新计时，起点就总是完整的。</p>
     */
    public void rebase() {
        if (active) {
            this.startMillis = Util.getMillis();
            this.frameMillis = this.startMillis;
        }
    }

    /** 中止并清掉端点，用于切页 / 关窗 / 重建布局。 */
    public void cancel() {
        active = false;
        from = null;
        to = null;
        titleText = Component.empty();
        subtitleText = Component.empty();
        albumText = Component.empty();
        metaText = Component.empty();
        subtitleFlying = false;
        albumFlying = false;
        metaFlying = false;
        cachedThumb = null;
    }

    /** 是否仍在飞行。到点后返回 {@code false}。 */
    public boolean isActive() {
        return active && from != null && to != null
                && now() - startMillis < DURATION_MILLIS;
    }

    /** 副标题是否正在飞行（两端都有对应元素）。页面据此决定要不要暂时让出这一段。 */
    public boolean subtitleActive() {
        return isActive() && subtitleFlying;
    }

    /** 专辑行是否正在飞行（两端都有对应元素）。 */
    public boolean albumActive() {
        return isActive() && albumFlying;
    }

    /** 末尾文字是否正在飞行（两端都有对应元素）。 */
    public boolean metaActive() {
        return isActive() && metaFlying;
    }

    /** 飞行图层最后真正显示的那张纹理；落地时直接交给目标页，避免二次查表造成断帧。 */
    public MediaThumbnails.Thumb coverThumb() {
        return cachedThumb;
    }

    /**
     * 其余内容的淡入进度（0..1）。
     *
     * <p>详情页用它把封面/标题之外的信息按进度淡入：飞行前半段画面只有飞行的封面和
     * 标题，后半段副信息逐渐出现，落地时刚好全部到位，观感是「元素展开成页面」而不是
     * 「页面已经好了，东西在上面飘」。</p>
     */
    public float contentReveal() {
        return easeOutCubic(GlassWidgets.clamp01((progress() - CONTENT_DELAY) / CONTENT_SPAN));
    }

    /** 在屏幕最上层画飞行中的封面与标题。 */
    public void draw(GuiGraphics graphics, Font font) {
        if (!isActive()) {
            return;
        }
        float eased = easeOutCubic(progress());
        // 飞行中段让封面轻微放大一点再收回，像被"拎起来"；两端严格为 0，
        // 所以起点与终点几何和页面里的元素完全一致，不会出现落定跳变。
        float lift = (float) Math.sin(Math.PI * eased);

        MediaThumbnails.Thumb thumb = thumbnail();
        if (thumb != null) {
            float fx0 = lerp(eased, from.coverX0(), to.coverX0());
            float fy0 = lerp(eased, from.coverY0(), to.coverY0());
            float fx1 = lerp(eased, from.coverX1(), to.coverX1());
            float fy1 = lerp(eased, from.coverY1(), to.coverY1());

            // 轻微放大：以矩形中心为基准，最多 3.5%。
            float bump = 0.035F * lift;
            float halfWidth = (fx1 - fx0) * 0.5F * (1.0F + bump);
            float halfHeight = (fy1 - fy0) * 0.5F * (1.0F + bump);
            float centerX = (fx0 + fx1) * 0.5F;
            float centerY = (fy0 + fy1) * 0.5F;
            fx0 = centerX - halfWidth;
            fx1 = centerX + halfWidth;
            fy0 = centerY - halfHeight;
            fy1 = centerY + halfHeight;

            // 不加任何阴影：过渡期间封面下方铺一层深色矩形会显得脏，且非正方形封面
            // 留白时阴影会露出一圈黑边。飞行图层保持干净，底部始终是页面本身。
            drawSubpixel(graphics, fx0, fy0, fx1, fy1, thumb);
        }

        // 标题：位置、缩放、颜色一起插值；同样亚像素平移，避免整数取整造成的微抖。
        drawTitle(graphics, font, eased);
        // 副标题 / 末尾文字：两端都有对应元素才飞，落点与各自页面里的位置严格一致。
        if (subtitleFlying) {
            drawTextPair(graphics, font, eased, subtitleText, from.subtitle(), to.subtitle());
        }
        if (albumFlying) {
            drawTextPair(graphics, font, eased, albumText, from.album(), to.album());
        }
        if (metaFlying) {
            drawTextPair(graphics, font, eased, metaText, from.meta(), to.meta());
        }
    }

    /** 标题的位置 / 缩放 / 颜色都取两端标题字段。 */
    private void drawTitle(GuiGraphics graphics, Font font, float eased) {
        float centerX = lerp(eased, from.titleCenterX(), to.titleCenterX());
        float topY = lerp(eased, from.titleTopY(), to.titleTopY());
        float scale = lerp(eased, from.titleScale(), to.titleScale());
        int color = lerpColor(from.titleColor(), to.titleColor(), eased);
        drawCentered(graphics, font, titleText, centerX, topY, color, scale);
    }

    /** 副标题 / 末尾文字的位置 / 缩放 / 颜色取对应的 {@link HeroElement.HeroText} 字段。 */
    private void drawTextPair(GuiGraphics graphics, Font font, float eased, Component text,
                              HeroElement.HeroText fromText, HeroElement.HeroText toText) {
        float centerX = lerp(eased, fromText.centerX(), toText.centerX());
        float topY = lerp(eased, fromText.topY(), toText.topY());
        float scale = lerp(eased, fromText.scale(), toText.scale());
        int color = lerpColor(fromText.color(), toText.color(), eased);
        drawCentered(graphics, font, text, centerX, topY, color, scale);
    }

    private static void drawCentered(GuiGraphics graphics, Font font, Component text,
                                     float centerX, float topY, int color, float scale) {
        int baseX = Math.round(centerX);
        int baseY = Math.round(topY);
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX - baseX, topY - baseY);
        GlassWidgets.centeredText(graphics, font, text, baseX, baseY, color, scale);
        graphics.pose().popMatrix();
    }

    /** 用亚像素平移把有小数坐标的封面画到整数基底上。 */
    private void drawSubpixel(GuiGraphics graphics,
                              float fx0, float fy0, float fx1, float fy1,
                              MediaThumbnails.Thumb thumb) {
        int baseX = (int) Math.floor(fx0);
        int baseY = (int) Math.floor(fy0);
        int width = Math.max(1, (int) Math.ceil(fx1) - baseX);
        int height = Math.max(1, (int) Math.ceil(fy1) - baseY);
        graphics.pose().pushMatrix();
        graphics.pose().translate(fx0 - baseX, fy0 - baseY);
        MediaThumbnails.drawBounded(graphics, thumb, baseX, baseY, baseX + width, baseY + height);
        graphics.pose().popMatrix();
    }

    /**
     * 整段动画只解析一次缩略图。
     *
     * <p>目标端是详情页/头部这类大封面时优先高清纹理；目标端是列表行（24px）时用小图，
     * 免得落地后从大图退回小图。目标端纹理还没就绪就退回起飞端那张——它一定在屏幕上
     * 出现过，能保证飞行期间不空。整段飞行只用一个纹理引用，不会中途换图。</p>
     */
    private MediaThumbnails.Thumb thumbnail() {
        if (cachedThumb != null) {
            return cachedThumb;
        }
        MediaThumbnails thumbnails = MediaThumbnails.getInstance();
        MediaThumbnails.Thumb thumb = resolve(thumbnails, to);
        if (thumb == null) {
            // 目标端纹理还没就绪（首次打开最常见）：用起飞端那张顶上，
            // 落地后由目标页做小图→高清的淡入，飞行图层本身不会中途换图。
            thumb = resolve(thumbnails, from);
        }
        if (thumb != null) {
            cachedThumb = thumb;
        }
        return cachedThumb;
    }

    /**
     * 解析一个端点的纹理。
     *
     * <p>目标端是大封面时优先高清纹理，没就绪再退小图（运行时按真实结果判断，不做假定），
     * 保证飞行期间始终有图可画。真正的档位不需要记录：落地时交接的是纹理引用本身，
     * 目标页拿到哪张就继续画哪张，再自行决定要不要淡入更清晰的版本。</p>
     */
    private static MediaThumbnails.Thumb resolve(MediaThumbnails thumbnails, HeroElement element) {
        String url = element.coverUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        if (prefersLarge(element)) {
            MediaThumbnails.Thumb large = thumbnails.getLarge(url);
            if (large != null) {
                return large;
            }
        }
        return thumbnails.get(url);
    }

    /** 该端点是否按高清大图显示（决定飞行时该优先拿哪一档纹理）。 */
    private static boolean prefersLarge(HeroElement element) {
        return element.coverX1() - element.coverX0() >= LARGE_COVER_MIN_PIXELS;
    }

    /** 当前原始进度（0..1，未缓动）。 */
    private float progress() {
        if (!active) {
            return 1.0F;
        }
        return GlassWidgets.clamp01((now() - startMillis) / (float) DURATION_MILLIS);
    }

    private static float easeOutCubic(float p) {
        float inverse = 1.0F - p;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float lerp(float t, float start, float end) {
        return start + (end - start) * t;
    }

    private static int lerpColor(int from, int to, float t) {
        int a = Math.round(lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF));
        int r = Math.round(lerp(t, (from >>> 16) & 0xFF, (to >>> 16) & 0xFF));
        int g = Math.round(lerp(t, (from >>> 8) & 0xFF, (to >>> 8) & 0xFF));
        int b = Math.round(lerp(t, from & 0xFF, to & 0xFF));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
