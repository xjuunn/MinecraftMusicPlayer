package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.client.gui.animation.HeroElement;
import com.junhsiun.musicplayer.client.gui.animation.SharedElementTransition;
import com.junhsiun.musicplayer.client.gui.glass.theme.FlatTheme;
import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.junhsiun.musicplayer.client.gui.glass.widget.GlassWidgets;
import com.junhsiun.musicplayer.client.gui.icon.SvgIcons;
import com.junhsiun.musicplayer.client.gui.media.CoverFade;
import com.junhsiun.musicplayer.model.LyricLine;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 歌曲详情页的居中视图：封面、歌名、歌手、专辑、时长，下方在「播放」与「歌词」之间切换。
 *
 * <p>本页这首<b>不是</b>当前播放曲目时，下方是一枚居中的播放按钮；一旦播放切到这一首，
 * 按钮整体向上滑出这一块区域、歌词随后淡入上浮，并随播放进度滚动高亮当前一句。两者用
 * 同一个弹簧进度编排，不会出现"按钮突然消失、歌词突然出现"的硬切。</p>
 *
 * <p>不挂任何子控件，全部自绘：底部歌词每帧都可能换行，用控件逐行同步反而更绕。</p>
 */
public final class SongDetailView extends AbstractWidget {

    private static final int COVER_SIZE = 84;
    private static final int CONTENT_MAX_WIDTH = 360;
    private static final int LYRIC_CONTEXT_LINES = 2;

    /** 播放按钮：高度、最小宽度、图标框与文字缩放。 */
    private static final int PLAY_HEIGHT = 26;
    private static final int PLAY_MIN_WIDTH = 104;
    private static final int PLAY_ICON_BOX = 11;
    /** 图标与文字之间的间距、左右内边距。 */
    private static final int PLAY_ICON_GAP = 5;
    private static final int PLAY_BUTTON_PADDING = 13;
    private static final float PLAY_TEXT_SCALE = 0.72F;

    /**
     * 刻录按钮：比播放按钮短一截。
     *
     * <p>播放仍是这一页的主操作，刻录只在手持唱片时作为补充入口出现；高度和最小宽度都
     * 收小，并排在播放按钮右侧，主次一眼能分清。</p>
     */
    private static final int BURN_HEIGHT = 22;
    private static final int BURN_MIN_WIDTH = 76;
    private static final int BURN_ICON_BOX = 10;
    private static final int BURN_ICON_GAP = 5;
    private static final int BURN_BUTTON_PADDING = 11;
    private static final float BURN_TEXT_SCALE = 0.66F;
    /** 两个按钮之间的间距。 */
    private static final int BUTTON_GAP = 8;

    /** 底部区域「按钮 ⇄ 歌词」过渡的时间常数（秒）。越小越快，0.09 大约 150ms 收敛到位。 */
    private static final float LYRIC_REVEAL_TAU = 0.09F;

    private final SongDetailPage page;

    /**
     * 正在进行的共享元素过渡；非空时本视图暂时让出封面与标题，交给屏幕最上层的
     * {@link SharedElementTransition} 绘制，避免两份元素重影。
     */
    private SharedElementTransition hero;

    /** 详情封面从小图切到高清大图时的平滑衔接。 */
    private final CoverFade coverFade = new CoverFade();
    /** 上一帧共享元素是否还在飞行：用它在落地那一帧把封面状态交接给本视图。 */
    private boolean heroWasActive;

    /**
     * 底部区域的状态进度：0 = 播放按钮，1 = 歌词。
     *
     * <p>用弹簧而不是线性时间轴：切歌是"播放状态的一次跳变"，弹簧的运动尾巴更自然，
     * 而且中途再次切走时可以从当前位置继续，不会重头播一遍。</p>
     */
    private final GlassWidgets.Spring lyricsReveal = new GlassWidgets.Spring();
    /** 底部区域的淡入进度：详情加载完成后从 0 收到 1，避免按钮/歌词"啪"地出现。 */
    private final GlassWidgets.Spring bottomPresence = new GlassWidgets.Spring();
    /** 播放按钮的悬停提亮与按压回弹。 */
    private final GlassWidgets.Spring playHover = new GlassWidgets.Spring();
    private final GlassWidgets.Spring playPress = new GlassWidgets.Spring();
    /** 刻录按钮的悬停提亮与按压回弹。 */
    private final GlassWidgets.Spring burnHover = new GlassWidgets.Spring();
    private final GlassWidgets.Spring burnPress = new GlassWidgets.Spring();
    /** 鼠标是否按在播放按钮上（松手时才真正点播）。 */
    private boolean playHeld;
    /** 鼠标是否按在刻录按钮上。 */
    private boolean burnHeld;
    /**
     * 上次还在播放时的歌词行下标。
     *
     * <p>播放状态刚结束的那一小段里，{@link SongDetailPage#currentLyricIndex()} 已经返回
     * {@code -1}；如果直接用它，正在淡出的歌词会瞬间跳回第一句。这里冻结最后一帧的下标，
     * 淡出过程中歌词保持原样，观感才是"同一块内容退场"。</p>
     */
    private int lastLyricIndex;
    /**
     * 本页这首是否曾经是当前播放曲目。
     *
     * <p>「正在播放」标签不能直接跟着 {@link SongDetailPage#playingThis()} 立刻消失：切歌时
     * 歌词整块还在淡出，标签却会凭空断开并把下面的歌词上提 12px。一旦出现过就让它常驻，
     * 跟着整块歌词一起淡出即可。</p>
     */
    private boolean wasPlayingThis;
    /** 播放按钮上一帧的实际矩形，供命中测试使用（每帧绘制时更新）。 */
    private int playButtonX0;
    private int playButtonY0 = -1;
    private int playButtonX1;
    private int playButtonY1 = -1;
    /** 刻录按钮上一帧的实际矩形。 */
    private int burnButtonX0;
    private int burnButtonY0 = -1;
    private int burnButtonX1;
    private int burnButtonY1 = -1;
    /** 这一帧底部是否稳定处于「按钮」状态：按钮正在离场/已经换成歌词时为 {@code false}。 */
    private boolean playButtonClickable;
    /** 这一帧刻录按钮是否可见且可点。 */
    private boolean burnButtonClickable;

    public SongDetailView(SongDetailPage page, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.page = page;
        // 进入页面时这首歌可能已经在播：直接落到歌词状态，不要再淡入一遍。
        this.lyricsReveal.snap(page.playingThis() ? 1.0F : 0.0F);
    }

    /** 由外壳在启动过渡后注入。 */
    public void setHero(SharedElementTransition hero) {
        this.hero = hero;
    }

    /**
     * 本页封面与标题的目标几何（动画终点）。
     *
     * <p>几何与 {@link #extractWidgetRenderState} 里的绘制算式必须保持一致，否则落定
     * 那一帧会出现"飞行元素跳到旁边"的错位。标题用与绘制相同的 {@link #fit} 结果，
     * 过渡文字从起点到终点始终是同一串，不会在落地时突然变完整或变截断。</p>
     */
    public HeroElement heroTarget() {
        Font font = Minecraft.getInstance().font;
        int centerX = getX() + width / 2;
        int contentWidth = Math.max(80, Math.min(width - 32, CONTENT_MAX_WIDTH));
        int coverTop = getY() + 8;
        int titleTop = coverTop + COVER_SIZE + 12;
        // 与 extractWidgetRenderState 完全相同的纵向排布：标题 → 歌手 → 专辑（始终占位）→ 时长。
        // 专辑那一行即使暂时为空也占位，否则详情接口晚到会让时长行整体下移，共享元素落地错位。
        int top = titleTop + Math.round(font.lineHeight * 1.0F) + 4;
        HeroElement.HeroText subtitle = null;
        String artist = page.artistText();
        if (!artist.isBlank()) {
            subtitle = new HeroElement.HeroText(
                    fit(font, artist, contentWidth, 0.72F),
                    centerX, top, 0.72F, FlatTheme.TEXT_SECONDARY);
        }
        top += Math.round(font.lineHeight * 0.72F) + 2;

        HeroElement.HeroText album = null;
        String albumName = page.albumText();
        if (!albumName.isBlank()) {
            album = new HeroElement.HeroText(
                    fit(font, albumName, contentWidth, 0.60F),
                    centerX, top, 0.60F, FlatTheme.TEXT_TERTIARY);
        }
        top += Math.round(font.lineHeight * 0.60F) + 2;

        HeroElement.HeroText meta = null;
        String duration = page.durationText();
        if (!duration.isBlank()) {
            meta = new HeroElement.HeroText(
                    duration, centerX, top, 0.60F, FlatTheme.TEXT_FAINT);
        }
        return new HeroElement(
                centerX - COVER_SIZE / 2, coverTop,
                centerX + COVER_SIZE / 2, coverTop + COVER_SIZE,
                centerX, titleTop, 1.0F, FlatTheme.TEXT,
                fit(font, page.titleText(), contentWidth, 1.0F), page.coverUrl(), page.id(),
                subtitle, album, meta);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int centerX = getX() + width / 2;
        int contentWidth = Math.max(80, Math.min(width - 32, CONTENT_MAX_WIDTH));
        int textLeft = centerX - contentWidth / 2;

        // 每帧先假设按钮不可点：只有真的画出按钮、且过渡没开始时才在 drawBottom 里打开。
        // 这样加载中 / 出错 / 已切到歌词时，上一帧留下的矩形绝不会被误用。
        playButtonClickable = false;
        burnButtonClickable = false;

        // 共享元素飞行期间：封面和标题由最上层 overlay 独家绘制，本视图不画（否则重影）；
        // 歌手与时长如果有对应元素，也一起交给 overlay；其余信息按 contentReveal() 延迟淡入。
        boolean flying = hero != null && hero.isActive();
        // 飞行结束的那一帧：把"最后一帧用的是小图还是大图"交给封面淡入状态，
        // 大图已经在大屏上出现过就不要再淡入一遍。
        if (heroWasActive && !flying && hero != null) {
            coverFade.handoff(hero.coverThumb());
        }
        heroWasActive = flying;
        float reveal = flying ? hero.contentReveal() : 1.0F;
        boolean hideSubtitle = flying && hero.subtitleActive();
        boolean hideAlbum = flying && hero.albumActive();
        boolean hideMeta = flying && hero.metaActive();

        // 底部「播放按钮 ⇄ 歌词」的过渡每帧推进一次，不受下面任何早退分支影响：
        // 即使封面还在飞行、详情还在加载，弹簧也照常向目标收敛，不会在可见时"从头开始"。
        lyricsReveal.tick(page.playingThis(), LYRIC_REVEAL_TAU);

        // 底部区域的入场位移同样每帧推进一次。只有"这一帧真的会画出底部"时才往前走；
        // 否则直接归零并清掉计时——弹簧是按真实时间积分的，隔了很久没推进再开始，
        // 第一帧会拿到被截断的 0.1s，一帧冲掉大半进度，看起来就是按钮闪了一下。
        boolean bottomReady = !flying && !page.loading() && page.status().getString().isBlank();
        if (bottomReady) {
            bottomPresence.tick(true, 0.07F);
        } else {
            bottomPresence.snap(0.0F);
            // 底部这一帧根本不画：顺手把两个交互弹簧也归零并清掉计时，
            // 免得它们带着很久以前的 lastMillis 在下次出现时一帧冲到目标。
            playHover.snap(0.0F);
            playPress.snap(0.0F);
            playHeld = false;
            burnHover.snap(0.0F);
            burnPress.snap(0.0F);
            burnHeld = false;
        }

        // 底部要在同一条竖直基准线上切换按钮与歌词，所以信息块顶部固定对齐。
        int top = getY() + 8;
        String coverUrl = page.coverUrl();
        if (!flying) {
            drawCover(graphics, centerX - COVER_SIZE / 2, top, coverUrl);
        }
        top += COVER_SIZE + 12;

        // 标题始终画（加载中也用列表带过来的兜底标题），这样动画结束交还给本视图时
        // 标题一定在；飞行期间则跳过，由 overlay 负责。
        Component title = Component.literal(fit(font, page.titleText(), contentWidth, 1.0F));
        int titleY = top;
        top += Math.round(font.lineHeight * 1.0F) + 4;
        if (!flying) {
            GlassWidgets.centeredText(graphics, font, title, centerX, titleY,
                    FlatTheme.TEXT, 1.0F);
        }

        boolean showContent = reveal > 0.01F;
        String artist = page.artistText();
        if (showContent && !artist.isBlank() && !hideSubtitle) {
            GlassWidgets.centeredText(graphics, font,
                    Component.literal(fit(font, artist, contentWidth, 0.72F)),
                    centerX, top, fade(FlatTheme.TEXT_SECONDARY, reveal), 0.72F);
        }
        top += Math.round(font.lineHeight * 0.72F) + 2;

        String album = page.albumText();
        if (showContent && !album.isBlank() && !hideAlbum) {
            GlassWidgets.centeredText(graphics, font,
                    Component.literal(fit(font, album, contentWidth, 0.60F)),
                    centerX, top, fade(FlatTheme.TEXT_TERTIARY, reveal), 0.60F);
        }
        // 专辑行始终占位：即使暂时为空（详情还没回来）也留出这一行，
        // 保证下方时长行不会因为数据晚到而整体下移、共享元素落地错位。
        top += Math.round(font.lineHeight * 0.60F) + 2;

        String duration = page.durationText();
        if (showContent && !duration.isBlank() && !hideMeta) {
            GlassWidgets.centeredText(graphics, font,
                    Component.literal(duration), centerX, top,
                    fade(FlatTheme.TEXT_FAINT, reveal), 0.60F);
        }
        top += Math.round(font.lineHeight * 0.60F) + 2;

        if (page.loading()) {
            if (showContent) {
                GlassWidgets.centeredText(graphics, font,
                        Component.translatable("musicplayer.browser.loading"),
                        centerX, top + 10, fade(FlatTheme.TEXT_TERTIARY, reveal), 0.62F);
            }
            return;
        }
        if (!page.status().getString().isBlank()) {
            if (showContent) {
                GlassWidgets.centeredText(graphics, font, page.status(),
                        centerX, top + 10, fade(FlatTheme.TEXT_TERTIARY, reveal), 0.62F);
            }
            return;
        }

        int dividerY = top + 4;
        if (showContent) {
            FlatTheme.fill(graphics, textLeft, dividerY, textLeft + contentWidth, dividerY + 1,
                    0.5F, FlatTheme.alpha(0xFFFFFF, 0.10F * reveal));
        }

        // 共享元素还在飞时不画底部：播放按钮/歌词不属于"展开成页面"的形变元素，
        // 让它等在飞行结束后整体从下方滑入，比在淡入的页面里抖出一块玻璃干净得多。
        if (!flying) {
            drawBottom(graphics, font, centerX, dividerY + 12, mouseX, mouseY);
        }
    }

    /**
     * 底部区域：播放按钮与歌词的过渡。
     *
     * <p>进度 {@code t = 0} 时只有按钮、{@code t = 1} 时只有歌词。退场顺序是「按钮先走」：
     * 按钮在前 55% 的进度里整体向上滑出这一块裁剪区；歌词等 t 过 0.30 之后才淡入，并从
     * 下方 8px 浮上来。两者的动作错开，不会出现两串文字在同一位置叠着发糊。</p>
     *
     * <p><b>为什么按钮只做位移、从不淡入淡出：</b>玻璃底的不透明度是编译期常量（见
     * {@code GlassPipelines#panel} 的 {@code GLASS_OPACITY}），逐帧改透明度只能靠叠加层，
     * 做不到真正淡出，还会在"近似填充"和"真玻璃"之间硬切一帧。所以按钮全程保持同一份材质、
     * 同一个矩形，只用裁剪把上下边界收干净：登场是从下方滑入、退场是向上滑出。纯几何的
     * 位移不会改变任何一个像素的取整结果，也就没有可抖的东西。</p>
     */
    private void drawBottom(GuiGraphicsExtractor graphics, Font font, int centerX, int top, int mouseX, int mouseY) {
        // 入场进度由 extractWidgetRenderState 每帧推进，这里只读值。
        float presence = GlassWidgets.clamp01(bottomPresence.eased());

        float t = GlassWidgets.clamp01(lyricsReveal.eased());

        // 底部区域的裁剪：按钮滑入/滑出、歌词换行后的最后一行，都由这一道边界收拾。
        // 裁剪区退化（这一块根本放不下）时直接不画：beginClip 对退化矩形只是压一个空标记，
        // 不会真的开剪刀，元素会不受约束地画到窗口别处去。
        int clipLeft = getX() + 2;
        int clipRight = getX() + width - 2;
        int clipTop = top - 2;
        int clipBottom = getY() + height - 2;
        if (clipRight - clipLeft < 14 || clipBottom - clipTop < 14) {
            return;
        }
        // 按钮只有在「已经完全落回原位、且本页这首确实没在播」时才可点：入场/退场途中它
        // 一直在滑动，旧矩形会盖到上面几行，接受点击就会点在看不见的东西上。
        // 阈值取 0.02 而不是 0：弹簧只是渐近收敛，永远差一点到不了 0。
        playButtonClickable = presence >= 0.98F && t <= 0.02F && !page.playingThis();
        boolean showBurn = page.canBurnThis();
        burnButtonClickable = playButtonClickable && showBurn;
        if (!showBurn) {
            burnHover.snap(0.0F);
            burnPress.snap(0.0F);
            burnHeld = false;
        }
        GlassWidgets.beginClip(graphics, clipLeft, clipTop, clipRight, clipBottom);
        try {
            if (t < 0.62F) {
                // 按钮的位移全部沿竖直方向，由裁剪区收边：
                //   · 入场：presence 0 → 1，按钮从下方 (top + PLAY_HEIGHT + 12) 升到 top，
                //     像被这一块内容顶上来；
                //   · 退场：t 0 → 0.55，从 top 继续向上升出上边界。
                // 两者用同一个高度基准，所以入场走完正好接上退场的起点，不会有位置跳变。
                float exit = GlassWidgets.clamp01(t / 0.55F);
                float dy = (PLAY_HEIGHT + 12) * ((1.0F - presence) - exit);
                int buttonTop = top + Math.round(dy);
                // 播放仍是主按钮；刻录只在手持唱片时作为副按钮排在右侧。
                // 两个按钮作为一个整体居中，避免只居中播放按钮时副按钮把整组视觉中心拖偏。
                int playWidth = playButtonWidth(font);
                int burnWidth = showBurn ? burnButtonWidth(font) : 0;
                int groupWidth = playWidth + (showBurn ? BUTTON_GAP + burnWidth : 0);
                int groupLeft = centerX - groupWidth / 2;
                drawPlayButton(graphics, font, groupLeft + playWidth / 2, buttonTop, mouseX, mouseY);
                if (showBurn) {
                    int burnCenter = groupLeft + playWidth + BUTTON_GAP + burnWidth / 2;
                    drawBurnButton(graphics, font, burnCenter,
                            buttonTop + (PLAY_HEIGHT - BURN_HEIGHT) / 2, mouseX, mouseY);
                }
            } else {
                // 按钮已经不在场上：把交互弹簧归零，避免它们带着"很久以前的计时"
                // 在下次出现时一帧跳到位。按住状态也一并清掉。
                playHover.snap(0.0F);
                playPress.snap(0.0F);
                playHeld = false;
                burnHover.snap(0.0F);
                burnPress.snap(0.0F);
                burnHeld = false;
            }
            if (t > 0.30F) {
                // 歌词等按钮走得差不多才登场，避免两串文字在同一位置叠着发糊。
                // 歌词不是玻璃，可以正常淡入，所以登场时也一起乘上 presence。
                float lyrics = GlassWidgets.clamp01((t - 0.30F) / 0.70F);
                int rise = Math.round(8.0F * (1.0F - lyrics));
                graphics.pose().pushMatrix();
                graphics.pose().translate(0.0F, rise);
                drawLyrics(graphics, font, centerX, top, lyrics * presence);
                graphics.pose().popMatrix();
            }
        } finally {
            GlassWidgets.endClip(graphics);
        }
    }

    /** 播放按钮实际宽度：文字与图标放不下时取最小宽度。 */
    private static int playButtonWidth(Font font) {
        String label = Component.translatable("musicplayer.song.play").getString();
        int textWidth = Math.round(font.width(label) * PLAY_TEXT_SCALE);
        int contentWidth = PLAY_ICON_BOX + PLAY_ICON_GAP + textWidth;
        return Math.max(PLAY_MIN_WIDTH, contentWidth + PLAY_BUTTON_PADDING * 2);
    }

    /** 刻录按钮实际宽度：刻意比播放按钮短，保持主次关系。 */
    private static int burnButtonWidth(Font font) {
        String label = Component.translatable("musicplayer.discmaster.burn").getString();
        int textWidth = Math.round(font.width(label) * BURN_TEXT_SCALE);
        int contentWidth = BURN_ICON_BOX + BURN_ICON_GAP + textWidth;
        return Math.max(BURN_MIN_WIDTH, contentWidth + BURN_BUTTON_PADDING * 2);
    }

    /**
     * 居中的播放按钮：液态玻璃胶囊 + 播放图标 + “播放”字样。
     *
     * <p>悬停提亮、按下回弹都走统一的弹簧，和其他玻璃按钮手感一致。</p>
     *
     * <p>这里<b>没有透明度参数</b>：按钮要么整块可见、要么被 {@link #drawBottom} 的裁剪
     * 移出视野，从不做半透明淡入淡出。玻璃底的不透明度是编译期常量，硬做淡出只能在
     * "近似填充"和"真玻璃"之间来回切材质，反而会闪。</p>
     */
    private void drawPlayButton(GuiGraphicsExtractor graphics, Font font, int centerX, int top, int mouseX, int mouseY) {
        String label = Component.translatable("musicplayer.song.play").getString();
        int textWidth = Math.round(font.width(label) * PLAY_TEXT_SCALE);
        int contentWidth = PLAY_ICON_BOX + PLAY_ICON_GAP + textWidth;
        int buttonWidth = playButtonWidth(font);

        boolean hovered = isMouseOver(mouseX, mouseY)
                && overPlayButton(mouseX, mouseY);
        playHover.tick(hovered, GlassTheme.Motion.HOVER);
        playPress.tickSpring(playHeld, playHeld ? GlassTheme.Motion.PRESS : GlassTheme.Motion.RELEASE);
        float lift = playHover.eased();
        float down = GlassWidgets.clamp01(playPress.value());

        // 基准矩形固定不变，只有按下的回弹做对称内缩。
        //
        // 这里必须"两边各缩同样的量"，不能用 {@code round(buttonWidth * scale)} 反推宽度：
        // 宽度每变化 1，{@code centerX - w / 2} 的整数除法就会把左边界在相邻两个像素之间
        // 来回挪——只要尺寸还在逐帧变化，按钮就会左右抖个不停。对称内缩时右边界也跟着走
        // 同样的量，中心严格不动，也就没有可以抖的方向。
        int baseLeft = centerX - buttonWidth / 2;
        int baseRight = baseLeft + buttonWidth;
        int insetX = Math.round((baseRight - baseLeft) * 0.015F * down);
        int insetY = Math.round(PLAY_HEIGHT * 0.015F * down);
        int x0 = baseLeft + insetX;
        int x1 = Math.max(x0 + 1, baseRight - insetX);
        int y0 = top + insetY;
        int y1 = Math.max(y0 + 1, top + PLAY_HEIGHT - insetY);
        int h = y1 - y0;
        float radius = h / 2.0F;
        playButtonX0 = x0;
        playButtonY0 = y0;
        playButtonX1 = x1;
        playButtonY1 = y1;

        if (!GlassWidgets.glassBackground(graphics, x0, y0, x1, y1,
                GlassTheme.BUTTON, lift, down)) {
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, 0xFFFFFF, 0xFFFFFF,
                    radius, 0.16F + 0.05F * lift, 1.0F);
        }
        // 主题色薄薄一层：浅色玻璃上也能一眼看出这是可点的东西。
        GlassWidgets.roundedFill(graphics, x0, y0, x1, y1,
                FlatTheme.accentRgb(), FlatTheme.accentRgb(),
                radius, 0.22F + 0.08F * lift, 1.0F);

        // 图标与文字始终以按钮当前的几何中心对齐，按下回弹时整体等比收一点。
        float innerScale = 1.0F - 0.03F * down;
        int center = (x0 + x1) / 2;
        int iconSize = Math.max(6, Math.round(PLAY_ICON_BOX * innerScale));
        int contentLeft = center - Math.round(contentWidth * innerScale / 2.0F);
        int iconTop = y0 + Math.round((h - iconSize) / 2.0F);
        SvgIcons.PLAY.drawCentered(graphics, contentLeft, iconTop, iconSize, iconSize, FlatTheme.TEXT);

        float textScale = PLAY_TEXT_SCALE * innerScale;
        int textHeight = Math.round(font.lineHeight * textScale);
        int textLeft = contentLeft + Math.round((PLAY_ICON_BOX + PLAY_ICON_GAP) * innerScale);
        int textY = y0 + Math.round((h - textHeight) / 2.0F);
        GlassWidgets.text(graphics, font, Component.literal(label),
                textLeft, textY, FlatTheme.TEXT, textScale);
    }

    /**
     * 播放按钮右侧的刻录按钮。
     *
     * <p>与播放按钮共用同一套玻璃材质与弹簧手感，但高度和最小宽度都更小，也不再铺主题色层：
     * 它是「手持唱片时顺手刻一张」的补充入口，不该抢走播放按钮的视觉重量。</p>
     */
    private void drawBurnButton(GuiGraphicsExtractor graphics, Font font, int centerX, int top, int mouseX, int mouseY) {
        String label = Component.translatable("musicplayer.discmaster.burn").getString();
        int textWidth = Math.round(font.width(label) * BURN_TEXT_SCALE);
        int contentWidth = BURN_ICON_BOX + BURN_ICON_GAP + textWidth;
        int buttonWidth = burnButtonWidth(font);

        boolean hovered = isMouseOver(mouseX, mouseY)
                && overBurnButton(mouseX, mouseY);
        burnHover.tick(hovered, GlassTheme.Motion.HOVER);
        burnPress.tickSpring(burnHeld, burnHeld ? GlassTheme.Motion.PRESS : GlassTheme.Motion.RELEASE);
        float lift = burnHover.eased();
        float down = GlassWidgets.clamp01(burnPress.value());

        int baseLeft = centerX - buttonWidth / 2;
        int baseRight = baseLeft + buttonWidth;
        int insetX = Math.round((baseRight - baseLeft) * 0.015F * down);
        int insetY = Math.round(BURN_HEIGHT * 0.015F * down);
        int x0 = baseLeft + insetX;
        int x1 = Math.max(x0 + 1, baseRight - insetX);
        int y0 = top + insetY;
        int y1 = Math.max(y0 + 1, top + BURN_HEIGHT - insetY);
        int h = y1 - y0;
        float radius = h / 2.0F;
        burnButtonX0 = x0;
        burnButtonY0 = y0;
        burnButtonX1 = x1;
        burnButtonY1 = y1;

        if (!GlassWidgets.glassBackground(graphics, x0, y0, x1, y1,
                GlassTheme.BUTTON, lift, down)) {
            GlassWidgets.roundedFill(graphics, x0, y0, x1, y1, 0xFFFFFF, 0xFFFFFF,
                    radius, 0.12F + 0.04F * lift, 1.0F);
        }

        float innerScale = 1.0F - 0.03F * down;
        int center = (x0 + x1) / 2;
        int iconSize = Math.max(6, Math.round(BURN_ICON_BOX * innerScale));
        int contentLeft = center - Math.round(contentWidth * innerScale / 2.0F);
        int iconTop = y0 + Math.round((h - iconSize) / 2.0F);
        SvgIcons.DISC.drawCentered(graphics, contentLeft, iconTop, iconSize, iconSize, FlatTheme.TEXT);

        float textScale = BURN_TEXT_SCALE * innerScale;
        int textHeight = Math.round(font.lineHeight * textScale);
        int textLeft = contentLeft + Math.round((BURN_ICON_BOX + BURN_ICON_GAP) * innerScale);
        int textY = y0 + Math.round((h - textHeight) / 2.0F);
        GlassWidgets.text(graphics, font, Component.literal(label),
                textLeft, textY, FlatTheme.TEXT_SECONDARY, textScale);
    }

    /**
     * 指针是否落在播放按钮的当前矩形里。
     *
     * <p>只有底部确实稳定处于「按钮」状态时才承认命中（见 {@link #playButtonClickable}）：
     * 过渡已经开始、或者本页这首已经在播时，上一帧的矩形可能还盖在专辑 / 时长那几行上。</p>
     */
    private boolean overPlayButton(double mouseX, double mouseY) {
        if (!playButtonClickable) {
            return false;
        }
        return mouseX >= playButtonX0 && mouseX <= playButtonX1
                && mouseY >= playButtonY0 && mouseY <= playButtonY1;
    }

    /** 指针是否落在刻录按钮的当前矩形里，且手持物仍然满足刻录条件。 */
    private boolean overBurnButton(double mouseX, double mouseY) {
        if (!burnButtonClickable) {
            return false;
        }
        return mouseX >= burnButtonX0 && mouseX <= burnButtonX1
                && mouseY >= burnButtonY0 && mouseY <= burnButtonY1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        // 本控件铺满整块详情区域，不能直接把点击交给基类：那会让点空白处也响一下。
        // 只有真的点在按钮上才接受这次点击。
        if (!isActive() || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (overBurnButton(event.x(), event.y())) {
            burnHeld = true;
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        if (!overPlayButton(event.x(), event.y())) {
            return false;
        }
        playHeld = true;
        // 按下即播放反馈声，和其他玻璃按钮一致。
        playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (burnHeld) {
            burnHeld = false;
            if (overBurnButton(event.x(), event.y())) {
                page.burnThis();
            }
            return true;
        }
        if (!playHeld) {
            return false;
        }
        playHeld = false;
        if (overPlayButton(event.x(), event.y())) {
            page.playThis();
        }
        return true;
    }

    private void drawLyrics(GuiGraphicsExtractor graphics, Font font, int centerX, int top,
                            float reveal) {
        if (reveal <= 0.01F) {
            return;
        }
        boolean playing = page.playingThis();
        if (playing) {
            wasPlayingThis = true;
        }
        // 「正在播放」标签一旦出现过就一直留在歌词块里：页面属于固定的一首歌，标签跟着整块
        // 歌词淡出即可。若按 playingThis() 立刻撤掉，切歌瞬间歌词还几乎不透明，标签却会
        // 凭空消失，并把下面的歌词整整上提 12px——一次可见的跳动。
        if (wasPlayingThis) {
            GlassWidgets.centeredText(graphics, font,
                    Component.translatable("musicplayer.song.now_playing"),
                    centerX, top, fade(FlatTheme.accent(), reveal), 0.58F);
            top += 12;
        }

        if (page.lyricsLoading()) {
            GlassWidgets.centeredText(graphics, font,
                    Component.translatable("musicplayer.song.lyrics_loading"),
                    centerX, top, fade(FlatTheme.TEXT_TERTIARY, reveal), 0.62F);
            return;
        }
        if (page.lyrics().isEmpty()) {
            GlassWidgets.centeredText(graphics, font,
                    Component.translatable("musicplayer.song.lyrics_empty"),
                    centerX, top, fade(FlatTheme.TEXT_TERTIARY, reveal), 0.62F);
            return;
        }

        int lyricWidth = Math.max(80, Math.min(width - 40, CONTENT_MAX_WIDTH));
        int current = page.currentLyricIndex();
        if (current >= 0) {
            lastLyricIndex = current;
        }
        if (current < 0) {
            // 正在淡出 / 还没到第一句：沿用最后一句有效下标，避免歌词整体跳回第一句；
            // 从未播过时从第一句开始展示。
            current = Math.max(0, Math.min(lastLyricIndex, page.lyrics().size() - 1));
        }

        // 只画当前句与上下文，焦点始终跟着进度走；静态展示（刚打开未播放）也走同一条路径。
        int first = Math.max(0, current - LYRIC_CONTEXT_LINES);
        int last = Math.min(page.lyrics().size() - 1, current + LYRIC_CONTEXT_LINES);
        int bottom = getY() + height - 4;
        for (int index = first; index <= last; index++) {
            LyricLine line = page.lyrics().get(index);
            if (index == current) {
                float scale = 0.82F;
                if (top + Math.round(font.lineHeight * scale) > bottom) {
                    break;
                }
                GlassWidgets.centeredText(graphics, font,
                        Component.literal(fit(font, line.text(), lyricWidth, scale)),
                        centerX, top, fade(FlatTheme.TEXT, reveal), scale);
                top += Math.round(font.lineHeight * scale) + 7;
            } else {
                float scale = 0.62F;
                if (top + Math.round(font.lineHeight * scale) > bottom) {
                    break;
                }
                int color = index < current ? FlatTheme.TEXT_FAINT : FlatTheme.TEXT_TERTIARY;
                GlassWidgets.centeredText(graphics, font,
                        Component.literal(fit(font, line.text(), lyricWidth, scale)),
                        centerX, top, fade(color, reveal), scale);
                top += Math.round(font.lineHeight * scale) + 5;
            }
        }
    }

    /** 在保留 RGB 的同时，把原有 alpha 再乘一个 0..1 的淡入系数。 */
    private static int fade(int argb, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        int alpha = Math.round(((argb >>> 24) & 0xFF) * clamped);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private void drawCover(GuiGraphicsExtractor graphics, int x, int y, String url) {
        if (coverFade.draw(graphics, url, x, y, x + COVER_SIZE, y + COVER_SIZE)) {
            return;
        }
        float radius = COVER_SIZE * 0.18F;
        GlassWidgets.roundedFill(graphics, x, y, x + COVER_SIZE, y + COVER_SIZE,
                0xFFFFFF, 0xFFFFFF, radius, 0.12F, 1.0F);
        int inset = COVER_SIZE / 4;
        GlassWidgets.roundedFill(graphics, x + inset, y + inset,
                x + COVER_SIZE - inset, y + COVER_SIZE - inset,
                0xFFFFFF, 0xFFFFFF, Math.max(1.0F, radius - 2.0F), 0.09F, 1.0F);
    }

    /** 按可用宽度收窄文字，必要时截断并补省略号。 */
    private static String fit(Font font, String text, int maxWidth, float scale) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int available = Math.max(1, Math.round(maxWidth / Math.max(0.05F, scale)));
        if (font.width(text) <= available) {
            return text;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, available - font.width(ellipsis))) + ellipsis;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}