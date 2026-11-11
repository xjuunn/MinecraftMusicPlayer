package com.junhsiun.musicplayer.client.gui.media;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiPipeline;
import net.minecraft.client.renderer.texture.TextureResources;
import net.minecraft.resources.Identifier;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 浏览界面的缩略图缓存：封面、头像统一生成一张保持原始宽高比的纹理。
 *
 * <p><b>为什么不复用 {@code CoverArtTextureCache}：</b>那个缓存是给唱片机方块用的——
 * 它把封面缩进 500×500 画布、按材质像素格抠圆角、还烘焙了内阴影。同一个 URL 拿去做列表缩略图，
 * 会连黑边和阴影一起画出来，而且它按格子复用一个画布纹理，列表里每行都要自己算 UV。
 * 两件事的产出物形状不同，分开做比硬凑更省事，也不会互相影响。</p>
 *
 * <p><b>三条硬要求（都对应实际踩过的观感问题）：</b></p>
 * <ol>
 *   <li><b>不裁切</b>：以前按中心正方形裁，非正方形的封面两边被切掉，看起来就是"封面不完整"。
 *       现在按原始宽高比缩到长边 {@link #TEXTURE_SIZE}，整张图都在，显示时用
 *       {@link #draw} 在方框内等比内接。</li>
 *   <li><b>够清晰</b>：纹理长边 {@link #TEXTURE_SIZE}，比显示尺寸略大，等于<b>降采样</b>显示；
 *       缩放用线性采样（{@link FilterMode#LINEAR}）而不是最近邻，不会出现块状锯齿。</li>
 *   <li><b>不放大</b>：源图向图床要 2 倍边长（{@code ?param=128y128}），本地再做区域平均，
 *       等于先超采样再缩小，比直接拿 64px 源图放大清楚得多。</li>
 * </ol>
 *
 * <p><b>为什么要自己画圆角：</b>GUI 的 blit 只能画方形。列表里的封面/头像要圆角，
 * 就在生成纹理时按圆角矩形算一次覆盖率（边缘带一点渐变），比在 GUI 里套一层遮罩省事得多。</p>
 */
public final class MediaThumbnails {

    /**
     * 纹理长边（像素）。
     *
     * <p>取 64 而不是更大，是被<b>采样方式</b>反推出来的：这张纹理没有 mipmap，双线性一次只取
     * 2×2 个像素。纹理比显示尺寸大得越多，一次采样覆盖到的源面积比例越小，缩小后的高频细节
     * 就越容易闪。列表封面画 22px、详情页头部 44px，64 对应 2.9× 与 1.45×，都还在双线性能
     * 干净处理的范围内；换成 128 反而会让 22px 那档变成 5.8× 抽点，看起来更脏。</p>
     *
     * <p>详情页那张大封面（{@code SongDetailView} 画到 ~84px）另有高分辨率档位，见
     * {@link #TEXTURE_SIZE_LARGE}——小缩略图追求不闪，大封面追求清晰，两者诉求相反，
     * 所以分成两套纹理缓存而不是硬用一个尺寸。</p>
     */
    public static final int TEXTURE_SIZE = 64;

    /**
     * 大封面（详情页）纹理长边（像素）。
     *
     * <p>详情页那张封面画到 ~84px，按 GUI 缩放（2~3 倍）实际占 168~252 设备像素；64px 纹理
     * 上去一定是糊的。这里单独用 256 的纹理，配合线性采样，在常见的 2~3 倍缩放下接近 1:1，
     * 清晰度足够。小缩略图仍然走 {@link #TEXTURE_SIZE}，两者各用一份缓存互不影响。</p>
     */
    public static final int TEXTURE_SIZE_LARGE = 256;
    /** 圆角半径占短边的比例（iOS 风格的大圆角）。 */
    private static final float RADIUS_RATIO = 0.22F;
    /** 最多缓存多少张。 */
    private static final int MAX_ENTRIES = 128;
    /** 向图床要的源图边长；2 倍于纹理，本地再做区域平均，缩下来不会糊。 */
    private static final int REQUEST_DIMENSION = 128;
    /** 大封面向图床要的源图边长；更大，保证详情页那张大图清晰。 */
    private static final int REQUEST_DIMENSION_LARGE = 512;
    /** 下载大小上限。 */
    private static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;
    /** 源图最大边长，防止超大图把内存吃穿。 */
    private static final int MAX_SOURCE_DIMENSION = 4096;
    /** 失败后的重试冷却。 */
    private static final long RETRY_COOLDOWN_MILLIS = 5000L;
    /** 下载卡住多久后允许重来。 */
    private static final long PENDING_TIMEOUT_MILLIS = 15000L;

    private static final MediaThumbnails INSTANCE = new MediaThumbnails();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "musicplayer-thumbnail");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Entry> entries = new LinkedHashMap<>(32, 0.75F, true);
    /** 大封面缓存（详情页大图），与小缩略图分开，互不挤占，纹理分辨率也更高。 */
    private final Map<String, Entry> largeEntries = new LinkedHashMap<>(16, 0.75F, true);
    private long nextToken;

    private MediaThumbnails() {
    }

    public static MediaThumbnails getInstance() {
        return INSTANCE;
    }

    /**
     * 一张可绘制的缩略图。
     *
     * @param id     注册进 {@code TextureManager} 的标识（释放时要用）
     * @param view   纹理视图
     * @param sampler 采样器（线性过滤，缩放才平滑）
     * @param width  纹理宽（像素）
     * @param height 纹理高（像素）
     */
    public record Thumb(Identifier id, GpuTextureView view, GpuSampler sampler, int width, int height) {

        /** 原始宽高比。 */
        public float aspect() {
            return height <= 0 ? 1.0F : (float) width / height;
        }
    }

    /**
     * 取缩略图；还没下载好返回 {@code null}，并顺带发起下载。
     *
     * <p>渲染循环里每帧调用是安全的：命中缓存只是一次哈希查找，未命中时也会被 pending 状态挡住。</p>
     */
    public Thumb get(String url) {
        return get(url, false);
    }

    /**
     * 取大封面缩略图（详情页那张大图，纹理分辨率更高）；还没下载好返回 {@code null}，并顺带发起下载。
     *
     * <p>与小缩略图走同一套下载/缩放逻辑，只是纹理更大、向图床要的源图更大，因此更清晰。</p>
     */
    public Thumb getLarge(String url) {
        return get(url, true);
    }

    private Thumb get(String url, boolean large) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Map<String, Entry> map = mapFor(large);
        synchronized (this) {
            Entry entry = map.get(url);
            if (entry != null && entry.thumb() != null) {
                return entry.thumb();
            }
        }
        request(url, large);
        return null;
    }

    /**
     * 在 {@code size × size} 的方框内<b>等比内接</b>绘制一张缩略图（不裁切、不拉伸）。
     *
     * <p>绝大多数封面是正方形，等比内接就等于铺满；少数非正方形的（部分歌单/头像）会
     * 在方框里留出对称的空白，而不是被切掉两边。</p>
     */
    public static void draw(GuiGraphicsExtractor graphics, Thumb thumb, int x, int y, int size) {
        drawBounded(graphics, thumb, x, y, x + size, y + size);
    }

    /**
     * 在任意矩形内<b>等比内接</b>绘制缩略图，供共享元素过渡插值封面位置/尺寸时使用。
     *
     * <p>和 {@link #draw} 同一条路径：先按纹理原始宽高比决定内接尺寸，再在矩形里居中，
     * 不裁切、不拉伸。</p>
     */
    public static void drawBounded(GuiGraphicsExtractor graphics, Thumb thumb,
                                   int x0, int y0, int x1, int y1) {
        drawBounded(graphics, thumb, x0, y0, x1, y1, -1);
    }

    /**
     * 在任意矩形内等比内接绘制缩略图，并叠加一个 ARGB 颜色（用于交叉淡入）。
     *
     * <p>{@code color} 为 {@code -1} 时走和 {@link #drawBounded(GuiGraphicsExtractor, Thumb, int, int, int, int)}
     * 完全相同的路径；否则用带颜色参数的 blit 把同一个纹理按 alpha 叠加上去。
     * 两个纹理来自同一张封面、同一内接尺寸，淡入过程中不会露出空白。</p>
     *
     * @param color ARGB 颜色；alpha 通道控制不透明度
     */
    public static void drawBounded(GuiGraphicsExtractor graphics, Thumb thumb,
                                   int x0, int y0, int x1, int y1, int color) {
        if (thumb == null || x1 <= x0 || y1 <= y0) {
            return;
        }
        int boxWidth = x1 - x0;
        int boxHeight = y1 - y0;
        float aspect = thumb.aspect();
        int drawWidth;
        int drawHeight;
        if (aspect >= (float) boxWidth / boxHeight) {
            drawWidth = boxWidth;
            drawHeight = Math.max(1, Math.round(boxWidth / aspect));
        } else {
            drawHeight = boxHeight;
            drawWidth = Math.max(1, Math.round(boxHeight * aspect));
        }
        int offsetX = x0 + (boxWidth - drawWidth) / 2;
        int offsetY = y0 + (boxHeight - drawHeight) / 2;
        if (color == -1) {
            // 这个重载的四个 int 是 <b>(x0, y0, x1, y1)</b>，不是 (x, y, w, h)；
            // 四个 float 是 <b>(u0, u1, v0, v1)</b>，不是 (u0, v0, u1, v1)。
            // 按 (x, y, w, h) 传会得到一个右下角缩到 (w, h) 的倒置矩形，画出来什么都没有
            // ——"封面全都消失"就是这么来的。
            graphics.blit(thumb.view(), thumb.sampler(),
                    offsetX, offsetY, offsetX + drawWidth, offsetY + drawHeight,
                    0.0F, 1.0F, 0.0F, 1.0F);
            return;
        }
        // 带 tint 的版本需要按纹理注册 ID 走 TextureManager，才能把颜色（含 alpha）
        // 传给 BlitRenderState；GpuTextureView 的 blit 重载没有颜色参数。
        graphics.blit(GuiPipeline.TEXTURED, thumb.id(),
                offsetX, offsetY, 0.0F, 0.0F,
                drawWidth, drawHeight,
                thumb.width(), thumb.height(),
                thumb.width(), thumb.height(), color);
    }

    /** 只发起下载，不等结果。 */
    public void request(String url) {
        request(url, false);
    }

    /** 只发起大封面下载，不等结果。 */
    public void requestLarge(String url) {
        request(url, true);
    }

    private void request(String url, boolean large) {
        if (url == null || url.isBlank() || executor.isShutdown()) {
            return;
        }
        Map<String, Entry> map = mapFor(large);
        synchronized (this) {
            Entry existing = map.get(url);
            if (existing != null) {
                if (existing.loading() && !existing.isStale()) {
                    return;
                }
                if (!existing.loading() && !existing.canRetry()) {
                    return;
                }
            }
            long token = nextToken++;
            map.put(url, Entry.pending(token));
        }
        try {
            executor.execute(() -> download(url, large));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            synchronized (this) {
                map.remove(url);
            }
        }
    }

    /** 释放全部纹理（退出世界时调用）。 */
    public void clear() {
        Minecraft client = Minecraft.getInstance();
        synchronized (this) {
            for (Entry entry : entries.values()) {
                // 纹理与像素都要在客户端线程上释放
                client.execute(() -> release(client, entry));
            }
            entries.clear();
            for (Entry entry : largeEntries.values()) {
                client.execute(() -> release(client, entry));
            }
            largeEntries.clear();
        }
    }

    public void shutdown() {
        clear();
        executor.shutdown();
    }

    // ── 下载与生成 ──────────────────────────────────────────────────────

    private void download(String url, boolean large) {
        try {
            OkHttpClient client = HttpClientFactory.create();
            Request request = new Request.Builder()
                    .url(downloadUrl(url, requestDimension(large)))
                    .header("User-Agent", "MinecraftMusicPlayer/" + MusicPlayerMod.VERSION)
                    .get()
                    .build();
            byte[] bytes;
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code());
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("empty body");
                }
                if (body.contentLength() > MAX_IMAGE_BYTES) {
                    throw new IOException("image too large");
                }
                bytes = body.bytes();
            }
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw new IOException("image too large");
            }
            NativeImage thumbnail;
            try (NativeImage source = readImage(bytes)) {
                thumbnail = toThumbnail(source, textureSize(large));
            }
            Identifier textureId = Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, textureIdPath(large, url));
            Minecraft.getInstance().execute(() -> register(url, textureId, thumbnail, large));
        } catch (Exception exception) {
            Map<String, Entry> map = mapFor(large);
            synchronized (this) {
                Entry current = map.get(url);
                if (current != null && current.loading()) {
                    map.put(url, Entry.failure(current.token()));
                }
            }
            MusicPlayerMod.LOGGER.debug("缩略图下载失败: {}", url, exception);
        }
    }

    private synchronized void register(String url, Identifier textureId, NativeImage image, boolean large) {
        Minecraft client = Minecraft.getInstance();
        Map<String, Entry> map = mapFor(large);
        Entry current = map.get(url);
        if (current == null || !current.loading()) {
            // 期间被 clear() 清掉：直接释放，避免纹理泄漏
            image.close();
            return;
        }
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            // 线性采样：纹理比显示尺寸大得多，靠它把降采样做平滑；
            // 用默认的最近邻会在缩小的时候丢像素，看起来"像素很低、有锯齿"。
            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            TextureResources resources = TextureResources.from2dImage(
                    () -> "musicplayer_thumb", image, sampler);
            client.getTextureManager().register(textureId, resources);
            // from2dImage 只把像素写进命令缓冲，<b>不会</b>关掉 NativeImage，得由我们自己释放。
            // 但不能立刻关：写入是录进命令缓冲、帧末才提交的，提前 close 会让上传读到已释放的
            // 内存。所以把它挂在缓存条目上，等这张纹理被淘汰/清空时再释放——那时早就提交完了。
            map.put(url, Entry.ready(
                    new Thumb(textureId, resources.textureView(), sampler, width, height), image));
            while (map.size() > MAX_ENTRIES) {
                String eldestKey = map.keySet().iterator().next();
                Entry eldest = map.remove(eldestKey);
                if (eldest != null) {
                    release(client, eldest);
                }
            }
        } catch (Exception exception) {
            image.close();
            map.put(url, Entry.failure(current.token()));
            MusicPlayerMod.LOGGER.debug("缩略图注册失败: {}", url, exception);
        }
    }

    /** 释放一条缓存条目占用的 GPU 纹理与堆外像素。 */
    private static void release(Minecraft client, Entry entry) {
        if (entry.thumb() != null) {
            client.getTextureManager().release(entry.thumb().id());
        }
        if (entry.image() != null) {
            entry.image().close();
        }
    }

    private static NativeImage readImage(byte[] bytes) throws IOException {
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (IOException ignored) {
            // 网易云封面大多是 JPEG，NativeImage 只认 PNG，这里退回 ImageIO
            BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
            if (buffered == null) {
                throw ignored;
            }
            int width = buffered.getWidth();
            int height = buffered.getHeight();
            if (width <= 0 || height <= 0 || width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION) {
                throw new IOException("image dimensions out of range: " + width + "x" + height);
            }
            NativeImage image = new NativeImage(width, height, true);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixel(x, y, buffered.getRGB(x, y));
                }
            }
            return image;
        }
    }

    /**
     * 按原始宽高比缩到长边 {@link #TEXTURE_SIZE}，并抠圆角。
     *
     * <p><b>不裁切</b>：整张源图都在结果里，非正方形的图会得到一个非正方形的纹理，
     * 绘制时由 {@link #draw} 等比内接。</p>
     *
     * <p>缩小时按<b>区域平均</b>取色，不是最近邻抽点：最近邻会漏掉大部分像素，
     * 高频细节变成噪点，缩小后还会闪烁。</p>
     */
    private static NativeImage toThumbnail(NativeImage source, int textureSize) {
        int srcWidth = Math.max(1, source.getWidth());
        int srcHeight = Math.max(1, source.getHeight());
        float scale = (float) textureSize / Math.max(srcWidth, srcHeight);
        if (scale > 1.0F) {
            // 源图本来就比纹理小：不放大，凭空插值只会更糊
            scale = 1.0F;
        }
        int outWidth = Math.max(1, Math.min(textureSize, Math.round(srcWidth * scale)));
        int outHeight = Math.max(1, Math.min(textureSize, Math.round(srcHeight * scale)));

        NativeImage result = new NativeImage(outWidth, outHeight, true);
        for (int y = 0; y < outHeight; y++) {
            int fromY = y * srcHeight / outHeight;
            int toY = Math.max(fromY + 1, (y + 1) * srcHeight / outHeight);
            for (int x = 0; x < outWidth; x++) {
                int fromX = x * srcWidth / outWidth;
                int toX = Math.max(fromX + 1, (x + 1) * srcWidth / outWidth);
                long alpha = 0L;
                long red = 0L;
                long green = 0L;
                long blue = 0L;
                int samples = 0;
                for (int sy = fromY; sy < toY; sy++) {
                    for (int sx = fromX; sx < toX; sx++) {
                        int color = source.getPixel(sx, sy);
                        int a = (color >>> 24) & 0xFF;
                        alpha += a;
                        red += ((color >>> 16) & 0xFF) * a;
                        green += ((color >>> 8) & 0xFF) * a;
                        blue += (color & 0xFF) * a;
                        samples++;
                    }
                }
                if (samples == 0) {
                    result.setPixel(x, y, 0);
                    continue;
                }
                int outAlpha = (int) (alpha / samples);
                if (outAlpha <= 0) {
                    result.setPixel(x, y, 0);
                    continue;
                }
                // 颜色按 alpha 加权平均，避免透明边缘把颜色拉黑
                long weight = alpha == 0L ? 1L : alpha;
                int outRed = (int) (red / weight);
                int outGreen = (int) (green / weight);
                int outBlue = (int) (blue / weight);
                int coverage = roundedCoverage(x, y, outWidth, outHeight);
                int maskedAlpha = outAlpha * coverage / 255;
                result.setPixel(x, y, (maskedAlpha << 24) | (outRed << 16) | (outGreen << 8) | outBlue);
            }
        }
        return result;
    }

    /**
     * 圆角矩形的边缘覆盖率（0~255）。
     *
     * <p>按"到圆角轮廓的距离"给一点渐变，缩放显示时边缘是平滑的，不会出现锯齿。</p>
     */
    private static int roundedCoverage(int x, int y, int width, int height) {
        float radius = Math.min(width, height) * RADIUS_RATIO;
        float centerX = x + 0.5F;
        float centerY = y + 0.5F;
        float nearestX = Math.min(Math.max(centerX, radius), width - radius);
        float nearestY = Math.min(Math.max(centerY, radius), height - radius);
        float dx = centerX - nearestX;
        float dy = centerY - nearestY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy) - radius;
        // distance < 0 在内部；边缘 1px 内做线性过渡
        float coverage = 0.5F - distance;
        if (coverage <= 0.0F) {
            return 0;
        }
        if (coverage >= 1.0F) {
            return 255;
        }
        return Math.round(coverage * 255.0F);
    }

    /** 网易图床支持 {@code ?param=WxH} 服务端缩放；其它来源原样请求。 */
    private static String downloadUrl(String url, int requestDimension) {
        if (url.indexOf('?') >= 0) {
            return url;
        }
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException exception) {
            return url;
        }
        if (host == null) {
            return url;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (!normalized.equals("music.126.net") && !normalized.endsWith(".music.126.net")) {
            return url;
        }
        return url + "?param=" + requestDimension + "y" + requestDimension;
    }

    private Map<String, Entry> mapFor(boolean large) {
        return large ? largeEntries : entries;
    }

    private static int textureSize(boolean large) {
        return large ? TEXTURE_SIZE_LARGE : TEXTURE_SIZE;
    }

    private static int requestDimension(boolean large) {
        return large ? REQUEST_DIMENSION_LARGE : REQUEST_DIMENSION;
    }

    private static String textureIdPath(boolean large, String url) {
        // Identifier 的 path 只允许 a-z0-9/._-，大写会被 assertValidPath 直接拒掉
        // （fromNamespaceAndPath 抛 IdentifierException），这里必须全小写。
        return (large ? "thumb_large/" : "thumb/") + hash(url);
    }

    private static String hash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(32);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value & 0xFF));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(url.hashCode());
        }
    }

    private record Entry(Thumb thumb, NativeImage image, boolean loading, boolean failed,
                         long token, long stampMillis) {
        static Entry pending(long token) {
            return new Entry(null, null, true, false, token, System.currentTimeMillis());
        }

        static Entry ready(Thumb thumb, NativeImage image) {
            return new Entry(thumb, image, false, false, 0L, System.currentTimeMillis());
        }

        static Entry failure(long token) {
            return new Entry(null, null, false, true, token, System.currentTimeMillis());
        }

        boolean isStale() {
            return System.currentTimeMillis() - stampMillis > PENDING_TIMEOUT_MILLIS;
        }

        boolean canRetry() {
            return failed && System.currentTimeMillis() - stampMillis >= RETRY_COOLDOWN_MILLIS;
        }
    }
}
