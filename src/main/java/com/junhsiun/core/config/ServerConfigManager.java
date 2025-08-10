package com.junhsiun.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junhsiun.core.utils.OkHttpUtil;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ServerConfigManager {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static ServerConfig config;
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("music-player-config.json");

    public static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                config = mapper.readValue(CONFIG_PATH.toFile(), ServerConfig.class);
            } else {
                config = new ServerConfig();
                saveConfig();
            }
        } catch (IOException e) {
            e.printStackTrace();
            config = new ServerConfig();
        }
        String proxy = config.proxy;
        OkHttpUtil.setProxy(proxy);
    }

    public static void saveConfig() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(CONFIG_PATH.toFile(), config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ServerConfig getConfig() {
        return config;
    }
}
