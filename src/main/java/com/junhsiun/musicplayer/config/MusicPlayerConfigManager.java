package com.junhsiun.musicplayer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.platform.api.MusicApiMode;
import com.junhsiun.musicplayer.util.FileAtomicWrite;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组统一配置管理：<b>客户端与服务端共用一个配置文件</b>。
 *
 * <p>文件：{@code config/minecraft-music-player.json}。</p>
 *
 * <p>所有写入都经由 {@link #markDirty()} / {@link #save()} 汇到这里，所以
 * 不可能出现两个管理器抢着写同一个文件的情况 —— 单人游戏的内置服务端与客户端
 * 在同一个 JVM 里，共享本类的 {@code config} 实例。</p>
 *
 * <p>写盘分两条路径：</p>
 * <ul>
 *   <li>{@link #save()}：立即落盘，用于命令、开关等低频变更。</li>
 *   <li>{@link #markDirty()}：只打标记，由 {@link #tick()} 合并到下一次落盘，
 *       用于滑块拖动这类每帧都在变的高频变更。</li>
 * </ul>
 */
public final class MusicPlayerConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("minecraft-music-player.json");
    private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("music-player-config.json");
    /** 早期把客户端字段单独写在这个文件里，加载时一次性并进来。 */
    private static final Path LEGACY_CLIENT_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("minecraft-music-player-client.json");
    /** 写盘节流间隔（毫秒）：滑块拖动等高频变更合并到该间隔落盘一次。 */
    private static final long SAVE_COOLDOWN_MILLIS = 500L;

    private static volatile MusicPlayerConfig config = new MusicPlayerConfig();
    /** 是否存在未落盘的变更。 */
    private static volatile boolean savePending;
    private static volatile long lastSaveMillis;
    /** 是否已经加载过。主初始化与客户端初始化都会调 {@link #load()}，只真正读盘一次。 */
    private static volatile boolean loaded;

    private MusicPlayerConfigManager() {
    }

    public static void load() {
        // 双端初始化各自调一次 load()。重复读盘不仅浪费，还会把内存里尚未落盘的
        // 修改（markDirty 之后、tick 落盘之前）直接冲掉，所以做成幂等。
        if (loaded) {
            return;
        }
        loaded = true;
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

            // Gson 对旧文件里缺失的子节填 null，必须补上，否则后续访问空指针
            ensureSections();
            migrateLegacyDefaults();
            migrateLegacyConfigFile();
            migrateLegacyClientConfig();
            normalizeAll();
            save();
        } catch (Exception exception) {
            MusicPlayerMod.LOGGER.error("加载配置失败，将使用默认配置。", exception);
            config = new MusicPlayerConfig();
            normalizeAll();
        }
    }

    /** Gson 反序列化后补齐缺失的分节。 */
    private static void ensureSections() {
        if (config.client == null) {
            config.client = new MusicPlayerConfig.ClientSettings();
        }
        if (config.glass == null) {
            config.glass = new MusicPlayerConfig.GlassSettings();
        }
        if (config.idle == null) {
            config.idle = new MusicPlayerConfig.IdleSettings();
        }
    }

    /** 规范化<b>全部分节</b>。新增分节记得加到这里。 */
    private static void normalizeAll() {
        config.client.normalize();
        config.glass.normalize();
        config.idle.normalize();
    }

    /** 强制重新读取磁盘上的配置，供 {@code /musicplayer config reload} 使用。 */
    public static void reload() {
        loaded = false;
        load();
    }

    public static void save() {
        lastSaveMillis = System.currentTimeMillis();
        try {
            FileAtomicWrite.writeString(CONFIG_PATH, GSON.toJson(config));
            savePending = false;
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.error("保存配置失败。", exception);
        }
    }

    /** 高频变更（拖动滑块等）改用本方法标记，由 {@link #tick()} 合并写盘。 */
    public static void markDirty() {
        savePending = true;
    }

    /** 每客户端 tick 调用：将待写变更合并落盘，最长延迟 {@link #SAVE_COOLDOWN_MILLIS}。 */
    public static void tick() {
        if (!savePending) {
            return;
        }
        if (System.currentTimeMillis() - lastSaveMillis < SAVE_COOLDOWN_MILLIS) {
            return;
        }
        save();
    }

    /** 退出前调用，立即落盘最后一次未合并的变更（不等节流间隔）。 */
    public static void flushDirty() {
        if (savePending) {
            save();
        }
    }

    public static MusicPlayerConfig get() {
        return config;
    }

    /** 一键把<b>玻璃外观</b>恢复默认并立即落盘（不影响音量等其他设置）。 */
    public static void resetGlass() {
        config.glass.resetToDefaults();
        save();
    }

    /** 一键把<b>客户端设置</b>恢复默认并立即落盘。 */
    public static void resetClient() {
        config.client.resetToDefaults();
        save();
    }

    /** 一键把<b>挂机纯享</b>设置恢复默认并立即落盘。 */
    public static void resetIdle() {
        config.idle.resetToDefaults();
        save();
    }

    /** 一键把<b>全部</b>配置恢复默认并立即落盘（含服务端字段）。 */
    public static void resetAll() {
        config = new MusicPlayerConfig();
        normalizeAll();
        save();
    }

    private static void migrateLegacyDefaults() {
        config.neteaseBaseUrl = MusicPlayerConfig.normalizeBaseUrl(config.neteaseBaseUrl);
        if (config.neteaseBaseUrl.isBlank()
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

    /**
     * 早期客户端字段单独写在 {@code minecraft-music-player-client.json}。
     *
     * <p>只在<b>新文件里还没有这些值</b>时并入，避免覆盖用户已经在新配置里改过的设置。
     * 迁移成功后旧文件保留不删，用户可自行清理。</p>
     */
    private static void migrateLegacyClientConfig() {
        if (!Files.exists(LEGACY_CLIENT_CONFIG_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(LEGACY_CLIENT_CONFIG_PATH)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            readDouble(root, "glassOpacity", value -> config.glass.opacity = value);
            readDouble(root, "glassBlur", value -> config.glass.blur = value);
            readBoolean(root, "glassShadow", value -> config.glass.shadow = value);
            readDouble(root, "bgmVolume", value -> config.client.bgmVolume = value);
            readBoolean(root, "enableNetworkBgm", value -> config.client.enableNetworkBgm = value);
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.warn("读取旧版客户端配置失败，将继续使用当前配置。", exception);
        } catch (IllegalStateException | ClassCastException | com.google.gson.JsonParseException exception) {
            MusicPlayerMod.LOGGER.warn("旧版客户端配置文件格式无效，已跳过。", exception);
        }
    }

    private static void readDouble(JsonObject root, String key, java.util.function.DoubleConsumer setter) {
        JsonElement element = root.get(key);
        if (element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()) {
            setter.accept(element.getAsDouble());
        }
    }

    private static void readBoolean(JsonObject root, String key, java.util.function.Consumer<Boolean> setter) {
        JsonElement element = root.get(key);
        if (element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean()) {
            setter.accept(element.getAsBoolean());
        }
    }
}
