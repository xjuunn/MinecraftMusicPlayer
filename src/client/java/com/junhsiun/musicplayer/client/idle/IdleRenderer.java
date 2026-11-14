package com.junhsiun.musicplayer.client.idle;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.client.gui.glass.GlassPipelines;
import com.junhsiun.musicplayer.client.gui.media.MediaThumbnails;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.model.LyricLine;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.Util;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalInt;

/**
 * 挂机「纯享」模式的渲染层。
 *
 * <p>由 {@code GameRendererMixin} 在 {@code GuiRenderer.render()} 之前调用：
 * 此时主渲染目标里正是本帧的世界、HUD 尚未绘制。流程分三步：</p>
 * <ol>
 *   <li>把主画面降采样到 1/2 分辨率的离屏贴图；</li>
 *   <li>用一个全屏 {@code RenderPass} 把特效着色器的结果写回主渲染目标；</li>
 *   <li>往 {@code GuiRenderState} 里追加歌词与曲目文字，交给原版界面渲染器绘制。</li>
 * </ol>
 *
 * <p>所有 GPU 资源懒创建、按窗口尺寸重建；任何异常都会永久降级为“只显示文字”，
 * 不会影响正常游戏画面。</p>
 */
public final class IdleRenderer {

    private static final int WORLD_DIVISOR = 2;
    /** {@code IdleParams} 是 4 个 vec4，正好 64 字节。 */
    private static final int PARAM_BUFFER_SIZE = 64;
    /** 参数缓冲的帧数：GPU 可能还在读上一帧，逐帧轮换避免写坏正在使用的数据。 */
    private static final int PARAM_BUFFER_COUNT = 3;
    private static final long HINT_VISIBLE_MILLIS = 9000L;

    private static final IdleRenderer INSTANCE = new IdleRenderer();

    private GpuTexture worldTexture;
    private GpuTextureView worldView;
    private GpuBuffer quadBuffer;
    private final GpuBuffer[] paramBuffers = new GpuBuffer[PARAM_BUFFER_COUNT];
    private int paramCursor;
    private final ByteBuffer paramData = BufferUtils.createByteBuffer(PARAM_BUFFER_SIZE);
    private int worldWidth;
    private int worldHeight;
    private boolean failed;
    /** 特效时间的原点：避免用「一天中的毫秒」在整点回绕时让动画跳一下。 */
    private long timeOriginMillis = Util.getMillis();

    private IdleRenderer() {
    }

    public static IdleRenderer getInstance() {
        return INSTANCE;
    }

    /** 每帧在界面绘制之前调用一次。 */
    public static void beforeGui(GuiRenderState renderState) {
        IdleController controller = IdleController.getInstance();
        long now = Util.getMillis();
        float fade = controller.fade(now);
        if (fade <= 0.001F || renderState == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        // 界面/覆盖层打开时不出特效：此时画面主体是界面，全屏着色会把界面压花。
        // 顺便把状态收掉——单机暂停时客户端 tick 不跑，不在这里收的话关掉菜单的
        // 第一帧会把特效整块弹回来。
        if (minecraft.screen != null || minecraft.getOverlay() != null) {
            controller.cancelImmediate();
            return;
        }
        INSTANCE.drawEffect(minecraft, now, fade);
        try {
            INSTANCE.drawOverlay(minecraft, renderState, now, fade);
        } catch (Throwable throwable) {
            MusicPlayerMod.LOGGER.warn("纯享文字叠加失败", throwable);
        }
    }

    /** 释放离屏贴图与缓冲区（退世界 / 关客户端）。 */
    public void reset() {
        closeWorld();
        if (quadBuffer != null) {
            quadBuffer.close();
            quadBuffer = null;
        }
        for (int i = 0; i < paramBuffers.length; i++) {
            if (paramBuffers[i] != null) {
                paramBuffers[i].close();
                paramBuffers[i] = null;
            }
        }
        paramCursor = 0;
        failed = false;
        timeOriginMillis = Util.getMillis();
    }

    // ── 全屏特效 ────────────────────────────────────────────────────────

    private void drawEffect(Minecraft minecraft, long now, float fade) {
        if (failed) {
            return;
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        try {
            ensureResources(main.width, main.height);
            MusicPlayerConfig.IdleSettings config = MusicPlayerConfigManager.get().idle;
            IdleEffect effect = IdleEffect.fromConfig();

            MediaThumbnails.Thumb cover = null;
            String coverUrl = IdleNowPlaying.coverUrl();
            if (coverUrl != null && !coverUrl.isBlank()) {
                cover = MediaThumbnails.getInstance().getLarge(coverUrl);
            }
            // 缩略图缓存可能在退世界时被清空，纹理视图一旦关闭就不能再绑定。
            boolean hasCover = cover != null && cover.view() != null && !cover.view().isClosed();

            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(
                    com.mojang.blaze3d.textures.FilterMode.LINEAR);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            // 1. 世界降采样：复用液态玻璃的模糊管线，spread = 0 等价于一次双线性采样。
            RenderPass downsample = encoder.createRenderPass(
                    () -> "musicplayer idle world",
                    worldView, OptionalInt.empty());
            try {
                downsample.setPipeline(GlassPipelines.blur(0.0F, false));
                downsample.bindTexture("Sampler0", main.getColorTextureView(), linear);
                downsample.setVertexBuffer(0, quadBuffer);
                downsample.draw(6, 1);
            } finally {
                downsample.close();
            }

            // 2. 逐帧参数
            float coverAspect = hasCover ? cover.aspect() : 1.0F;
            GpuBufferSlice paramSlice = writeParams(encoder, main.width, main.height, now, fade,
                    config, effect, coverAspect);

            // 3. 特效写回主渲染目标。这里只读取 worldView，不会与主目标形成采样反馈。
            RenderPass pass = encoder.createRenderPass(
                    () -> "musicplayer idle effect",
                    main.getColorTextureView(), OptionalInt.empty());
            try {
                pass.setPipeline(IdlePipelines.effect(effect, hasCover));
                pass.bindTexture("Sampler0", worldView, linear);
                if (hasCover) {
                    pass.bindTexture("Sampler1", cover.view(), cover.sampler());
                }
                pass.setUniform("IdleParams", paramSlice);
                pass.setVertexBuffer(0, quadBuffer);
                pass.draw(6, 1);
            } finally {
                pass.close();
            }
        } catch (Throwable throwable) {
            failed = true;
            MusicPlayerMod.LOGGER.warn("纯享特效初始化失败，本次仅显示文字", throwable);
        }
    }

    private GpuBufferSlice writeParams(CommandEncoder encoder, int width, int height, long now, float fade,
                                       MusicPlayerConfig.IdleSettings config, IdleEffect effect,
                                       float coverAspect) {
        // 相对时间原点：连续、单调，也不会在某个整点回绕时让噪声动画跳一帧。
        float time = Math.max(0L, now - timeOriginMillis) / 1000.0F;
        long elapsed = IdleNowPlaying.elapsedMillis();
        long duration = IdleNowPlaying.durationMillis();
        float progress = duration <= 0L ? 0.0F : Math.min(1.0F, elapsed / (float) duration);

        float bass = AudioAnalyzer.bass();
        float mid = AudioAnalyzer.mid();
        float treble = AudioAnalyzer.treble();
        float level = AudioAnalyzer.level();
        int rgb = MusicPlayerConfigManager.get().glass.themeColorRgb();

        float accentR = ((rgb >> 16) & 0xFF) / 255.0F;
        float accentG = ((rgb >> 8) & 0xFF) / 255.0F;
        float accentB = (rgb & 0xFF) / 255.0F;

        paramData.clear();
        paramData.putFloat(width).putFloat(height).putFloat(time).putFloat(fade);

        paramData.putFloat(bass).putFloat(mid).putFloat(treble).putFloat(level);

        paramData.putFloat(accentR).putFloat(accentG).putFloat(accentB)
                .putFloat((float) config.intensity);

        paramData.putFloat(progress).putFloat(coverAspect)
                .putFloat(effect.shaderMode() + 4.0F * IdleFilter.fromConfig().shaderIndex())
                .putFloat(bass * bass);
        paramData.flip();

        GpuBufferSlice slice = paramBuffers[paramCursor].slice();
        paramCursor = (paramCursor + 1) % paramBuffers.length;
        encoder.writeToBuffer(slice, paramData);
        return slice;
    }

    private void ensureResources(int width, int height) {
        GpuDevice device = RenderSystem.getDevice();
        if (quadBuffer == null) {
            quadBuffer = createQuadBuffer(device);
        }
        for (int i = 0; i < paramBuffers.length; i++) {
            if (paramBuffers[i] == null) {
                paramBuffers[i] = device.createBuffer(() -> "musicplayer idle params",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, PARAM_BUFFER_SIZE);
            }
        }
        int nextWidth = Math.max(1, width / WORLD_DIVISOR);
        int nextHeight = Math.max(1, height / WORLD_DIVISOR);
        if (worldView != null && worldWidth == nextWidth && worldHeight == nextHeight) {
            return;
        }
        closeWorld();
        worldTexture = device.createTexture(
                () -> "musicplayer idle world",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT
                        | GpuTexture.USAGE_COPY_DST,
                TextureFormat.RGBA8,
                nextWidth,
                nextHeight,
                1,
                1);
        worldView = device.createTextureView(worldTexture);
        worldWidth = nextWidth;
        worldHeight = nextHeight;
    }

    private void closeWorld() {
        if (worldView != null) {
            if (!worldView.isClosed()) {
                worldView.close();
            }
            worldView = null;
        }
        if (worldTexture != null) {
            if (!worldTexture.isClosed()) {
                worldTexture.close();
            }
            worldTexture = null;
        }
        worldWidth = 0;
        worldHeight = 0;
    }

    private static GpuBuffer createQuadBuffer(GpuDevice device) {
        float[][] vertices = {
                {-1.0F, -1.0F, 0.0F, 0.0F, 0.0F},
                {1.0F, -1.0F, 0.0F, 1.0F, 0.0F},
                {1.0F, 1.0F, 0.0F, 1.0F, 1.0F},
                {-1.0F, -1.0F, 0.0F, 0.0F, 0.0F},
                {1.0F, 1.0F, 0.0F, 1.0F, 1.0F},
                {-1.0F, 1.0F, 0.0F, 0.0F, 1.0F}
        };
        ByteBuffer buffer = BufferUtils.createByteBuffer(vertices.length * 5 * Float.BYTES);
        for (float[] vertex : vertices) {
            for (float value : vertex) {
                buffer.putFloat(value);
            }
        }
        buffer.flip();
        return device.createBuffer(() -> "musicplayer idle quad",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, buffer);
    }

    // ── 歌词 / 曲目文字 ─────────────────────────────────────────────────

    private void drawOverlay(Minecraft minecraft, GuiRenderState renderState, long now, float fade) {
        MusicPlayerConfig.IdleSettings config = MusicPlayerConfigManager.get().idle;
        boolean showLyrics = config.showLyrics;
        String title = showLyrics ? IdleNowPlaying.title() : "";
        String artist = showLyrics ? IdleNowPlaying.artist() : "";
        List<LyricLine> lines = showLyrics ? IdleLyrics.getInstance().lines() : List.of();
        float hintAlpha = hintAlpha(fade, now);
        if ((title.isBlank() && lines.isEmpty()) && hintAlpha <= 0.01F) {
            return;
        }
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(minecraft, renderState, 0, 0);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        Font font = minecraft.font;
        float alpha = smooth(fade);
        int rgb = MusicPlayerConfigManager.get().glass.themeColorRgb();

        int accent = withAlpha(0xFF000000 | lighten(rgb, 0.42F), alpha);
        int primary = withAlpha(0xFFFFFFFF, alpha * 0.96F);
        int secondary = withAlpha(0xFFD8DEE9, alpha * 0.66F);
        int lyricDim = 0xFFFFFFFF;
        int lyricBright = 0xFF000000 | lighten(rgb, 0.62F);

        int margin = Math.max(18, Math.round(width * 0.035F));

        // 底部渐暗，保证标题在明亮画面上也读得清。
        if (showLyrics) {
            graphics.fillGradient(0, height - 118, width, height,
                    withAlpha(0x00000000, alpha), withAlpha(0x9E000000, alpha));
        }

        // 曲目信息
        if (!title.isBlank()) {
            drawScaledText(graphics, font, title, margin, height - 70,
                    1.32F, primary, true, false);
            if (!artist.isBlank()) {
                drawScaledText(graphics, font, artist, margin, height - 46,
                        1.0F, secondary, true, false);
            }
        }

        // 进度条
        long elapsed = IdleNowPlaying.elapsedMillis();
        long duration = IdleNowPlaying.durationMillis();
        if (showLyrics && duration > 0L) {
            float progress = Math.min(1.0F, elapsed / (float) duration);
            int barY = height - 24;
            int barRight = width - margin;
            graphics.fill(margin, barY, barRight, barY + 2, withAlpha(0x40FFFFFF, alpha));
            int filled = margin + Math.round((barRight - margin) * progress);
            if (filled > margin) {
                graphics.fill(margin, barY, filled, barY + 2, accent);
            }
        }

        // 歌词
        if (!lines.isEmpty()) {
            drawLyrics(graphics, font, lines, width, height, alpha,
                    lyricDim, lyricBright, config.lyricScale);
        }

        // 首次进入的退出提示，9 秒后淡出。即使关掉歌词也必须显示：
        // 这是玩家唯一能知道「怎么退出」的地方。
        if (hintAlpha > 0.01F) {
            String hint = net.minecraft.network.chat.Component
                    .translatable("musicplayer.idle.hint").getString();
            int hintWidth = font.width(hint);
            int hintX = width - margin - hintWidth;
            int hintY = 16;
            graphics.text(font, hint, hintX, hintY,
                    withAlpha(0xFFFFFFFF, hintAlpha * 0.55F), true);
            // 一条极细的荧光下划线，让提示看起来是「刻」在画面上的，而不是一句普通文字。
            graphics.fill(hintX, hintY + font.lineHeight + 3, hintX + hintWidth,
                    hintY + font.lineHeight + 4, withAlpha(0xFF000000 | rgb, hintAlpha * 0.42F));
        }
    }

    /** 退出提示的不透明度：只在真正处于纯享模式时显示，淡出阶段不再出现。 */
    private static float hintAlpha(float fade, long now) {
        IdleController controller = IdleController.getInstance();
        if (!controller.isActive()) {
            return 0.0F;
        }
        long activeMillis = controller.activeMillis(now);
        return smooth(fade) * clamp01(1.0F - (activeMillis - HINT_VISIBLE_MILLIS) / 2000.0F);
    }

    private void drawLyrics(GuiGraphicsExtractor graphics, Font font,
                            List<LyricLine> lines, int width, int height, float alpha,
                            int dimColor, int brightColor, double lyricScale) {
        long elapsed = IdleNowPlaying.elapsedMillis();
        int index = IdleLyrics.getInstance().indexAt(elapsed);
        if (index < 0) {
            index = 0;
        }
        if (index >= lines.size()) {
            return;
        }

        int maxWidth = Math.max(80, Math.round(width * 0.84F));
        float baseScale = 1.95F * (float) lyricScale;

        float centerX = width * 0.5F;
        float lyricY = height * 0.56F;

        // 上一句
        if (index > 0) {
            String previous = lines.get(index - 1).text();
            if (!previous.isBlank()) {
                float scale = lineScale(font, previous, maxWidth, baseScale * 0.62F);
                drawScaledText(graphics, font, previous, centerX,
                        lyricY - font.lineHeight * scale - 16.0F,
                        scale, withAlpha(dimColor, alpha * 0.52F), true, true);
            }
        }

        // 下一句
        if (index + 1 < lines.size()) {
            String next = lines.get(index + 1).text();
            if (!next.isBlank()) {
                float scale = lineScale(font, next, maxWidth, baseScale * 0.76F);
                drawScaledText(graphics, font, next, centerX,
                        lyricY + font.lineHeight * baseScale + 18.0F,
                        scale, withAlpha(dimColor, alpha * 0.70F), true, true);
            }
        }

        String current = lines.get(index).text();
        if (current.isBlank()) {
            return;
        }
        float scale = lineScale(font, current, maxWidth, baseScale);
        drawScaledText(graphics, font, current, centerX, lyricY, scale,
                withAlpha(dimColor, alpha * 0.40F), true, true);

        long startMillis = lines.get(index).timeMillis();
        long endMillis = index + 1 < lines.size()
                ? lines.get(index + 1).timeMillis()
                : startMillis + 4800L;
        float progress = clamp01((elapsed - startMillis) / (float) Math.max(1L, endMillis - startMillis));
        if (progress <= 0.001F) {
            return;
        }

        int textWidth = font.width(current);
        float halfWidth = textWidth * scale * 0.5F;
        int left = Math.round(centerX - halfWidth);
        int right = Math.round(centerX + halfWidth);
        if (progress < 0.999F) {
            int clipRight = Math.round(left + (right - left) * progress);
            if (clipRight <= left) {
                return;
            }
            graphics.enableScissor(left - 2, Math.round(lyricY - 4.0F),
                    clipRight, Math.round(lyricY + font.lineHeight * scale + 4.0F));
            drawScaledText(graphics, font, current, centerX, lyricY, scale,
                    withAlpha(brightColor, alpha), true, true);
            graphics.disableScissor();
        } else {
            drawScaledText(graphics, font, current, centerX, lyricY, scale,
                    withAlpha(brightColor, alpha), true, true);
        }
    }

    /** 让长句自动缩小，短句保持大字号。 */
    private static float lineScale(Font font, String text, int maxWidth, float desired) {
        if (text == null || text.isEmpty()) {
            return desired;
        }
        float textWidth = Math.max(1, font.width(text));
        float fit = maxWidth / textWidth;
        return Math.max(1.0F, Math.min(desired, fit));
    }

    private static void drawScaledText(GuiGraphicsExtractor graphics, Font font, String text,
                                       float centerX, float topY, float scale, int color,
                                       boolean shadow, boolean centered) {
        int textWidth = font.width(text);
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, topY);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, centered ? -textWidth / 2 : 0, 0, color, shadow);
        graphics.pose().popMatrix();
    }

    // ── 颜色小工具 ──────────────────────────────────────────────────────

    private static int withAlpha(int argb, float alpha) {
        int base = (argb >>> 24) & 0xFF;
        int value = Math.max(0, Math.min(255, Math.round(base * clamp01(alpha))));
        return (value << 24) | (argb & 0x00FFFFFF);
    }

    private static int lighten(int rgb, float amount) {
        float t = clamp01(amount);
        int r = Math.round(((rgb >> 16) & 0xFF) + (255 - ((rgb >> 16) & 0xFF)) * t);
        int g = Math.round(((rgb >> 8) & 0xFF) + (255 - ((rgb >> 8) & 0xFF)) * t);
        int b = Math.round((rgb & 0xFF) + (255 - (rgb & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static float smooth(float value) {
        float t = clamp01(value);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
