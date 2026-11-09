package com.junhsiun.musicplayer.service;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.network.MusicConfigSnapshotPayload;
import com.junhsiun.musicplayer.platform.api.MusicApiMode;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端配置的 UI 同步。
 *
 * <p>设置页在客户端，但服务端字段必须由服务端持有并落盘。这里提供两件事：</p>
 * <ul>
 *   <li>把当前服务端配置打包成键值快照发给客户端；</li>
 *   <li>接收客户端发来的单字段修改，校验管理员权限与取值范围后写入配置。</li>
 * </ul>
 *
 * <p>只接受白名单键。这样即使客户端被改过，也不能借这个通道写任意字段或注入未知配置。</p>
 */
public final class ServerConfigSync {

    private ServerConfigSync() {
    }

    /** 生成当前配置快照。 */
    public static MusicConfigSnapshotPayload snapshot(ServerPlayer player) {
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("neteaseBaseUrl", config.neteaseBaseUrl);
        values.put("apiMode", config.apiMode);
        values.put("bgmPlaylistId", config.bgmPlaylistId);
        values.put("proxy", config.proxy);
        values.put("useSystemProxy", Boolean.toString(config.useSystemProxy));
        values.put("preferIpv4", Boolean.toString(config.preferIpv4));
        values.put("allowCustomServer", Boolean.toString(config.allowCustomServer));
        values.put("allowSongRequest", Boolean.toString(config.allowSongRequest));
        values.put("allowPlaylistRequest", Boolean.toString(config.allowPlaylistRequest));
        values.put("autoAdvance", Boolean.toString(config.autoAdvance));
        values.put("announceQueueChanges", Boolean.toString(config.announceQueueChanges));
        values.put("showLoadingHints", Boolean.toString(config.showLoadingHints));
        values.put("showLyrics", Boolean.toString(config.showLyrics));
        values.put("connectTimeoutSeconds", Integer.toString(config.connectTimeoutSeconds));
        values.put("readTimeoutSeconds", Integer.toString(config.readTimeoutSeconds));
        values.put("searchLimit", Integer.toString(config.searchLimit));
        values.put("maxQueueSize", Integer.toString(config.maxQueueSize));
        values.put("maxSongsPerPlayer", Integer.toString(config.maxSongsPerPlayer));
        values.put("playlistQueueLimit", Integer.toString(config.playlistQueueLimit));
        values.put("queueCacheSize", Integer.toString(config.queueCacheSize));
        values.put("voteSkipPercent", Double.toString(config.voteSkipPercent));
        values.put("enableLootMusicDiscs", Boolean.toString(config.enableLootMusicDiscs));
        values.put("lootMusicDiscChance", Double.toString(config.lootMusicDiscChance));
        values.put("lootMusicDiscCount", Integer.toString(config.lootMusicDiscCount));
        return new MusicConfigSnapshotPayload(canEdit(player), values);
    }

    public static boolean canEdit(ServerPlayer player) {
        return player != null && player.createCommandSourceStack().permissions()
                .hasPermission(Permissions.COMMANDS_ADMIN);
    }

    /** 发给单个玩家。 */
    public static void send(ServerPlayer player) {
        if (player == null || !ServerPlayNetworking.canSend(player, MusicConfigSnapshotPayload.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(player, snapshot(player));
    }

    /** 发给所有在线玩家（修改后让所有人的设置页同步刷新）。 */
    public static void sendToAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    /**
     * 应用一个字段修改。
     *
     * @return 是否真的写入（权限不足、键未知、值非法都会返回 false）
     */
    public static boolean apply(ServerPlayer player, String key, String rawValue) {
        if (player == null || key == null || !canEdit(player)) {
            return false;
        }
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        // 管理页的「恢复默认」：只重置根上的服务端字段，客户端 / 外观分节保持用户当前选择。
        if ("resetServerDefaults".equals(key)) {
            config.resetServerToDefaults();
            MusicPlayerConfigManager.save();
            MusicPlayerMod.queueService().clearTrackCache();
            MusicPlayerMod.queueService().refreshCacheSettings();
            MusicPlayerMod.musicApiRouter().resetHealth();
            MusicPlayerMod.syncBgmConfig(player.level().getServer());
            sendToAll(player.level().getServer());
            return true;
        }
        String value = rawValue == null ? "" : rawValue.trim();
        boolean cacheChanged = false;
        boolean apiChanged = false;
        boolean bgmChanged = false;
        switch (key) {
            case "neteaseBaseUrl" -> {
                String normalized = MusicPlayerConfig.normalizeBaseUrl(value);
                if (normalized.isEmpty()) {
                    return false;
                }
                config.neteaseBaseUrl = normalized;
                cacheChanged = true;
            }
            case "apiMode" -> {
                String normalized = MusicApiMode.normalize(value);
                if (normalized == null) {
                    return false;
                }
                config.apiMode = normalized;
                cacheChanged = true;
                apiChanged = true;
            }
            case "bgmPlaylistId" -> {
                String normalized = normalizeBgmPlaylist(value);
                if (normalized == null) {
                    return false;
                }
                config.bgmPlaylistId = normalized;
                bgmChanged = true;
            }
            case "proxy" -> config.proxy = "none".equalsIgnoreCase(value) ? "" : value;
            case "useSystemProxy" -> config.useSystemProxy = parseBool(value, config.useSystemProxy);
            case "preferIpv4" -> config.preferIpv4 = parseBool(value, config.preferIpv4);
            case "allowCustomServer" -> config.allowCustomServer = parseBool(value, config.allowCustomServer);
            case "allowSongRequest" -> config.allowSongRequest = parseBool(value, config.allowSongRequest);
            case "allowPlaylistRequest" -> config.allowPlaylistRequest = parseBool(value, config.allowPlaylistRequest);
            case "autoAdvance" -> config.autoAdvance = parseBool(value, config.autoAdvance);
            case "announceQueueChanges" -> config.announceQueueChanges = parseBool(value, config.announceQueueChanges);
            case "showLoadingHints" -> config.showLoadingHints = parseBool(value, config.showLoadingHints);
            case "showLyrics" -> config.showLyrics = parseBool(value, config.showLyrics);
            case "connectTimeoutSeconds" -> config.connectTimeoutSeconds =
                    parseInt(value, 3, 60, config.connectTimeoutSeconds);
            case "readTimeoutSeconds" -> config.readTimeoutSeconds =
                    parseInt(value, 3, 120, config.readTimeoutSeconds);
            case "searchLimit" -> config.searchLimit = parseInt(value, 3, 20, config.searchLimit);
            case "maxQueueSize" -> config.maxQueueSize = parseInt(value, 1, 200, config.maxQueueSize);
            case "maxSongsPerPlayer" -> config.maxSongsPerPlayer =
                    parseInt(value, 1, 50, config.maxSongsPerPlayer);
            case "playlistQueueLimit" -> config.playlistQueueLimit =
                    parseInt(value, 1, 100, config.playlistQueueLimit);
            case "queueCacheSize" -> {
                int parsed = parseInt(value, 0, 20, config.queueCacheSize);
                if (parsed != config.queueCacheSize) {
                    config.queueCacheSize = parsed;
                    cacheChanged = true;
                }
            }
            case "voteSkipPercent" -> config.voteSkipPercent =
                    parseDouble(value, 0.1D, 1.0D, config.voteSkipPercent);
            case "enableLootMusicDiscs" -> config.enableLootMusicDiscs =
                    parseBool(value, config.enableLootMusicDiscs);
            case "lootMusicDiscChance" -> config.lootMusicDiscChance =
                    parseDouble(value, 0.0D, 1.0D, config.lootMusicDiscChance);
            case "lootMusicDiscCount" -> config.lootMusicDiscCount =
                    parseInt(value, 0, 5, config.lootMusicDiscCount);
            default -> {
                return false;
            }
        }

        MusicPlayerConfigManager.save();
        if (cacheChanged) {
            MusicPlayerMod.queueService().clearTrackCache();
            MusicPlayerMod.queueService().refreshCacheSettings();
        }
        if (apiChanged) {
            MusicPlayerMod.musicApiRouter().resetHealth();
        }
        if (bgmChanged) {
            MusicPlayerMod.syncBgmConfig(player.level().getServer());
        }
        sendToAll(player.level().getServer());
        return true;
    }

    private static boolean parseBool(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    private static int parseInt(String value, int min, int max, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed < min || parsed > max ? fallback : parsed;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double min, double max, double fallback) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || parsed < min || parsed > max) {
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /** {@code null} 表示非法；空串 / {@code off} 表示关闭。 */
    private static String normalizeBgmPlaylist(String value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty() || "off".equalsIgnoreCase(value)) {
            return "";
        }
        if ("reset".equalsIgnoreCase(value)) {
            return MusicPlayerConfig.DEFAULT_BGM_PLAYLIST_ID;
        }
        if (value.length() <= 20 && value.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            return value;
        }
        return null;
    }
}
