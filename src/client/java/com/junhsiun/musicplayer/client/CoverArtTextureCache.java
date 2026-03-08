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

            try (NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes))) {
                DynamicTexture texture = new DynamicTexture(() -> "musicplayer_cover", image);
                Identifier textureId = Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "cover/" + Integer.toHexString(coverUrl.hashCode()));
                Minecraft.getInstance().execute(() -> registerTexture(coverUrl, textureId, texture));
            }
        } catch (Exception exception) {
            synchronized (this) {
                entries.remove(coverUrl);
            }
            MusicPlayerMod.LOGGER.warn("Failed to download cover art: {}", coverUrl, exception);
        }
    }

    private synchronized void registerTexture(String coverUrl, Identifier textureId, DynamicTexture texture) {
        Minecraft client = Minecraft.getInstance();
        texture.upload();
        client.getTextureManager().register(textureId, texture);
        entries.put(coverUrl, CacheEntry.ready(textureId));

        while (entries.size() > MAX_CACHE_SIZE) {
            String eldestKey = entries.keySet().iterator().next();
            CacheEntry eldest = entries.remove(eldestKey);
            if (eldest != null && eldest.textureId() != null) {
                client.getTextureManager().release(eldest.textureId());
            }
        }
    }

    private record CacheEntry(Identifier textureId, boolean loading) {
        public static CacheEntry pending() {
            return new CacheEntry(null, true);
        }

        public static CacheEntry ready(Identifier textureId) {
            return new CacheEntry(textureId, false);
        }
    }
}
