package com.junhsiun.musicplayer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.platform.api.MusicApiMode;
import com.junhsiun.musicplayer.util.FileAtomicWrite;
import com.junhsiun.musicplayer.util.Java8;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class MusicPlayerConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("minecraft-music-player.json");
    private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("music-player-config.json");

    private static volatile MusicPlayerConfig config = new MusicPlayerConfig();

    private MusicPlayerConfigManager() {
    }

    public static void load() {
        try {
            if (Files.notExists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    config = GSON.fromJson(reader, MusicPlayerConfig.class);
                }
                if (config == null) {
                    config = new MusicPlayerConfig();
                }
            } else {
                config = new MusicPlayerConfig();
            }

            migrateLegacyDefaults();
            migrateLegacyConfigFile();
            save();
        } catch (Exception exception) {
            MusicPlayerMod.LOGGER.error("加载配置失败，将使用默认配置。", exception);
            config = new MusicPlayerConfig();
        }
    }

    public static void save() {
        try {
            FileAtomicWrite.writeString(CONFIG_PATH, GSON.toJson(config));
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.error("保存配置失败。", exception);
        }
    }

    public static MusicPlayerConfig get() {
        return config;
    }

    public static void resetAll() {
        config = new MusicPlayerConfig();
        save();
    }

    private static void migrateLegacyDefaults() {
        config.neteaseBaseUrl = MusicPlayerConfig.normalizeBaseUrl(config.neteaseBaseUrl);
        if (Java8.isBlank(config.neteaseBaseUrl)
                || "http://127.0.0.1:3000".equalsIgnoreCase(config.neteaseBaseUrl)) {
            config.neteaseBaseUrl = MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL;
        }
        if (MusicApiMode.normalize(config.apiMode) == null) {
            config.apiMode = MusicPlayerConfig.DEFAULT_API_MODE;
        } else {
            config.apiMode = MusicApiMode.normalize(config.apiMode);
        }
        if (config.proxy == null) {
            config.proxy = "";
        }
        if (config.bgmPlaylistId == null) {
            config.bgmPlaylistId = MusicPlayerConfig.DEFAULT_BGM_PLAYLIST_ID;
        }
        config.bgmPlaylistId = config.bgmPlaylistId.trim();
        if (config.connectTimeoutSeconds <= 0) {
            config.connectTimeoutSeconds = 10;
        }
        if (config.readTimeoutSeconds <= 0) {
            config.readTimeoutSeconds = 20;
        }
        if (config.queueCacheSize < 0) {
            config.queueCacheSize = 3;
        }
        if (config.maxQueueSize <= 0) {
            config.maxQueueSize = 40;
        }
        if (config.maxSongsPerPlayer <= 0) {
            config.maxSongsPerPlayer = 5;
        }
        if (config.lootMusicDiscChance < 0D || config.lootMusicDiscChance > 1D) {
            config.lootMusicDiscChance = 0.3D;
        }
        if (config.lootMusicDiscCount < 0) {
            config.lootMusicDiscCount = 1;
        }
    }

    private static void migrateLegacyConfigFile() {
        if (!Files.exists(LEGACY_CONFIG_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(LEGACY_CONFIG_PATH)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            JsonElement legacyProxy = root.get("proxy");
            if (legacyProxy != null && legacyProxy.isJsonPrimitive()) {
                String proxyValue = legacyProxy.getAsString();
                if ((config.proxy == null || Java8.isBlank(config.proxy)) && proxyValue != null && Java8.isNotBlank(proxyValue)) {
                    config.proxy = proxyValue.trim();
                }
            }

            JsonElement platformBaseUrl = root.get("platformBaseUrl");
            if (platformBaseUrl != null && platformBaseUrl.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : platformBaseUrl.getAsJsonObject().entrySet()) {
                    String value = entry.getValue().getAsString();
                    if ((config.neteaseBaseUrl == null
                            || Java8.isBlank(config.neteaseBaseUrl)
                            || MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL.equals(config.neteaseBaseUrl)
                            || "http://127.0.0.1:3000".equalsIgnoreCase(config.neteaseBaseUrl))
                            && value != null && !Java8.isBlank(value)) {
                        config.neteaseBaseUrl = MusicPlayerConfig.normalizeBaseUrl(value);
                    }
                }
            }
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.warn("读取旧版配置失败，将继续使用当前配置。", exception);
        } catch (IllegalStateException | ClassCastException | com.google.gson.JsonParseException exception) {
            MusicPlayerMod.LOGGER.warn("旧版配置文件格式无效，已跳过。", exception);
        }
    }
}
