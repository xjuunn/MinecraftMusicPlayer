package com.junhsiun.musicplayer.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.junhsiun.musicplayer.MusicPlayerMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MusicPlayerConfigManager {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
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
                config = MAPPER.readValue(CONFIG_PATH.toFile(), MusicPlayerConfig.class);
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
            MAPPER.writeValue(CONFIG_PATH.toFile(), config);
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
        if (!config.useSystemProxy && (config.proxy == null || config.proxy.isBlank())) {
            config.useSystemProxy = true;
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
        try {
            JsonNode root = MAPPER.readTree(LEGACY_CONFIG_PATH.toFile());
            String legacyProxy = root.path("proxy").asText("");
            if ((config.proxy == null || config.proxy.isBlank()) && legacyProxy != null && !legacyProxy.isBlank()) {
                config.proxy = legacyProxy.trim();
            }

            JsonNode platformBaseUrl = root.path("platformBaseUrl");
            if (platformBaseUrl.isObject()) {
                platformBaseUrl.fields().forEachRemaining(entry -> {
                    String value = entry.getValue().asText("");
                    if ((config.neteaseBaseUrl == null
                            || config.neteaseBaseUrl.isBlank()
                            || MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL.equals(config.neteaseBaseUrl)
                            || "http://127.0.0.1:3000".equalsIgnoreCase(config.neteaseBaseUrl))
                            && value != null && !value.isBlank()) {
                        config.neteaseBaseUrl = value.trim();
                    }
                });
            }
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.warn("读取旧版配置失败，将继续使用当前配置。", exception);
        }
    }
}
