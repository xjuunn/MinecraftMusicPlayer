package com.junhsiun.musicplayer.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.junhsiun.musicplayer.MusicPlayerMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端本地配置管理：独立配置文件，不与服务端共享。
 * 音频播放线程会高频读取 {@link #get()}，因此 config 引用为 volatile。
 */
public final class MusicPlayerClientConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("minecraft-music-player-client.json");
    private static final Path LEGACY_SHARED_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("minecraft-music-player.json");

    private static volatile MusicPlayerClientConfig config = new MusicPlayerClientConfig();

    private MusicPlayerClientConfigManager() {
    }

    public static void load() {
        try {
            if (Files.notExists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    config = GSON.fromJson(reader, MusicPlayerClientConfig.class);
                }
                if (config == null) {
                    config = new MusicPlayerClientConfig();
                }
            } else {
                config = new MusicPlayerClientConfig();
                migrateFromLegacySharedConfig(config);
            }
            if (Double.isNaN(config.bgmVolume) || config.bgmVolume < 0.0D || config.bgmVolume > 1.0D) {
                config.bgmVolume = 0.7D;
            }
            save();
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.error("加载客户端配置失败，将使用默认配置。", exception);
            config = new MusicPlayerClientConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, GSON.toJson(config).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.error("保存客户端配置失败。", exception);
        }
    }

    public static MusicPlayerClientConfig get() {
        return config;
    }

    /** 旧版把客户端字段写在共享配置里：首次迁移时读出，之后由独立配置文件接管。 */
    private static void migrateFromLegacySharedConfig(MusicPlayerClientConfig target) {
        if (!Files.exists(LEGACY_SHARED_CONFIG_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(LEGACY_SHARED_CONFIG_PATH)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonElement volume = root.get("bgmVolume");
            if (volume != null && volume.isJsonPrimitive()) {
                target.bgmVolume = volume.getAsDouble();
            }
            JsonElement enabled = root.get("enableNetworkBgm");
            if (enabled != null && enabled.isJsonPrimitive()) {
                target.enableNetworkBgm = enabled.getAsBoolean();
            }
        } catch (IOException | IllegalStateException | ClassCastException exception) {
            MusicPlayerMod.LOGGER.warn("从旧版共享配置迁移客户端设置失败，将使用默认值。", exception);
        }
    }
}
