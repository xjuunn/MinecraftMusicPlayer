package com.junhsiun.musicplayer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.junhsiun.musicplayer.MusicPlayerMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MusicPlayerConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("minecraft-music-player.json");
    private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("music-player-config.json");

    private static MusicPlayerConfig config = new MusicPlayerConfig();

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
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.error("加载配置失败，将使用默认配置。", exception);
            config = new MusicPlayerConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.error("保存配置失败。", exception);
        }
    }

    public static MusicPlayerConfig get() {
        return config;
    }

    private static void migrateLegacyDefaults() {
        if (config.neteaseBaseUrl == null || config.neteaseBaseUrl.isBlank()
                || "http://127.0.0.1:3000".equalsIgnoreCase(config.neteaseBaseUrl.trim())) {
            config.neteaseBaseUrl = MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL;
        }
        if (config.proxy == null) {
            config.proxy = "";
        }
        if (config.proxy == null) {
            config.proxy = "";
            if (!config.useSystemProxy) {
                config.useSystemProxy = true;
            }
        }
        if (config.connectTimeoutSeconds <= 0) {
            config.connectTimeoutSeconds = 10;
        }
        if (config.readTimeoutSeconds <= 0) {
            config.readTimeoutSeconds = 20;
        }
        if (config.queueCacheSize < 0) {
            config.queueCacheSize = 3;
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
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonElement legacyProxy = root.get("proxy");
            if (legacyProxy != null && legacyProxy.isJsonPrimitive()) {
                String proxyValue = legacyProxy.getAsString();
                if ((config.proxy == null || config.proxy.isBlank()) && proxyValue != null && !proxyValue.isBlank()) {
                    config.proxy = proxyValue.trim();
                }
            }

            JsonElement platformBaseUrl = root.get("platformBaseUrl");
            if (platformBaseUrl != null && platformBaseUrl.isJsonObject()) {
                for (var entry : platformBaseUrl.getAsJsonObject().entrySet()) {
                    String value = entry.getValue().getAsString();
                    if ((config.neteaseBaseUrl == null
                            || config.neteaseBaseUrl.isBlank()
                            || MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL.equals(config.neteaseBaseUrl)
                            || "http://127.0.0.1:3000".equalsIgnoreCase(config.neteaseBaseUrl))
                            && value != null && !value.isBlank()) {
                        config.neteaseBaseUrl = value.trim();
                    }
                }
            }
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.warn("读取旧版配置失败，将继续使用当前配置。", exception);
        } catch (IllegalStateException | ClassCastException exception) {
            MusicPlayerMod.LOGGER.warn("旧版配置文件格式无效，已跳过。", exception);
        }
    }
}
