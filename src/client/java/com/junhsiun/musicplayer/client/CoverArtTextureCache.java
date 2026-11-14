package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ExternalTextureProvider;
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

public final class CoverArtTextureCache {
    private static final int MAX_CACHE_SIZE = 32;
    /** 封面下载大小上限（字节）。 */
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    /** 封面解码最大边长，防止超大图触发 OOM。 */
    private static final int MAX_IMAGE_DIMENSION = 4096;
    /**
     * 封面纹理尺寸：与唱片机侧面显示区域同比例（贴图上是 10×10 像素，正方形）。
     *
     * <p>画布保持正方形；封面按原比例缩放后居中放进去，空出来的部分填黑。</p>
     */
    private static final int COVER_WIDTH = 500;
    private static final int COVER_HEIGHT = 500;
    /** 显示区域是 10×10 个材质像素，画布按整数倍放大，方便按像素格子抠图。 */
    private static final int COVER_CELLS = 10;
    private static final int COVER_CELL_SIZE = COVER_WIDTH / COVER_CELLS;
    /** 留黑边的底色（不透明纯黑）。 */
    private static final int LETTERBOX_COLOR = 0xFF000000;
    /**
     * 需要抠成透明的格子（列, 行，0 基，相对显示区域左上角）。
     *
     * <p>唱片机材质在那两处是圆角/边框，抠掉之后就能看到唱片机本身的材质：
     * 第一行左右各 2 格，第二行左右各 1 格。</p>
     */
    private static final int[][] TRANSPARENT_CELLS = {
            {0, 0}, {1, 0}, {8, 0}, {9, 0},
            {0, 1}, {9, 1}
    };
    /** 内阴影/反光的参数（单位：材质像素格）。 */
    private static final float INSET_TOP_CELLS = 1.8F;
    private static final float INSET_TOP_STRENGTH = 0.45F;
    private static final float INSET_BOTTOM_CELLS = 1.4F;
    private static final float INSET_BOTTOM_STRENGTH = 0.10F;
    private static final float INSET_SIDE_CELLS = 1.0F;
    private static final float INSET_SIDE_STRENGTH = 0.22F;
    /**
     * 向图床要的边长。
     *
     * <p>封面在近处大约占 300×430 屏幕像素，纹理按 500×500 做；源图要 512 才不会因为
     * 放大而发糊。网易图床支持 {@code ?param=WxH} 服务端缩放。</p>
     */
    private static final int REQUEST_DIMENSION = 512;
    /** 失败后的重试冷却（毫秒）。 */
    private static final long RETRY_COOLDOWN_MILLIS = 5000L;
    /** 下载卡住的判定时间（毫秒）：超过它仍是 pending 就允许重新下载。 */
    private static final long PENDING_TIMEOUT_MILLIS = 15000L;
    private static final CoverArtTextureCache INSTANCE = new CoverArtTextureCache();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "musicplayer-cover-cache");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, CacheEntry> entries = new LinkedHashMap<>(16, 0.75F, true);
    /** 请求世代号：用于识别"在途下载已被新一轮重试取代"的情况。 */
    private long nextToken;

    private CoverArtTextureCache() {
    }

    public static CoverArtTextureCache getInstance() {
        return INSTANCE;
    }

    /**
     * 请求下载并注册一张封面。
     *
     * <p>已经处于 pending 或 ready 的条目直接返回。失败条目与卡死的 pending 条目
     * 在冷却后<b>允许重新下载</b>：渲染器每帧都会调本方法，如果失败一次就永久放弃，
     * 玩家会看到唱片一直缺封面，只能重进存档才能恢复——这正是"封面时有时无"的来源。
     * 重试有冷却，避免离线时每帧都发起一次必然失败的请求。</p>
     */
    public synchronized void request(String coverUrl) {
        if (coverUrl == null || coverUrl.isBlank()) {
            return;
        }
        if (executor.isShutdown()) {
            return;
        }
        CacheEntry existing = entries.get(coverUrl);
        if (existing != null) {
            if (existing.loading() && !existing.isStale()) {
                return;
            }
            if (!existing.loading() && !existing.canRetry()) {
                return;
            }
        }
        MusicPlayerMod.LOGGER.debug("Requesting cover art: {}", coverUrl);
        long token = nextToken++;
        entries.put(coverUrl, CacheEntry.pending(token));
        try {
            executor.execute(() -> downloadAndRegister(coverUrl, token));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            entries.remove(coverUrl);
        }
    }

    public synchronized Identifier getTextureId(String coverUrl) {
        CacheEntry entry = entries.get(coverUrl);
        return entry == null ? null : entry.textureId();
    }

    public synchronized void clear() {
        Minecraft client = Minecraft.getInstance();
        for (CacheEntry entry : entries.values()) {
            client.execute(() -> releaseEntry(client, entry));
        }
        entries.clear();
    }

    /**
     * 释放一条缓存占用的纹理。
     *
     * <p>26.4-snapshot-8 起 {@code TextureManager} 的纹理都由 {@code TextureProvider} 托管，
     * 而 {@code ExternalTextureProvider.releaseTexture} 是空实现，{@code release(id)} 不再顺带
     * 关闭纹理。所以这里在注销 ID 之后再自己 {@code close()} 一次，否则显存会泄漏。</p>
     */
    private static void releaseEntry(Minecraft client, CacheEntry entry) {
        if (entry.textureId() != null) {
            client.getTextureManager().release(entry.textureId());
        }
        if (entry.resources() != null) {
            entry.resources().close();
        }
    }

    public void shutdown() {
        clear();
        executor.shutdown();
    }

    private void downloadAndRegister(String coverUrl, long token) {
        long startedNanos = System.nanoTime();
        try {
            OkHttpClient client = HttpClientFactory.create();
            Request request = new Request.Builder()
                    .url(downloadUrl(coverUrl))
                    .header("User-Agent", "MinecraftMusicPlayer/" + com.junhsiun.musicplayer.MusicPlayerMod.VERSION)
                    .get()
                    .build();
            byte[] bytes;
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code());
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty image body");
                }
                long contentLength = body.contentLength();
                if (contentLength > MAX_IMAGE_BYTES) {
                    throw new IOException("Image too large: " + contentLength);
                }
                bytes = body.bytes();
            }
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw new IOException("Image too large: " + bytes.length);
            }

            try (NativeImage image = readImage(bytes)) {
                if (image.getWidth() <= 0 || image.getHeight() <= 0
                        || image.getWidth() > MAX_IMAGE_DIMENSION || image.getHeight() > MAX_IMAGE_DIMENSION) {
                    throw new IOException("Image dimensions out of range: " + image.getWidth() + "x" + image.getHeight());
                }
                NativeImage processedImage = toCoverTexture(image);
                Identifier textureId = Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "cover/" + hashUrl(coverUrl));
                Minecraft.getInstance().execute(() -> registerTexture(coverUrl, token, textureId, processedImage, startedNanos));
            }
        } catch (Exception exception) {
            synchronized (this) {
                // 只有当条目还是本次请求时才能标记失败：期间可能已经被 clear() 清掉，
                // 或者被新一轮重试取代
                CacheEntry current = entries.get(coverUrl);
                if (current != null && current.token() == token) {
                    entries.put(coverUrl, CacheEntry.failure(token));
                }
            }
            MusicPlayerMod.LOGGER.warn("Failed to download cover art: {}", coverUrl, exception);
        }
    }

    private synchronized void registerTexture(String coverUrl, long token, Identifier textureId, NativeImage image, long startedNanos) {
        Minecraft client = Minecraft.getInstance();
        try {
            // clear() 期间、或被新一轮重试取代的在途下载：直接释放纹理，避免泄漏
            CacheEntry current = entries.get(coverUrl);
            if (current == null || !current.loading() || current.token() != token) {
                image.close();
                return;
            }
            // 26.4-snapshot-8 起 TextureManager 不再接受现成的 TextureResources，
            // 只能注册 TextureProvider；ExternalTextureProvider 正是官方的“外部已上传纹理”包装，
            // 它把传入的 TextureResources 原样当作 current handle 返回，并且 releaseTexture 是空实现，
            // 所以纹理的生命周期仍然完全由本缓存（release / TextureResources.close）控制，行为与旧版一致。
            TextureResources texture = TextureResources.from2dImage(() -> "musicplayer_cover", image);
            client.getTextureManager().registerProvider(textureId, new ExternalTextureProvider(texture));
            entries.put(coverUrl, CacheEntry.ready(textureId, texture));
            // 用 INFO 而不是 DEBUG：这一行是"封面到底有没有下载成功"的唯一证据。
            // 默认日志级别下 DEBUG 不会打印，出问题时就看不出是没下载、还是下载了但没画出来。
            // 每个 URL 每个会话只会打印一次（命中缓存后 request 直接返回）。
            // 带上耗时，方便判断"黑几秒"到底是网络还是别的地方。
            MusicPlayerMod.LOGGER.info("Cover art ready in {} ms: {} -> {}",
                    (System.nanoTime() - startedNanos) / 1_000_000L, coverUrl, textureId);

            while (entries.size() > MAX_CACHE_SIZE) {
                String eldestKey = entries.keySet().iterator().next();
                CacheEntry eldest = entries.remove(eldestKey);
                if (eldest != null) {
                    releaseEntry(client, eldest);
                }
            }
        } catch (Exception exception) {
            entries.put(coverUrl, CacheEntry.failure(token));
            image.close();
            MusicPlayerMod.LOGGER.warn("Failed to register cover art texture: {}", coverUrl, exception);
        }
    }

    private static NativeImage readImage(byte[] bytes) throws IOException {
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (IOException ignored) {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bufferedImage == null) {
                throw ignored;
            }
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                throw new IOException("Image dimensions out of range: " + width + "x" + height);
            }
            NativeImage image = new NativeImage(width, height, true);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = bufferedImage.getRGB(x, y);
                    image.setPixel(x, y, argb);
                }
            }
            return image;
        }
    }

    /**
     * 把原图按原比例缩放进正方形画布，居中显示，空出来的部分填黑，最后抠掉圆角。
     *
     * <p>不裁切：封面完整显示，避免把图片边缘切掉。宽高比不一致时，多出来的上下或左右
     * 就留黑边。正方形封面（绝大多数）正好铺满，没有黑边。</p>
     */
    private static NativeImage toCoverTexture(NativeImage source) {
        int srcWidth = Math.max(1, source.getWidth());
        int srcHeight = Math.max(1, source.getHeight());
        float srcAspect = (float) srcWidth / srcHeight;
        float targetAspect = (float) COVER_WIDTH / COVER_HEIGHT;

        int drawWidth;
        int drawHeight;
        if (srcAspect > targetAspect) {
            // 原图比区域更宽（正方形封面走这条）：左右铺满，上下留黑边
            drawWidth = COVER_WIDTH;
            drawHeight = Math.max(1, Math.round(COVER_WIDTH / srcAspect));
        } else {
            // 原图比区域更窄：上下铺满，左右留黑边
            drawHeight = COVER_HEIGHT;
            drawWidth = Math.max(1, Math.round(COVER_HEIGHT * srcAspect));
        }
        drawWidth = Math.min(drawWidth, COVER_WIDTH);
        drawHeight = Math.min(drawHeight, COVER_HEIGHT);
        int offsetX = (COVER_WIDTH - drawWidth) / 2;
        int offsetY = (COVER_HEIGHT - drawHeight) / 2;

        NativeImage result = new NativeImage(COVER_WIDTH, COVER_HEIGHT, true);
        for (int y = 0; y < COVER_HEIGHT; y++) {
            for (int x = 0; x < COVER_WIDTH; x++) {
                result.setPixel(x, y, LETTERBOX_COLOR);
            }
        }
        for (int y = 0; y < drawHeight; y++) {
            int srcY = y * srcHeight / drawHeight;
            for (int x = 0; x < drawWidth; x++) {
                int srcX = x * srcWidth / drawWidth;
                result.setPixel(offsetX + x, offsetY + y, source.getPixel(srcX, srcY));
            }
        }
        clearCorners(result);
        applyInsetShading(result);
        return result;
    }

    /**
     * 给封面加一层"嵌进去"的明暗：顶部一道内阴影、左右轻微压暗、底部一点点反光。
     *
     * <p>光源按惯例在上方，所以凹槽的上内壁会挡住光、在封面顶部留下阴影，下内壁受光反射
     * 到底部。配合整体压暗，看上去就像封面陷在唱片机内部，而不是贴在表面。</p>
     */
    private static void applyInsetShading(NativeImage image) {
        for (int y = 0; y < COVER_HEIGHT; y++) {
            float cellY = (y + 0.5F) / COVER_CELL_SIZE;
            float topShadow = clamp01((INSET_TOP_CELLS - cellY) / INSET_TOP_CELLS) * INSET_TOP_STRENGTH;
            float bottomLight = clamp01((cellY - (COVER_CELLS - INSET_BOTTOM_CELLS)) / INSET_BOTTOM_CELLS)
                    * INSET_BOTTOM_STRENGTH;
            for (int x = 0; x < COVER_WIDTH; x++) {
                float cellX = (x + 0.5F) / COVER_CELL_SIZE;
                float sideShadow = (clamp01(INSET_SIDE_CELLS - cellX)
                        + clamp01(cellX - (COVER_CELLS - INSET_SIDE_CELLS))) * INSET_SIDE_STRENGTH;
                float factor = 1.0F - topShadow - sideShadow + bottomLight;
                if (factor >= 0.999F) {
                    continue;
                }
                int color = image.getPixel(x, y);
                int alpha = (color >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                int red = scaleChannel((color >>> 16) & 0xFF, factor);
                int green = scaleChannel((color >>> 8) & 0xFF, factor);
                int blue = scaleChannel(color & 0xFF, factor);
                image.setPixel(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, value);
    }

    private static int scaleChannel(int channel, float factor) {
        int scaled = Math.round(channel * factor);
        return Math.max(0, Math.min(255, scaled));
    }

    /** 把 {@link #TRANSPARENT_CELLS} 列出的格子抠成全透明，露出唱片机本身的材质。 */
    private static void clearCorners(NativeImage image) {
        for (int[] cell : TRANSPARENT_CELLS) {
            int startX = cell[0] * COVER_CELL_SIZE;
            int startY = cell[1] * COVER_CELL_SIZE;
            for (int y = startY; y < startY + COVER_CELL_SIZE; y++) {
                for (int x = startX; x < startX + COVER_CELL_SIZE; x++) {
                    image.setPixel(x, y, 0x00000000);
                }
            }
        }
    }

    /**
     * 实际下载用的地址。
     *
     * <p>网易云图床支持 {@code ?param=WxH} 服务端缩放：原始封面常常上千像素、几百 KB，
     * 而唱片在游戏里只有 0.68 格宽，要一张 256 的只要几十 KB，弱网下差距就是"几秒"
     * 和"零点几秒"。只对网易图床加这个参数，其它来源（自建代理、本地地址）原样请求，
     * 免得给不认识这个参数的服务器拼上奇怪的查询串。</p>
     *
     * <p>缓存键与纹理 ID 仍然按<b>原始</b>地址算，同一张封面只会有一份纹理。</p>
     */
    private static String downloadUrl(String coverUrl) {
        if (coverUrl.indexOf('?') >= 0) {
            return coverUrl;
        }
        String host;
        try {
            host = URI.create(coverUrl).getHost();
        } catch (IllegalArgumentException exception) {
            return coverUrl;
        }
        if (host == null) {
            return coverUrl;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (!normalized.equals("music.126.net") && !normalized.endsWith(".music.126.net")) {
            return coverUrl;
        }
        return coverUrl + "?param=" + REQUEST_DIMENSION + "y" + REQUEST_DIMENSION;
    }

    private static String hashUrl(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    private record CacheEntry(Identifier textureId, TextureResources resources, boolean loading,
                              boolean failed, long token, long stampMillis) {
        public static CacheEntry pending(long token) {
            return new CacheEntry(null, null, true, false, token, System.currentTimeMillis());
        }

        public static CacheEntry ready(Identifier textureId, TextureResources resources) {
            return new CacheEntry(textureId, resources, false, false, 0L, System.currentTimeMillis());
        }

        public static CacheEntry failure(long token) {
            return new CacheEntry(null, null, false, true, token, System.currentTimeMillis());
        }

        /** 下载卡住（超过 {@link #PENDING_TIMEOUT_MILLIS} 仍未完成）：允许重新发起。 */
        public boolean isStale() {
            return System.currentTimeMillis() - stampMillis > PENDING_TIMEOUT_MILLIS;
        }

        /** 失败后经过冷却期才允许重试，避免离线时每帧都发一次必然失败的请求。 */
        public boolean canRetry() {
            return failed && System.currentTimeMillis() - stampMillis >= RETRY_COOLDOWN_MILLIS;
        }
    }
}
