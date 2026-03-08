package com.junhsiun.musicplayer.command;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.model.ArtistInfo;
import com.junhsiun.musicplayer.model.PlaylistInfo;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.UserPlaylistView;
import com.junhsiun.musicplayer.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

public final class MusicCommands {
    private MusicCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        dispatcher.register(
                Commands.literal("music")
                        .executes(commandContext -> sendHelp(commandContext.getSource()))
                        .then(help())
                        .then(now())
                        .then(queue())
                        .then(join())
                        .then(leave())
                        .then(muteOnce())
                        .then(voteNext())
                        .then(play())
                        .then(search())
                        .then(view())
                        .then(next())
                        .then(stop())
                        .then(admin())
        );
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> help() {
        return Commands.literal("help").executes(context -> sendHelp(context.getSource()));
    }

    private static int sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Minecraft Music Player").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("/music now").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music queue").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music join | /music leave | /music mute once").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music vote next").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music play song <歌曲ID>").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music play playlist <歌单ID>").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music search song <关键词>").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music search song page <页码> <关键词>").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music search artist <关键词>").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music search playlist <关键词>").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music search user <关键词>").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music view artist <作者ID> | playlist <歌单ID> | user <用户ID>").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> now() {
        return Commands.literal("now").executes(context -> {
            context.getSource().sendSuccess(MusicPlayerMod.queueService()::describeNowPlaying, false);
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> queue() {
        return Commands.literal("queue").executes(context -> {
            List<Component> lines = MusicPlayerMod.queueService().describeQueue();
            lines.forEach(line -> context.getSource().sendSuccess(() -> line, false));
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> join() {
        return Commands.literal("join").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            MusicPlayerMod.queueService().joinPlayer(player);
            Messages.success(context.getSource(), "你已加入当前播放。", false);
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> leave() {
        return Commands.literal("leave").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            MusicPlayerMod.queueService().leavePlayer(player);
            Messages.success(context.getSource(), "你已退出当前播放。", false);
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> muteOnce() {
        return Commands.literal("mute").then(Commands.literal("once").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            MusicPlayerMod.queueService().leavePlayer(player);
            Messages.success(context.getSource(), "你已停止收听当前歌曲，可用 /music join 重新加入。", false);
            return 1;
        }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> voteNext() {
        return Commands.literal("vote").then(Commands.literal("next").executes(context -> {
            MusicPlayerMod.queueService().voteSkip(context.getSource().getServer(), context.getSource().getPlayerOrException());
            return 1;
        }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> play() {
        return Commands.literal("play")
                .then(Commands.literal("song")
                        .then(Commands.argument("song_id", StringArgumentType.string()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String songId = StringArgumentType.getString(context, "song_id");
                            loading(context.getSource(), MusicPlayerMod.queueService().isPlaying()
                                    ? "正在解析音乐并加入队列..."
                                    : "正在解析音乐并准备播放...");
                            MusicPlayerMod.queueService().requestSong(context.getSource().getServer(), context.getSource(), player, songId);
                            return 1;
                        })))
                .then(Commands.literal("playlist")
                        .then(Commands.argument("playlist_id", StringArgumentType.string()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String playlistId = StringArgumentType.getString(context, "playlist_id");
                            loading(context.getSource(), "正在加载歌单，请稍候...");
                            MusicPlayerMod.queueService().requestPlaylist(context.getSource().getServer(), context.getSource(), player, playlistId);
                            return 1;
                        })));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> search() {
        return Commands.literal("search")
                .then(pagedSearch("song", "正在搜索歌曲，请稍候...", (source, keyword, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchSongs(keyword, page)
                            .whenComplete((results, throwable) -> server.execute(() -> sendSongResults(source, keyword, page, results, throwable)));
                }))
                .then(pagedSearch("artist", "正在搜索作者，请稍候...", (source, keyword, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchArtists(keyword, page)
                            .whenComplete((results, throwable) -> server.execute(() -> sendArtistResults(source, keyword, page, results, throwable)));
                }))
                .then(pagedSearch("author", "正在搜索作者，请稍候...", (source, keyword, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchArtists(keyword, page)
                            .whenComplete((results, throwable) -> server.execute(() -> sendArtistResults(source, keyword, page, results, throwable)));
                }))
                .then(pagedSearch("playlist", "正在搜索歌单，请稍候...", (source, keyword, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchPlaylists(keyword, page)
                            .whenComplete((results, throwable) -> server.execute(() -> sendPlaylistResults(source, keyword, page, results, throwable)));
                }))
                .then(pagedSearch("user", "正在搜索用户，请稍候...", (source, keyword, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchUsers(keyword, page)
                            .whenComplete((results, throwable) -> server.execute(() -> sendUserResults(source, keyword, page, results, throwable)));
                }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> view() {
        return Commands.literal("view")
                .then(Commands.literal("playlist").then(Commands.argument("playlist_id", StringArgumentType.string()).executes(context -> {
                    String playlistId = StringArgumentType.getString(context, "playlist_id");
                    loading(context.getSource(), "正在加载歌单详情...");
                    MinecraftServer server = context.getSource().getServer();
                    MusicPlayerMod.netease().playlistDetail(playlistId).whenComplete((playlist, throwable) -> server.execute(() -> showPlaylist(context.getSource(), playlist, throwable)));
                    return 1;
                })))
                .then(Commands.literal("user").then(Commands.argument("user_id", StringArgumentType.string()).executes(context -> {
                    String userId = StringArgumentType.getString(context, "user_id");
                    loading(context.getSource(), "正在加载用户歌单...");
                    MinecraftServer server = context.getSource().getServer();
                    MusicPlayerMod.netease().userPlaylists(userId).whenComplete((user, throwable) -> server.execute(() -> showUserPlaylists(context.getSource(), user, throwable)));
                    return 1;
                })))
                .then(Commands.literal("artist").then(Commands.argument("artist_id", StringArgumentType.string()).executes(context -> {
                    String artistId = StringArgumentType.getString(context, "artist_id");
                    loading(context.getSource(), "正在加载作者详情...");
                    MinecraftServer server = context.getSource().getServer();
                    MusicPlayerMod.netease().artistDetail(artistId).whenComplete((artist, throwable) -> server.execute(() -> showArtist(context.getSource(), artist, throwable)));
                    return 1;
                })))
                .then(Commands.literal("author").then(Commands.argument("artist_id", StringArgumentType.string()).executes(context -> {
                    String artistId = StringArgumentType.getString(context, "artist_id");
                    loading(context.getSource(), "正在加载作者详情...");
                    MinecraftServer server = context.getSource().getServer();
                    MusicPlayerMod.netease().artistDetail(artistId).whenComplete((artist, throwable) -> server.execute(() -> showArtist(context.getSource(), artist, throwable)));
                    return 1;
                })));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> next() {
        return Commands.literal("next")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(context -> {
                    MusicPlayerMod.queueService().skipNow(context.getSource().getServer(), context.getSource());
                    return 1;
                });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> stop() {
        return Commands.literal("stop")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(context -> {
                    MusicPlayerMod.queueService().stop(context.getSource().getServer(), "管理员已停止播放。");
                    return 1;
                });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> admin() {
        return Commands.literal("admin")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .then(Commands.literal("reload").executes(context -> {
                    MusicPlayerConfigManager.load();
                    Messages.success(context.getSource(), "配置已重新加载。", false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {
                    MusicPlayerConfig config = MusicPlayerConfigManager.get();
                    Messages.info(context.getSource(), "当前网易云服务地址: " + config.neteaseBaseUrl, false);
                    Messages.info(context.getSource(), "歌曲点播: " + yesNo(config.allowSongRequest) + "，歌单点播: " + yesNo(config.allowPlaylistRequest), false);
                    Messages.info(context.getSource(), "自动切歌: " + yesNo(config.autoAdvance) + "，搜索加载提示: " + yesNo(config.showLoadingHints), false);
                    Messages.info(context.getSource(), "搜索上限: " + config.searchLimit + "，队列上限: " + config.maxQueueSize + "，歌单入列上限: " + config.playlistQueueLimit, false);
                    Messages.info(context.getSource(), "投票切歌阈值: " + config.voteSkipPercent, false);
                    return 1;
                }))
                .then(Commands.literal("clearqueue").executes(context -> {
                    MusicPlayerMod.queueService().clearQueue(context.getSource());
                    return 1;
                }))
                .then(Commands.literal("set")
                        .then(Commands.literal("baseUrl").then(Commands.argument("value", StringArgumentType.greedyString()).executes(context -> {
                            MusicPlayerConfig config = MusicPlayerConfigManager.get();
                            String value = StringArgumentType.getString(context, "value").trim();
                            if (!config.allowCustomServer && !"default".equalsIgnoreCase(value)
                                    && !com.junhsiun.musicplayer.config.MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL.equalsIgnoreCase(value)) {
                                Messages.warning(context.getSource(), "管理员已禁用自定义网易云服务地址。");
                                return 0;
                            }
                            config.neteaseBaseUrl = "default".equalsIgnoreCase(value)
                                    ? com.junhsiun.musicplayer.config.MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL
                                    : value;
                            MusicPlayerConfigManager.save();
                            Messages.success(context.getSource(), "网易云服务地址已更新为: " + config.neteaseBaseUrl, false);
                            return 1;
                        })))
                        .then(boolSetting("allowCustomServer", value -> MusicPlayerConfigManager.get().allowCustomServer = value))
                        .then(boolSetting("allowSongRequest", value -> MusicPlayerConfigManager.get().allowSongRequest = value))
                        .then(boolSetting("allowPlaylistRequest", value -> MusicPlayerConfigManager.get().allowPlaylistRequest = value))
                        .then(boolSetting("autoAdvance", value -> MusicPlayerConfigManager.get().autoAdvance = value))
                        .then(boolSetting("announceQueueChanges", value -> MusicPlayerConfigManager.get().announceQueueChanges = value))
                        .then(boolSetting("showLoadingHints", value -> MusicPlayerConfigManager.get().showLoadingHints = value))
                        .then(boolSetting("preferIpv4", value -> MusicPlayerConfigManager.get().preferIpv4 = value))
                        .then(Commands.literal("proxy").then(Commands.argument("value", StringArgumentType.greedyString()).executes(context -> {
                            String value = StringArgumentType.getString(context, "value").trim();
                            MusicPlayerConfigManager.get().proxy = "none".equalsIgnoreCase(value) ? "" : value;
                            MusicPlayerConfigManager.save();
                            Messages.success(context.getSource(), "已更新 proxy = " + (MusicPlayerConfigManager.get().proxy.isBlank() ? "<none>" : MusicPlayerConfigManager.get().proxy), false);
                            return 1;
                        })))
                        .then(intSetting("connectTimeoutSeconds", 3, 60, value -> MusicPlayerConfigManager.get().connectTimeoutSeconds = value))
                        .then(intSetting("readTimeoutSeconds", 3, 120, value -> MusicPlayerConfigManager.get().readTimeoutSeconds = value))
                        .then(intSetting("searchLimit", 3, 20, value -> MusicPlayerConfigManager.get().searchLimit = value))
                        .then(intSetting("maxQueueSize", 1, 200, value -> MusicPlayerConfigManager.get().maxQueueSize = value))
                        .then(intSetting("playlistQueueLimit", 1, 100, value -> MusicPlayerConfigManager.get().playlistQueueLimit = value))
                        .then(doubleSetting("voteSkipPercent", 0.1D, 1.0D, value -> MusicPlayerConfigManager.get().voteSkipPercent = value)));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> pagedSearch(String literal, String loadingText, PagedSearchExecutor executor) {
        return Commands.literal(literal)
                .then(Commands.argument("keyword", StringArgumentType.greedyString()).executes(context -> {
                    loading(context.getSource(), loadingText);
                    executor.execute(context.getSource(), StringArgumentType.getString(context, "keyword"), 1);
                    return 1;
                }))
                .then(Commands.literal("page")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .then(Commands.argument("keyword", StringArgumentType.greedyString()).executes(context -> {
                                    int page = IntegerArgumentType.getInteger(context, "page");
                                    loading(context.getSource(), loadingText);
                                    executor.execute(context.getSource(), StringArgumentType.getString(context, "keyword"), page);
                                    return 1;
                                }))));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> boolSetting(String name, java.util.function.Consumer<Boolean> setter) {
        return Commands.literal(name).then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
            boolean value = BoolArgumentType.getBool(context, "value");
            setter.accept(value);
            MusicPlayerConfigManager.save();
            Messages.success(context.getSource(), "已更新 " + name + " = " + value, false);
            return 1;
        }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> intSetting(String name, int min, int max, java.util.function.IntConsumer setter) {
        return Commands.literal(name).then(Commands.argument("value", IntegerArgumentType.integer(min, max)).executes(context -> {
            int value = IntegerArgumentType.getInteger(context, "value");
            setter.accept(value);
            MusicPlayerConfigManager.save();
            Messages.success(context.getSource(), "已更新 " + name + " = " + value, false);
            return 1;
        }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> doubleSetting(String name, double min, double max, java.util.function.DoubleConsumer setter) {
        return Commands.literal(name).then(Commands.argument("value", DoubleArgumentType.doubleArg(min, max)).executes(context -> {
            double value = DoubleArgumentType.getDouble(context, "value");
            setter.accept(value);
            MusicPlayerConfigManager.save();
            Messages.success(context.getSource(), "已更新 " + name + " = " + value, false);
            return 1;
        }));
    }

    private static void sendSongResults(CommandSourceStack source, String keyword, int page, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "搜索歌曲失败：" + rootMessage(throwable));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, "没有搜索到匹配的歌曲。");
            return;
        }
        source.sendSuccess(() -> Component.literal("歌曲搜索结果").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> Messages.clickableCommand("[点歌]", "点击点歌", "/music play song " + entry.id(), ChatFormatting.GREEN)
                    .append(Component.literal(" " + entry.title()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" - " + entry.subtitle()).withStyle(ChatFormatting.GRAY)), false);
        }
        sendSearchNavigation(source, "song", keyword, page, results.size());
    }

    private static void sendArtistResults(CommandSourceStack source, String keyword, int page, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "搜索作者失败：" + rootMessage(throwable));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, "没有搜索到匹配的作者。");
            return;
        }
        source.sendSuccess(() -> Component.literal("作者搜索结果").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> Messages.clickableCommand("[查看]", "查看作者热门歌曲", "/music view artist " + entry.id(), ChatFormatting.GREEN)
                    .append(Component.literal(" " + entry.title()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" - " + entry.subtitle()).withStyle(ChatFormatting.GRAY)), false);
        }
        sendSearchNavigation(source, "artist", keyword, page, results.size());
    }

    private static void sendPlaylistResults(CommandSourceStack source, String keyword, int page, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "搜索歌单失败：" + rootMessage(throwable));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, "没有搜索到匹配的歌单。");
            return;
        }
        source.sendSuccess(() -> Component.literal("歌单搜索结果").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> Messages.clickableCommand("[查看]", "查看歌单详情", "/music view playlist " + entry.id(), ChatFormatting.GREEN)
                    .append(Component.literal(" " + entry.title()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" - " + entry.subtitle()).withStyle(ChatFormatting.GRAY)), false);
        }
        sendSearchNavigation(source, "playlist", keyword, page, results.size());
    }

    private static void sendUserResults(CommandSourceStack source, String keyword, int page, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "搜索用户失败：" + rootMessage(throwable));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, "没有搜索到匹配的用户。");
            return;
        }
        source.sendSuccess(() -> Component.literal("用户搜索结果").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> Messages.clickableCommand("[查看歌单]", "查看用户歌单", "/music view user " + entry.id(), ChatFormatting.GREEN)
                    .append(Component.literal(" " + entry.title()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" - " + entry.subtitle()).withStyle(ChatFormatting.GRAY)), false);
        }
        sendSearchNavigation(source, "user", keyword, page, results.size());
    }

    private static void sendSearchNavigation(CommandSourceStack source, String type, String keyword, int page, int resultSize) {
        int pageSize = MusicPlayerConfigManager.get().searchLimit;
        Component navigation = Component.empty();

        if (page > 1) {
            navigation = navigation.copy().append(Messages.clickableCommand("[上一页]", "查看上一页", "/music search " + type + " page " + (page - 1) + " " + keyword, ChatFormatting.YELLOW));
        } else {
            navigation = navigation.copy().append(Component.literal("[上一页]").withStyle(ChatFormatting.DARK_GRAY));
        }

        navigation = navigation.copy().append(Component.literal(" 第 " + page + " 页 ").withStyle(ChatFormatting.GRAY));

        if (resultSize >= pageSize) {
            navigation = navigation.copy().append(Messages.clickableCommand("[下一页]", "查看下一页", "/music search " + type + " page " + (page + 1) + " " + keyword, ChatFormatting.YELLOW));
        } else {
            navigation = navigation.copy().append(Component.literal("[下一页]").withStyle(ChatFormatting.DARK_GRAY));
        }

        Component finalNavigation = navigation;
        source.sendSuccess(() -> finalNavigation, false);
    }

    private static void showPlaylist(CommandSourceStack source, PlaylistInfo playlist, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "加载歌单失败：" + rootMessage(throwable));
            return;
        }
        source.sendSuccess(() -> Component.literal("歌单：").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(playlist.title()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" "))
                .append(Messages.clickableCommand("[加入队列]", "将歌单加入播放队列", "/music play playlist " + playlist.id(), ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("创建者：").withStyle(ChatFormatting.GRAY)
                .append(Messages.clickableCommand(playlist.ownerName(), "查看用户歌单", "/music view user " + playlist.ownerId(), ChatFormatting.YELLOW)), false);
        for (SearchEntry track : playlist.tracks()) {
            source.sendSuccess(() -> Messages.clickableCommand("[点歌]", "点播这首歌", "/music play song " + track.id(), ChatFormatting.GREEN)
                    .append(Component.literal(" " + track.title()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" - " + track.subtitle()).withStyle(ChatFormatting.GRAY)), false);
        }
    }

    private static void showUserPlaylists(CommandSourceStack source, UserPlaylistView user, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "加载用户歌单失败：" + rootMessage(throwable));
            return;
        }
        source.sendSuccess(() -> Component.literal("用户：").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(user.name()).withStyle(ChatFormatting.AQUA)), false);
        if (!user.signature().isBlank()) {
            source.sendSuccess(() -> Component.literal(user.signature()).withStyle(ChatFormatting.GRAY), false);
        }
        for (SearchEntry playlist : user.playlists()) {
            source.sendSuccess(() -> Messages.clickableCommand("[查看]", "查看歌单详情", "/music view playlist " + playlist.id(), ChatFormatting.GREEN)
                    .append(Component.literal(" " + playlist.title()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" - " + playlist.subtitle()).withStyle(ChatFormatting.GRAY)), false);
        }
    }

    private static void showArtist(CommandSourceStack source, ArtistInfo artist, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "加载作者详情失败：" + rootMessage(throwable));
            return;
        }
        source.sendSuccess(() -> Component.literal("作者：").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(artist.name()).withStyle(ChatFormatting.AQUA)), false);
        if (!artist.description().isBlank()) {
            source.sendSuccess(() -> Component.literal(artist.description()).withStyle(ChatFormatting.GRAY), false);
        }
        for (SearchEntry track : artist.topSongs()) {
            source.sendSuccess(() -> Messages.clickableCommand("[点歌]", "点播这首歌", "/music play song " + track.id(), ChatFormatting.GREEN)
                    .append(Component.literal(" " + track.title()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" - " + track.subtitle()).withStyle(ChatFormatting.GRAY)), false);
        }
    }

    private static void loading(CommandSourceStack source, String text) {
        if (MusicPlayerConfigManager.get().showLoadingHints) {
            Messages.loading(source, text);
        }
    }

    private static String yesNo(boolean value) {
        return value ? "开启" : "关闭";
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    @FunctionalInterface
    private interface PagedSearchExecutor {
        void execute(CommandSourceStack source, String keyword, int page);
    }
}
