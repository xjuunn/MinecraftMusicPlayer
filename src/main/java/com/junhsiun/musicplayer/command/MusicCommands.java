package com.junhsiun.musicplayer.command;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.model.ArtistInfo;
import com.junhsiun.musicplayer.model.PlaylistInfo;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

public final class MusicCommands {
    private MusicCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("music")
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
                .then(admin()));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> help() {
        return Commands.literal("help").executes(context -> sendHelp(context.getSource()));
    }

    private static int sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Minecraft Music Player").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("/music now").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music queue [page]").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music play song <歌曲ID>").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music play playlist <歌单ID>  切换到歌单播放模式").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music search song page <页码> <关键词>").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music view playlist page <页码> <歌单ID>").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music view artist page <页码> <作者ID>").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/music view user page <页码> <用户ID>").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> now() {
        return Commands.literal("now").executes(context -> {
            TrackInfo track = MusicPlayerMod.queueService().currentTrack();
            if (track == null) {
                context.getSource().sendSuccess(MusicPlayerMod.queueService()::describeNowPlaying, false);
                return 1;
            }
            sendQuickBar(context.getSource(),
                    Messages.clickableCommand("[播放队列]", "查看当前待播队列", "/music queue", ChatFormatting.YELLOW),
                    Messages.clickableCommand("[帮助]", "查看音乐模组帮助", "/music help", ChatFormatting.GRAY));
            context.getSource().sendSuccess(() -> renderCurrentTrack(track), false);
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> queue() {
        return Commands.literal("queue")
                .executes(context -> showQueue(context.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> showQueue(context.getSource(), IntegerArgumentType.getInteger(context, "page"))));
    }

    private static int showQueue(CommandSourceStack source, int requestedPage) {
        int totalEntries = MusicPlayerMod.queueService().queuedCount();
        PageWindow page = pageWindow(totalEntries, requestedPage, pageSize());
        TrackInfo currentTrack = MusicPlayerMod.queueService().currentTrack();
        sendQuickBar(source,
                Messages.clickableCommand("[当前播放]", "查看当前播放", "/music now", ChatFormatting.AQUA),
                Messages.clickableCommand("[刷新队列]", "重新查看当前页队列", "/music queue " + page.page(), ChatFormatting.YELLOW),
                Messages.clickableCommand("[帮助]", "查看音乐模组帮助", "/music help", ChatFormatting.GRAY));
        if (currentTrack == null) {
            source.sendSuccess(() -> Component.literal("当前没有歌曲在播放。").withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> renderCurrentTrack(currentTrack), false);
        }
        if (totalEntries == 0) {
            source.sendSuccess(() -> Component.literal("队列为空。").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("待播队列").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);
        List<SearchEntry> entries = MusicPlayerMod.queueService().queuedEntries(page.page(), page.pageSize());
        for (int index = 0; index < entries.size(); index++) {
            SearchEntry entry = entries.get(index);
            int order = (page.page() - 1) * page.pageSize() + index + 1;
            MutableComponent line = Component.literal(order + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(renderEntry(entry, Messages.clickableCommand("[点歌]", "点击重新点播这首歌曲", "/music play song " + entry.id(), ChatFormatting.GREEN), "点击重新点播这首歌曲", "点击查看作者详情"));
            source.sendSuccess(() -> line, false);
        }
        sendNavigation(source, page.page(), page.totalPages(), "/music queue %d", true);
        return 1;
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
        return Commands.literal("mute")
                .then(Commands.literal("once").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MusicPlayerMod.queueService().leavePlayer(player);
                    Messages.success(context.getSource(), "你已停止接收当前歌曲，可用 /music join 重新加入。", false);
                    return 1;
                }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> voteNext() {
        return Commands.literal("vote")
                .then(Commands.literal("next").executes(context -> {
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
                            loading(context.getSource(), MusicPlayerMod.queueService().isPlaying() ? "正在解析音乐并加入队列..." : "正在解析音乐并准备播放...");
                            MusicPlayerMod.queueService().requestSong(context.getSource().getServer(), context.getSource(), player, songId);
                            return 1;
                        })))
                .then(Commands.literal("playlist")
                        .then(Commands.argument("playlist_id", StringArgumentType.string()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String playlistId = StringArgumentType.getString(context, "playlist_id");
                            loading(context.getSource(), "正在加载歌单并切换到歌单播放模式，请稍候...");
                            MusicPlayerMod.queueService().requestPlaylist(context.getSource().getServer(), context.getSource(), player, playlistId);
                            return 1;
                        })));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> search() {
        return Commands.literal("search")
                .then(pagedSearch("song", "正在搜索歌曲，请稍候...", (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchSongs(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendSongResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("artist", "正在搜索作者，请稍候...", (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchArtists(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendArtistResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("author", "正在搜索作者，请稍候...", (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchArtists(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendArtistResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("playlist", "正在搜索歌单，请稍候...", (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchPlaylists(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendPlaylistResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("user", "正在搜索用户，请稍候...", (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchUsers(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendUserResults(source, keyword, page, literal, results, throwable)));
                }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> view() {
        return Commands.literal("view")
                .then(pagedView("playlist", "playlist_id", "正在加载歌单详情...", (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().playlistDetail(id).whenComplete((playlist, throwable) -> server.execute(() -> showPlaylist(source, id, page, playlist, throwable)));
                }))
                .then(pagedView("user", "user_id", "正在加载用户歌单...", (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().userPlaylists(id).whenComplete((user, throwable) -> server.execute(() -> showUserPlaylists(source, id, page, user, throwable)));
                }))
                .then(pagedView("artist", "artist_id", "正在加载作者详情...", (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().artistDetail(id).whenComplete((artist, throwable) -> server.execute(() -> showArtist(source, id, page, artist, throwable, "artist")));
                }))
                .then(pagedView("author", "artist_id", "正在加载作者详情...", (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().artistDetail(id).whenComplete((artist, throwable) -> server.execute(() -> showArtist(source, id, page, artist, throwable, "author")));
                }));
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
                    Messages.info(context.getSource(), "当前音乐服务地址: " + config.neteaseBaseUrl, false);
                    Messages.info(context.getSource(), "歌曲点播: " + yesNo(config.allowSongRequest) + "，歌单点播: " + yesNo(config.allowPlaylistRequest), false);
                    Messages.info(context.getSource(), "自动切歌: " + yesNo(config.autoAdvance) + "，加载提示: " + yesNo(config.showLoadingHints), false);
                    Messages.info(context.getSource(), "代理模式: " + (!config.proxy.isBlank() ? ("手动代理 " + config.proxy) : (config.useSystemProxy ? "自动系统代理" : "直连")), false);
                    Messages.info(context.getSource(), "IPv4 优先: " + yesNo(config.preferIpv4) + "，连接超时: " + config.connectTimeoutSeconds + "s，读取超时: " + config.readTimeoutSeconds + "s", false);
                    Messages.info(context.getSource(), "搜索上限: " + config.searchLimit + "，队列上限: " + config.maxQueueSize + "，歌单导入上限: " + config.playlistQueueLimit + "，预缓存数量: " + config.queueCacheSize, false);
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
                            if (!config.allowCustomServer && !"default".equalsIgnoreCase(value) && !MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL.equalsIgnoreCase(value)) {
                                Messages.warning(context.getSource(), "管理员已禁用自定义音乐服务地址。");
                                return 0;
                            }
                            config.neteaseBaseUrl = "default".equalsIgnoreCase(value) ? MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL : value;
                            MusicPlayerConfigManager.save();
                            Messages.success(context.getSource(), "音乐服务地址已更新为: " + config.neteaseBaseUrl, false);
                            return 1;
                        })))
                        .then(boolSetting("allowCustomServer", value -> MusicPlayerConfigManager.get().allowCustomServer = value))
                        .then(boolSetting("allowSongRequest", value -> MusicPlayerConfigManager.get().allowSongRequest = value))
                        .then(boolSetting("allowPlaylistRequest", value -> MusicPlayerConfigManager.get().allowPlaylistRequest = value))
                        .then(boolSetting("autoAdvance", value -> MusicPlayerConfigManager.get().autoAdvance = value))
                        .then(boolSetting("announceQueueChanges", value -> MusicPlayerConfigManager.get().announceQueueChanges = value))
                        .then(boolSetting("showLoadingHints", value -> MusicPlayerConfigManager.get().showLoadingHints = value))
                        .then(boolSetting("useSystemProxy", value -> MusicPlayerConfigManager.get().useSystemProxy = value))
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
                        .then(intSetting("queueCacheSize", 0, 20, value -> {
                            MusicPlayerConfigManager.get().queueCacheSize = value;
                            MusicPlayerMod.queueService().refreshCacheSettings();
                        }))
                        .then(doubleSetting("voteSkipPercent", 0.1D, 1.0D, value -> MusicPlayerConfigManager.get().voteSkipPercent = value)));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> pagedSearch(String literal, String loadingText, PagedSearchExecutor executor) {
        return Commands.literal(literal)
                .then(Commands.argument("keyword", StringArgumentType.greedyString()).executes(context -> {
                    loading(context.getSource(), loadingText);
                    executor.execute(context.getSource(), StringArgumentType.getString(context, "keyword"), 1, literal);
                    return 1;
                }))
                .then(Commands.literal("page")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .then(Commands.argument("keyword", StringArgumentType.greedyString()).executes(context -> {
                                    int page = IntegerArgumentType.getInteger(context, "page");
                                    loading(context.getSource(), loadingText);
                                    executor.execute(context.getSource(), StringArgumentType.getString(context, "keyword"), page, literal);
                                    return 1;
                                }))));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> pagedView(String literal, String idArgument, String loadingText, PagedViewExecutor executor) {
        return Commands.literal(literal)
                .then(Commands.argument(idArgument, StringArgumentType.string()).executes(context -> {
                    loading(context.getSource(), loadingText);
                    executor.execute(context.getSource(), StringArgumentType.getString(context, idArgument), 1);
                    return 1;
                }))
                .then(Commands.literal("page")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .then(Commands.argument(idArgument, StringArgumentType.string()).executes(context -> {
                                    loading(context.getSource(), loadingText);
                                    executor.execute(context.getSource(), StringArgumentType.getString(context, idArgument), IntegerArgumentType.getInteger(context, "page"));
                                    return 1;
                                }))));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> boolSetting(String name, Consumer<Boolean> setter) {
        return Commands.literal(name).then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
            boolean value = BoolArgumentType.getBool(context, "value");
            setter.accept(value);
            MusicPlayerConfigManager.save();
            Messages.success(context.getSource(), "已更新 " + name + " = " + value, false);
            return 1;
        }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> intSetting(String name, int min, int max, IntConsumer setter) {
        return Commands.literal(name).then(Commands.argument("value", IntegerArgumentType.integer(min, max)).executes(context -> {
            int value = IntegerArgumentType.getInteger(context, "value");
            setter.accept(value);
            MusicPlayerConfigManager.save();
            Messages.success(context.getSource(), "已更新 " + name + " = " + value, false);
            return 1;
        }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> doubleSetting(String name, double min, double max, DoubleConsumer setter) {
        return Commands.literal(name).then(Commands.argument("value", DoubleArgumentType.doubleArg(min, max)).executes(context -> {
            double value = DoubleArgumentType.getDouble(context, "value");
            setter.accept(value);
            MusicPlayerConfigManager.save();
            Messages.success(context.getSource(), "已更新 " + name + " = " + value, false);
            return 1;
        }));
    }

    private static void sendSongResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "搜索歌曲失败: " + rootMessage(throwable));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, "没有搜索到匹配的歌曲。");
            return;
        }
        source.sendSuccess(() -> Component.literal("歌曲搜索结果").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        sendQuickBar(source,
                Messages.clickableCommand("[重新搜索]", "重新查看当前搜索页", String.format("/music search %s page %d %s", literal, page, keyword), ChatFormatting.YELLOW),
                Messages.clickableCommand("[当前播放]", "查看当前播放", "/music now", ChatFormatting.AQUA),
                Messages.clickableCommand("[播放队列]", "查看当前待播队列", "/music queue", ChatFormatting.GRAY));
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> renderEntry(entry, Messages.clickableCommand("[点歌]", "点击点歌", "/music play song " + entry.id(), ChatFormatting.GREEN), "点击点歌", "点击查看作者详情"), false);
        }
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void sendArtistResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "搜索作者失败: " + rootMessage(throwable));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, "没有搜索到匹配的作者。");
            return;
        }
        source.sendSuccess(() -> Component.literal("作者搜索结果").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        sendQuickBar(source,
                Messages.clickableCommand("[重新搜索]", "重新查看当前搜索页", String.format("/music search %s page %d %s", literal, page, keyword), ChatFormatting.YELLOW),
                Messages.clickableCommand("[当前播放]", "查看当前播放", "/music now", ChatFormatting.AQUA),
                Messages.clickableCommand("[播放队列]", "查看当前待播队列", "/music queue", ChatFormatting.GRAY));
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> renderEntry(entry, Messages.clickableCommand("[查看]", "查看作者详情", "/music view artist " + entry.id(), ChatFormatting.GREEN), "查看作者详情", ""), false);
        }
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void sendPlaylistResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "搜索歌单失败: " + rootMessage(throwable));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, "没有搜索到匹配的歌单。");
            return;
        }
        source.sendSuccess(() -> Component.literal("歌单搜索结果").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        sendQuickBar(source,
                Messages.clickableCommand("[重新搜索]", "重新查看当前搜索页", String.format("/music search %s page %d %s", literal, page, keyword), ChatFormatting.YELLOW),
                Messages.clickableCommand("[当前播放]", "查看当前播放", "/music now", ChatFormatting.AQUA),
                Messages.clickableCommand("[播放队列]", "查看当前待播队列", "/music queue", ChatFormatting.GRAY));
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> renderEntry(entry, Messages.clickableCommand("[查看]", "查看歌单详情", "/music view playlist " + entry.id(), ChatFormatting.GREEN), "查看歌单详情", "点击查看创建者详情"), false);
        }
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void sendUserResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "搜索用户失败: " + rootMessage(throwable));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, "没有搜索到匹配的用户。");
            return;
        }
        source.sendSuccess(() -> Component.literal("用户搜索结果").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        sendQuickBar(source,
                Messages.clickableCommand("[重新搜索]", "重新查看当前搜索页", String.format("/music search %s page %d %s", literal, page, keyword), ChatFormatting.YELLOW),
                Messages.clickableCommand("[当前播放]", "查看当前播放", "/music now", ChatFormatting.AQUA),
                Messages.clickableCommand("[播放队列]", "查看当前待播队列", "/music queue", ChatFormatting.GRAY));
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> renderEntry(entry, Messages.clickableCommand("[查看]", "查看用户歌单", "/music view user " + entry.id(), ChatFormatting.GREEN), "查看用户歌单", ""), false);
        }
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void showPlaylist(CommandSourceStack source, String playlistId, int requestedPage, PlaylistInfo playlist, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "加载歌单失败: " + rootMessage(throwable));
            return;
        }
        if (playlist == null) {
            Messages.warning(source, "未找到歌单详情。");
            return;
        }
        source.sendSuccess(() -> Component.literal("歌单: ").withStyle(ChatFormatting.GOLD)
                .append(clickableText(playlist.title(), "/music view playlist " + playlist.id(), "查看歌单详情", ChatFormatting.AQUA))
                .append(Component.literal(" · 创建者: ").withStyle(ChatFormatting.GRAY))
                .append(clickableText(playlist.ownerName(), "/music view user " + playlist.ownerId(), "查看创建者信息", ChatFormatting.YELLOW))
                .append(Component.literal(" "))
                .append(Messages.clickableCommand("[播放歌单]", "切换到歌单播放模式，并从第一首开始顺序播放", "/music play playlist " + playlist.id(), ChatFormatting.GREEN)), false);
        sendQuickBar(source,
                Messages.clickableCommand("[创建者详情]", "查看创建者信息", "/music view user " + playlist.ownerId(), ChatFormatting.YELLOW),
                Messages.clickableCommand("[当前播放]", "查看当前播放", "/music now", ChatFormatting.AQUA),
                Messages.clickableCommand("[播放队列]", "查看当前待播队列", "/music queue", ChatFormatting.GRAY));
        PageWindow page = pageWindow(playlist.tracks().size(), requestedPage, pageSize());
        for (SearchEntry track : slicePage(playlist.tracks(), page)) {
            source.sendSuccess(() -> renderEntry(track, Messages.clickableCommand("[点歌]", "点播这首歌曲", "/music play song " + track.id(), ChatFormatting.GREEN), "点播这首歌曲", "点击查看作者详情"), false);
        }
        sendNavigation(source, page.page(), page.totalPages(), "/music view playlist page %d " + playlistId, true);
    }

    private static void showUserPlaylists(CommandSourceStack source, String userId, int requestedPage, UserPlaylistView user, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "加载用户歌单失败: " + rootMessage(throwable));
            return;
        }
        if (user == null) {
            Messages.warning(source, "未找到用户歌单。");
            return;
        }
        source.sendSuccess(() -> Component.literal("用户: ").withStyle(ChatFormatting.GOLD)
                .append(clickableText(user.name(), "/music view user " + user.id(), "查看用户歌单", ChatFormatting.AQUA)), false);
        sendQuickBar(source,
                Messages.clickableCommand("[刷新用户歌单]", "重新查看该用户的歌单列表", "/music view user " + user.id(), ChatFormatting.YELLOW),
                Messages.clickableCommand("[当前播放]", "查看当前播放", "/music now", ChatFormatting.AQUA),
                Messages.clickableCommand("[播放队列]", "查看当前待播队列", "/music queue", ChatFormatting.GRAY));
        if (user.signature() != null && !user.signature().isBlank()) {
            source.sendSuccess(() -> Component.literal(user.signature()).withStyle(ChatFormatting.GRAY), false);
        }
        if (user.playlists().isEmpty()) {
            Messages.warning(source, "该用户没有可显示的歌单。");
            return;
        }
        PageWindow page = pageWindow(user.playlists().size(), requestedPage, pageSize());
        for (SearchEntry playlist : slicePage(user.playlists(), page)) {
            source.sendSuccess(() -> renderEntry(playlist, Messages.clickableCommand("[查看]", "查看歌单详情", "/music view playlist " + playlist.id(), ChatFormatting.GREEN), "查看歌单详情", ""), false);
        }
        sendNavigation(source, page.page(), page.totalPages(), "/music view user page %d " + userId, true);
    }

    private static void showArtist(CommandSourceStack source, String artistId, int requestedPage, ArtistInfo artist, Throwable throwable, String literal) {
        if (throwable != null) {
            Messages.warning(source, "加载作者详情失败: " + rootMessage(throwable));
            return;
        }
        if (artist == null) {
            Messages.warning(source, "未找到作者详情。");
            return;
        }
        source.sendSuccess(() -> Component.literal("作者: ").withStyle(ChatFormatting.GOLD)
                .append(clickableText(artist.name(), "/music view " + literal + " " + artist.id(), "查看作者详情", ChatFormatting.AQUA)), false);
        sendQuickBar(source,
                Messages.clickableCommand("[刷新作者详情]", "重新查看该作者详情", "/music view " + literal + " " + artist.id(), ChatFormatting.YELLOW),
                Messages.clickableCommand("[当前播放]", "查看当前播放", "/music now", ChatFormatting.AQUA),
                Messages.clickableCommand("[播放队列]", "查看当前待播队列", "/music queue", ChatFormatting.GRAY));
        if (artist.description() != null && !artist.description().isBlank()) {
            source.sendSuccess(() -> Component.literal(artist.description()).withStyle(ChatFormatting.GRAY), false);
        }
        if (artist.topSongs().isEmpty()) {
            Messages.warning(source, "该作者没有可显示的热门歌曲。");
            return;
        }
        PageWindow page = pageWindow(artist.topSongs().size(), requestedPage, pageSize());
        for (SearchEntry track : slicePage(artist.topSongs(), page)) {
            source.sendSuccess(() -> renderEntry(track, Messages.clickableCommand("[点歌]", "点播这首歌曲", "/music play song " + track.id(), ChatFormatting.GREEN), "点播这首歌曲", ""), false);
        }
        sendNavigation(source, page.page(), page.totalPages(), "/music view " + literal + " page %d " + artistId, true);
    }

    private static MutableComponent renderCurrentTrack(TrackInfo track) {
        MutableComponent line = Component.literal("当前播放: ").withStyle(ChatFormatting.GOLD)
                .append(clickableText(track.title(), "/music play song " + track.id(), "点击重新点播这首歌曲", ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(clickableText(track.artist(), track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(), "点击查看作者详情", ChatFormatting.GRAY));
        if (!track.sourceUrls().isEmpty()) {
            line.append(Component.literal(" "));
            line.append(Messages.clickableUrl("[打开直链]", "点击在浏览器中打开当前歌曲直链", track.sourceUrls().getFirst(), ChatFormatting.GREEN));
        }
        return line;
    }

    private static MutableComponent renderEntry(SearchEntry entry, MutableComponent action, String titleHover, String subtitleHover) {
        MutableComponent line = action.copy();
        line.append(Component.literal(" "));
        line.append(clickableText(entry.title(), entry.titleCommand(), titleHover, ChatFormatting.AQUA));
        if (entry.subtitle() != null && !entry.subtitle().isBlank()) {
            line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(clickableText(entry.subtitle(), entry.subtitleCommand(), subtitleHover, ChatFormatting.GRAY));
        }
        return line;
    }

    private static MutableComponent clickableText(String text, String command, String hover, ChatFormatting color) {
        return command != null && !command.isBlank()
                ? Messages.clickableCommand(text, hover == null || hover.isBlank() ? text : hover, command, color)
                : Component.literal(text).withStyle(color);
    }

    private static void sendSearchNavigation(CommandSourceStack source, String type, String keyword, int page, int resultSize) {
        boolean hasNext = resultSize >= pageSize();
        sendNavigation(source, page, page + (hasNext ? 1 : 0), "/music search " + type + " page %d " + keyword, hasNext);
    }

    private static void sendNavigation(CommandSourceStack source, int page, int totalPages, String commandPattern, boolean hasKnownNext) {
        MutableComponent nav = Component.literal("");
        if (page > 1) {
            nav.append(Messages.clickableCommand("[上一页]", "查看上一页", String.format(commandPattern, page - 1), ChatFormatting.YELLOW));
            nav.append(Component.literal(" "));
        }
        nav.append(Component.literal(hasKnownNext ? ("第 " + page + "/" + Math.max(page, totalPages) + " 页") : ("第 " + page + " 页")).withStyle(ChatFormatting.GRAY));
        if (hasKnownNext && page < totalPages) {
            nav.append(Component.literal(" "));
            nav.append(Messages.clickableCommand("[下一页]", "查看下一页", String.format(commandPattern, page + 1), ChatFormatting.YELLOW));
        }
        source.sendSuccess(() -> nav, false);
    }

    @SafeVarargs
    private static void sendQuickBar(CommandSourceStack source, MutableComponent... actions) {
        MutableComponent line = Component.literal("");
        boolean first = true;
        for (MutableComponent action : actions) {
            if (action == null) {
                continue;
            }
            if (!first) {
                line.append(Component.literal(" "));
            }
            line.append(action);
            first = false;
        }
        if (!first) {
            source.sendSuccess(() -> line, false);
        }
    }

    private static <T> List<T> slicePage(List<T> entries, PageWindow page) {
        if (entries.isEmpty()) {
            return List.of();
        }
        int start = (page.page() - 1) * page.pageSize();
        int end = Math.min(entries.size(), start + page.pageSize());
        return entries.subList(start, end);
    }

    private static PageWindow pageWindow(int totalEntries, int requestedPage, int pageSize) {
        int safePageSize = Math.max(1, pageSize);
        int totalPages = Math.max(1, (int) Math.ceil(totalEntries / (double) safePageSize));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        return new PageWindow(page, totalPages, safePageSize);
    }

    private static int pageSize() {
        return Math.max(3, MusicPlayerConfigManager.get().searchLimit);
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
        void execute(CommandSourceStack source, String keyword, int page, String literal);
    }

    @FunctionalInterface
    private interface PagedViewExecutor {
        void execute(CommandSourceStack source, String id, int page);
    }

    private record PageWindow(int page, int totalPages, int pageSize) {
    }
}
