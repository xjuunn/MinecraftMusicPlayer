package com.junhsiun.musicplayer.config;

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
                save();
            }
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
}