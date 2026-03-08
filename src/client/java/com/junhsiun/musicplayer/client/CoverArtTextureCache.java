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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CoverArtTextureCache {
    private static final int MAX_CACHE_SIZE = 32;
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
        if (coverUrl == null || coverUrl.isBlank()) {
            return;
        }
        CacheEntry existing = entries.get(coverUrl);
        if (existing != null) {
            return;
        }
        MusicPlayerMod.LOGGER.info("Requesting cover art: {}", coverUrl);
        entries.put(coverUrl, CacheEntry.pending());
        executor.execute(() -> downloadAndRegister(coverUrl));
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

    private void downloadAndRegister(String coverUrl) {
        try {
            OkHttpClient client = HttpClientFactory.create();
            Request request = new Request.Builder()
                    .url(coverUrl)
                    .header("User-Agent", "MinecraftMusicPlayer/2.0")
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
                bytes = body.bytes();
            }

            try (NativeImage image = readImage(bytes)) {
                NativeImage processedImage = toCircularTexture(image);
                Identifier textureId = Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "cover/" + Integer.toHexString(coverUrl.hashCode()));
                Minecraft.getInstance().execute(() -> registerTexture(coverUrl, textureId, processedImage));
            }
        } catch (Exception exception) {
            synchronized (this) {
                entries.put(coverUrl, CacheEntry.failure());
            }
            MusicPlayerMod.LOGGER.warn("Failed to download cover art: {}", coverUrl, exception);
        }
    }

    private synchronized void registerTexture(String coverUrl, Identifier textureId, NativeImage image) {
        Minecraft client = Minecraft.getInstance();
        try {
            DynamicTexture texture = new DynamicTexture(() -> "musicplayer_cover", image);
            texture.upload();
            client.getTextureManager().register(textureId, texture);
            entries.put(coverUrl, CacheEntry.ready(textureId));
            MusicPlayerMod.LOGGER.info("Registered cover art texture: {} -> {}", coverUrl, textureId);

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
            NativeImage image = new NativeImage(bufferedImage.getWidth(), bufferedImage.getHeight(), true);
            for (int y = 0; y < bufferedImage.getHeight(); y++) {
                for (int x = 0; x < bufferedImage.getWidth(); x++) {
                    int argb = bufferedImage.getRGB(x, y);
                    image.setPixel(x, y, argb);
                }
            }
            return image;
        }
    }

    private static NativeImage toCircularTexture(NativeImage source) {
        int size = Math.max(1, Math.min(source.getWidth(), source.getHeight()));
        int offsetX = Math.max(0, (source.getWidth() - size) / 2);
        int offsetY = Math.max(0, (source.getHeight() - size) / 2);
        float center = (size - 1) / 2.0F;
        float radius = size / 2.0F;
        float feather = Math.max(1.5F, size * 0.03F);

        NativeImage result = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int color = source.getPixel(offsetX + x, offsetY + y);
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

    private static float clamp(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, value);
    }

    private record CacheEntry(Identifier textureId, boolean loading, boolean failed) {
        public static CacheEntry pending() {
            return new CacheEntry(null, true, false);
        }

        public static CacheEntry ready(Identifier textureId) {
            return new CacheEntry(textureId, false, false);
        }

        public static CacheEntry failure() {
            return new CacheEntry(null, false, true);
        }
    }
}
