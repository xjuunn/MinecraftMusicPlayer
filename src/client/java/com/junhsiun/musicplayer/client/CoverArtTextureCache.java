package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
    /** 处理后纹理最大边长：唱片封面在世界中显示尺寸很小，上传也发生在渲染线程，必须封顶。 */
    private static final int MAX_PROCESSED_DIMENSION = 512;
    /**
     * 向图床要的边长。
     *
     * <p>唱片在世界里只有 0.68 格宽，256 已经够用；而原始封面常常是 1000+ 像素、
     * 几百 KB（实测有 616 KB 的），弱网下这就是"先黑几秒"的主要来源。
     * 网易图床支持 {@code ?param=WxH} 服务端缩放，同一张图 256 只要几十 KB。</p>
     */
    private static final int REQUEST_DIMENSION = 256;
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
            if (entry.textureId() != null) {
                client.execute(() -> client.getTextureManager().release(entry.textureId()));
            }
        }
        entries.clear();
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
                NativeImage processedImage = toCircularTexture(image);
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
                client.getTextureManager().release(textureId);
                image.close();
                return;
            }
            DynamicTexture texture = new DynamicTexture(() -> "musicplayer_cover", image);
            texture.upload();
            client.getTextureManager().register(textureId, texture);
            entries.put(coverUrl, CacheEntry.ready(textureId));
            // 用 INFO 而不是 DEBUG：这一行是"封面到底有没有下载成功"的唯一证据。
            // 默认日志级别下 DEBUG 不会打印，出问题时就看不出是没下载、还是下载了但没画出来。
            // 每个 URL 每个会话只会打印一次（命中缓存后 request 直接返回）。
            // 带上耗时，方便判断"黑几秒"到底是网络还是别的地方。
            MusicPlayerMod.LOGGER.info("Cover art ready in {} ms: {} -> {}",
                    (System.nanoTime() - startedNanos) / 1_000_000L, coverUrl, textureId);

            while (entries.size() > MAX_CACHE_SIZE) {
                String eldestKey = entries.keySet().iterator().next();
                CacheEntry eldest = entries.remove(eldestKey);
                if (eldest != null && eldest.textureId() != null) {
                    client.getTextureManager().release(eldest.textureId());
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

    private static NativeImage toCircularTexture(NativeImage source) {
        int srcSize = Math.max(1, Math.min(source.getWidth(), source.getHeight()));
        int offsetX = Math.max(0, (source.getWidth() - srcSize) / 2);
        int offsetY = Math.max(0, (source.getHeight() - srcSize) / 2);
        int size = Math.min(srcSize, MAX_PROCESSED_DIMENSION);
        float center = (size - 1) / 2.0F;
        float radius = size / 2.0F;
        float feather = Math.max(1.5F, size * 0.03F);

        NativeImage result = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            int srcY = offsetY + y * srcSize / size;
            for (int x = 0; x < size; x++) {
                int srcX = offsetX + x * srcSize / size;
                int color = source.getPixel(srcX, srcY);
                float dx = x - center;
                float dy = y - center;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float alphaFactor = clamp((radius - distance) / feather);
                if (alphaFactor <= 0.0F) {
                    result.setPixel(x, y, 0x00000000);
                    continue;
                }
                int alpha = (color >>> 24) & 0xFF;
                int red = (color >>> 16) & 0xFF;
                int green = (color >>> 8) & 0xFF;
                int blue = color & 0xFF;
                int maskedAlpha = Math.min(255, Math.max(0, Math.round(alpha * alphaFactor)));
                result.setPixel(x, y, (maskedAlpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return result;
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

    private static float clamp(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, value);
    }

    private record CacheEntry(Identifier textureId, boolean loading, boolean failed, long token, long stampMillis) {
        public static CacheEntry pending(long token) {
            return new CacheEntry(null, true, false, token, System.currentTimeMillis());
        }

        public static CacheEntry ready(Identifier textureId) {
            return new CacheEntry(textureId, false, false, 0L, System.currentTimeMillis());
        }

        public static CacheEntry failure(long token) {
            return new CacheEntry(null, false, true, token, System.currentTimeMillis());
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
