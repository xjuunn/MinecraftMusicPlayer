package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import com.junhsiun.musicplayer.util.Java8;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
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
    private static final CoverArtTextureCache INSTANCE = new CoverArtTextureCache();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "musicplayer-cover-cache");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, CacheEntry> entries = new LinkedHashMap<>(16, 0.75F, true);

    private CoverArtTextureCache() {
    }

    public static CoverArtTextureCache getInstance() {
        return INSTANCE;
    }

    public synchronized void request(String coverUrl) {
        if (coverUrl == null || Java8.isBlank(coverUrl)) {
            return;
        }
        if (executor.isShutdown()) {
            return;
        }
        CacheEntry existing = entries.get(coverUrl);
        if (existing != null) {
            return;
        }
        MusicPlayerMod.LOGGER.debug("Requesting cover art: {}", coverUrl);
        entries.put(coverUrl, CacheEntry.pending());
        try {
            executor.execute(() -> downloadAndRegister(coverUrl));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            entries.remove(coverUrl);
        }
    }

    public synchronized ResourceLocation getTextureId(String coverUrl) {
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

    private void downloadAndRegister(String coverUrl) {
        try {
            OkHttpClient client = HttpClientFactory.create();
            Request request = new Request.Builder()
                    .url(coverUrl)
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
                ResourceLocation textureId = new ResourceLocation(MusicPlayerMod.MOD_ID, "cover/" + hashUrl(coverUrl));
                Minecraft.getInstance().execute(() -> registerTexture(coverUrl, textureId, processedImage));
            }
        } catch (Exception exception) {
            synchronized (this) {
                if (entries.containsKey(coverUrl)) {
                    entries.put(coverUrl, CacheEntry.failure());
                }
            }
            MusicPlayerMod.LOGGER.warn("Failed to download cover art: {}", coverUrl, exception);
        }
    }

    private synchronized void registerTexture(String coverUrl, ResourceLocation textureId, NativeImage image) {
        Minecraft client = Minecraft.getInstance();
        try {
            // clear() 期间在途下载完成：直接释放纹理，避免泄漏
            CacheEntry current = entries.get(coverUrl);
            if (current == null || !current.loading()) {
                client.getTextureManager().release(textureId);
                image.close();
                return;
            }
            DynamicTexture texture = new DynamicTexture(image);
            texture.upload();
            client.getTextureManager().register(textureId, texture);
            entries.put(coverUrl, CacheEntry.ready(textureId));
            MusicPlayerMod.LOGGER.debug("Registered cover art texture: {} -> {}", coverUrl, textureId);

            while (entries.size() > MAX_CACHE_SIZE) {
                String eldestKey = entries.keySet().iterator().next();
                CacheEntry eldest = entries.remove(eldestKey);
                if (eldest != null && eldest.textureId() != null) {
                    client.getTextureManager().release(eldest.textureId());
                }
            }
        } catch (Exception exception) {
            entries.put(coverUrl, CacheEntry.failure());
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
                    image.setPixelRGBA(x, y, argb);
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
                int color = source.getPixelRGBA(srcX, srcY);
                float dx = x - center;
                float dy = y - center;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float alphaFactor = clamp((radius - distance) / feather);
                if (alphaFactor <= 0.0F) {
                    result.setPixelRGBA(x, y, 0x00000000);
                    continue;
                }
                int alpha = (color >>> 24) & 0xFF;
                int red = (color >>> 16) & 0xFF;
                int green = (color >>> 8) & 0xFF;
                int blue = color & 0xFF;
                int maskedAlpha = Math.min(255, Math.max(0, Math.round(alpha * alphaFactor)));
                result.setPixelRGBA(x, y, (maskedAlpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return result;
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

    private static final class CacheEntry {
        private final ResourceLocation textureId;
        private final boolean loading;
        private final boolean failed;

        private CacheEntry(ResourceLocation textureId, boolean loading, boolean failed) {
            this.textureId = textureId;
            this.loading = loading;
            this.failed = failed;
        }

        public static CacheEntry pending() {
            return new CacheEntry(null, true, false);
        }

        public static CacheEntry ready(ResourceLocation textureId) {
            return new CacheEntry(textureId, false, false);
        }

        public static CacheEntry failure() {
            return new CacheEntry(null, false, true);
        }

        public ResourceLocation textureId() {
            return textureId;
        }

        public boolean loading() {
            return loading;
        }

        public boolean failed() {
            return failed;
        }
    }
}
