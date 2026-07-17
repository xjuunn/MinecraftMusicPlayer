package com.junhsiun.musicplayer.command;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MusicCommands {
    private MusicCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("music")
                .executes(commandContext -> sendHelpOverview(commandContext.getSource()))
                .then(help())
                .then(now())
                .then(seek())
                .then(pause())
                .then(resume())
                .then(play())
                .then(skip())
                .then(stop())
                .then(queue())
                .then(playlist())
                .then(join())
                .then(leave())
                .then(muteOnce())
                .then(burn())
                .then(random())
                .then(lyrics())
                .then(search())
                .then(view())
                .then(config()));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> help() {
        return Commands.literal("help")
                .executes(context -> sendHelpOverview(context.getSource()))
                .then(Commands.argument("subcommand", StringArgumentType.word()).executes(context -> {
                    String sub = StringArgumentType.getString(context, "subcommand");
                    return sendHelpFor(context.getSource(), sub);
                }));
    }

    private static int sendHelpOverview(CommandSourceStack source) {
        sendHeader(source);
        source.sendSuccess(() -> sectionHeader("音乐播放器", null), false);
        helpEntry(source, "now", "查看当前播放与进度");
        helpEntry(source, "play", "点播单曲或歌单");
        helpEntry(source, "skip", "跳过当前歌曲（投票/直接）");
        helpEntry(source, "stop", "管理员 - 完全停止播放");
        helpEntry(source, "queue", "管理单点队列");
        helpEntry(source, "playlist", "管理歌单播放模式");
        helpEntry(source, "search", "搜索歌曲、作者、歌单或用户");
        helpEntry(source, "view", "查看歌单、作者或用户详情");
        helpEntry(source, "join", "加入当前播放");
        helpEntry(source, "leave", "退出当前播放");
        helpEntry(source, "mute", "暂时静音当前歌曲");
        helpEntry(source, "burn", "将歌曲刻录到唱片");
        helpEntry(source, "random", "随机生成热门音乐");
        helpEntry(source, "lyrics", "切换实时歌词显示");
        helpEntry(source, "help", "显示本帮助页面");
        helpEntry(source, "config", "管理员 - 配置与管理");
        source.sendSuccess(() -> spacer(), false);
        return 1;
    }

    private static void helpEntry(CommandSourceStack source, String command, String description) {
        MutableComponent line = Messages.clickableCommand(command, "查看 " + command + " 的详细用法", "/music help " + command, ChatFormatting.GOLD);
        line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(Component.literal(description).withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> line, false);
    }

    private static int sendHelpFor(CommandSourceStack source, String subcommand) {
        sendHeader(source);
        switch (subcommand) {
            case "now" -> {
                source.sendSuccess(() -> sectionHeader("now", null), false);
                detailLine(source, "/music now", "查看正在播放的歌曲、播放进度和点歌人");
            }
            case "queue" -> {
                source.sendSuccess(() -> sectionHeader("queue", null), false);
                detailLine(source, "/music queue", "查看待播歌曲列表");
                detailLine(source, "/music queue promote <歌曲ID>", "将歌曲提升到下一首播放");
                detailLine(source, "/music queue remove <歌曲ID>", "从队列中移除指定歌曲");
                detailLine(source, "/music queue clear", "管理员 - 清空单点队列");
            }
            case "play" -> {
                source.sendSuccess(() -> sectionHeader("play", null), false);
                detailLine(source, "/music play song <歌曲ID>", "点播一首单曲");
                detailLine(source, "/music play playlist <歌单ID>", "切换到歌单播放模式，从第一首开始顺序播放");
            }
            case "playlist" -> {
                source.sendSuccess(() -> sectionHeader("playlist", null), false);
                detailLine(source, "/music playlist", "查看歌单播放状态");
                detailLine(source, "/music playlist list", "查看当前歌单已加载的曲目");
                detailLine(source, "/music playlist stop", "停止歌单播放模式");
            }
            case "skip" -> {
                source.sendSuccess(() -> sectionHeader("skip", null), false);
                detailLine(source, "/music skip", "跳过当前歌曲");
                detailLine(source, "  · 点歌人直接跳过", "无需投票");
                detailLine(source, "  · 管理员直接跳过", "无需投票");
                detailLine(source, "  · 其他玩家发起投票", "达到阈值后自动切换");
            }
            case "search" -> {
                source.sendSuccess(() -> sectionHeader("search", null), false);
                detailLine(source, "/music search song <关键词>", "搜索歌曲");
                detailLine(source, "/music search artist <关键词>", "搜索作者");
                detailLine(source, "/music search playlist <关键词>", "搜索歌单");
                detailLine(source, "/music search user <关键词>", "搜索用户");
            }
            case "view" -> {
                source.sendSuccess(() -> sectionHeader("view", null), false);
                detailLine(source, "/music view playlist <歌单ID>", "查看歌单详情与曲目列表");
                detailLine(source, "/music view artist <作者ID>", "查看作者详情与热门歌曲");
                detailLine(source, "/music view user <用户ID>", "查看用户创建的歌单");
                detailLine(source, "/music view <音乐/歌单链接>", "通过链接直接查看歌曲或歌单");
            }
            case "join" -> {
                source.sendSuccess(() -> sectionHeader("join", null), false);
                detailLine(source, "/music join", "加入当前播放，开始接收音乐");
            }
            case "leave" -> {
                source.sendSuccess(() -> sectionHeader("leave", null), false);
                detailLine(source, "/music leave", "退出当前播放，不再接收音乐");
            }
            case "mute" -> {
                source.sendSuccess(() -> sectionHeader("mute", null), false);
                detailLine(source, "/music mute once", "暂时静音当前歌曲，可用 /music join 重新加入");
            }
            case "burn" -> {
                source.sendSuccess(() -> sectionHeader("burn", null), false);
                detailLine(source, "/music burn song <歌曲ID>", "将歌曲刻录到主手持有的空白唱片");
            }
            case "random" -> {
                source.sendSuccess(() -> sectionHeader("random", null), false);
                detailLine(source, "/music random", "随机生成 10 首热门音乐，可直接点播");
            }
            case "help" -> {
                return sendHelpOverview(source);
            }
            case "lyrics" -> {
                source.sendSuccess(() -> sectionHeader("lyrics", null), false);
                detailLine(source, "/music lyrics", "切换实时歌词显示");
                detailLine(source, "/music lyrics on", "开启歌词显示");
                detailLine(source, "/music lyrics off", "关闭歌词显示");
                detailLine(source, "/music lyrics status", "查看当前歌词显示状态");
            }
            case "stop" -> {
                source.sendSuccess(() -> sectionHeader("stop", null), false);
                detailLine(source, "/music stop", "停止所有播放并清空当前播放状态");
            }
            case "config" -> {
                source.sendSuccess(() -> sectionHeader("config", null), false);
                detailLine(source, "/music config reload", "重新加载配置并清空音源缓存");
                detailLine(source, "/music config status", "查看当前配置状态");
                detailLine(source, "/music config clearqueue", "清空单点队列");
                detailLine(source, "/music config set <配置项> <值>", "修改配置项");
            }
            default -> {
                source.sendSuccess(() -> Component.literal("未知子命令: " + subcommand).withStyle(ChatFormatting.RED), false);
                source.sendSuccess(() -> spacer(), false);
                return sendHelpOverview(source);
            }
        }
        source.sendSuccess(() -> spacer(), false);
        return 1;
    }

    private static void detailLine(CommandSourceStack source, String command, String description) {
        MutableComponent line = Component.literal(command).withStyle(ChatFormatting.GOLD);
        line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(Component.literal(description).withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> line, false);
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> now() {
        return Commands.literal("now").executes(context -> {
            TrackInfo track = MusicPlayerMod.queueService().currentTrack();
            if (track == null) {
                sendHeader(context.getSource());
                context.getSource().sendSuccess(MusicPlayerMod.queueService()::describeNowPlaying, false);
                return 1;
            }
        sendHeader(context.getSource());
        boolean isAdmin = context.getSource().permissions().hasPermission(Permissions.COMMANDS_ADMIN);
        boolean isPaused = MusicPlayerMod.queueService().isPaused();
        sendQuickBar(context.getSource(),
                Messages.clickableCommand("[快退 5s]", "后退 5 秒", "/music seek -5", ChatFormatting.GRAY),
                isPaused
                        ? (isAdmin ? Messages.clickableCommand("[继续]", "继续播放", "/music resume", ChatFormatting.GREEN) : null)
                        : (isAdmin ? Messages.clickableCommand("[暂停]", "暂停播放", "/music pause", ChatFormatting.YELLOW) : null),
                Messages.clickableCommand("[快进 5s]", "前进 5 秒", "/music seek 5", ChatFormatting.GRAY),
                Messages.clickableCommand("[跳过]", "投票或直接跳过当前歌曲", "/music skip", ChatFormatting.YELLOW),
                Messages.clickableCommand("[队列]", "查看队列", "/music queue", ChatFormatting.GRAY),
                Messages.clickableCommand("[歌单]", "查看歌单状态", "/music playlist", ChatFormatting.AQUA),
                Messages.clickableCommand("[帮助]", "查看音乐模组帮助", "/music help", ChatFormatting.DARK_GRAY));
        context.getSource().sendSuccess(() -> spacer(), false);
        long elapsedMs = MusicPlayerMod.queueService().playbackElapsedMillis();
        long durationMs = MusicPlayerMod.queueService().playbackDurationMillis();
        String requesterName = MusicPlayerMod.queueService().currentRequesterName();
        String elapsed = Messages.formatDuration(elapsedMs);
        String duration = durationMs > 0L ? Messages.formatDuration(durationMs) : "";
        context.getSource().sendSuccess(() -> renderCurrentTrack(context.getSource(), track, elapsed, duration, requesterName), false);
        context.getSource().sendSuccess(() -> renderProgressLine(elapsed, duration, requesterName, isPaused, durationMs > 0L ? MusicPlayerMod.queueService().playbackElapsedMillis() : 0L, durationMs), false);
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> seek() {
        return Commands.literal("seek")
                .then(Commands.argument("delta", IntegerArgumentType.integer()).executes(context -> {
                    int delta = IntegerArgumentType.getInteger(context, "delta");
                    MusicPlayerMod.queueService().seek(context.getSource().getServer(), delta);
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        String icon = delta >= 0 ? "⏩" : "⏪";
                        player.sendSystemMessage(Component.literal(icon + "  " + Math.abs(delta) + " 秒").withStyle(ChatFormatting.GRAY));
                    }
                    return 1;
                }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> pause() {
        return Commands.literal("pause")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(context -> {
                    MusicPlayerMod.queueService().pause(context.getSource().getServer());
                    return 1;
                });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> resume() {
        return Commands.literal("resume")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(context -> {
                    MusicPlayerMod.queueService().resume(context.getSource().getServer());
                    return 1;
                });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> queue() {
        return Commands.literal("queue")
                .executes(context -> showQueue(context.getSource(), 1))
                .then(Commands.literal("promote")
                        .then(Commands.argument("song_id", StringArgumentType.string()).executes(context -> {
                            String songId = StringArgumentType.getString(context, "song_id");
                            if (!MusicPlayerMod.queueService().moveQueuedTrackToFront(songId)) {
                                Messages.warning(context.getSource(), "未找到这首待播歌曲，无法调整到下一首。");
                                return 0;
                            }
                            Messages.success(context.getSource(), "已将这首歌调整为下一首播放。", false);
                            return showQueue(context.getSource(), 1);
                        })))
                .then(Commands.literal("remove")
                        .then(Commands.argument("song_id", StringArgumentType.string()).executes(context -> {
                            String songId = StringArgumentType.getString(context, "song_id");
                            if (!MusicPlayerMod.queueService().removeFromQueue(songId)) {
                                Messages.warning(context.getSource(), "未找到这首待播歌曲。");
                                return 0;
                            }
                            Messages.success(context.getSource(), "已从队列中移除。", false);
                            return showQueue(context.getSource(), 1);
                        })))
                .then(Commands.literal("clear")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(context -> {
                            MusicPlayerMod.queueService().clearQueue(context.getSource());
                            return showQueue(context.getSource(), 1);
                        }))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> showQueue(context.getSource(), IntegerArgumentType.getInteger(context, "page"))));
    }

    private static int showQueue(CommandSourceStack source, int requestedPage) {
        int totalEntries = MusicPlayerMod.queueService().queuedCount();
        PageWindow page = pageWindow(totalEntries, requestedPage, pageSize());
        TrackInfo currentTrack = MusicPlayerMod.queueService().currentTrack();
        sendHeader(source);
        sendQuickBar(source,
                Messages.clickableCommand("[当前播放]", "查看当前播放", "/music now", ChatFormatting.AQUA),
                Messages.clickableCommand("[刷新]", "重新查看当前页队列", "/music queue " + page.page(), ChatFormatting.YELLOW),
                Messages.clickableCommand("[跳过]", "投票或直接跳过当前歌曲", "/music skip", ChatFormatting.GRAY),
                Messages.clickableCommand("[帮助]", "查看音乐模组帮助", "/music help", ChatFormatting.DARK_GRAY));
        if (currentTrack == null) {
            source.sendSuccess(() -> Component.literal("当前没有歌曲在播放。").withStyle(ChatFormatting.GRAY), false);
        } else {
            long elapsedMs = MusicPlayerMod.queueService().playbackElapsedMillis();
            long durationMs = MusicPlayerMod.queueService().playbackDurationMillis();
            String requesterName = MusicPlayerMod.queueService().currentRequesterName();
            String elapsed = Messages.formatDuration(elapsedMs);
            String duration = durationMs > 0L ? Messages.formatDuration(durationMs) : "";
            source.sendSuccess(() -> renderCurrentTrack(source, currentTrack, elapsed, duration, requesterName), false);
            source.sendSuccess(() -> renderProgressLine(elapsed, duration, requesterName, MusicPlayerMod.queueService().isPaused(), elapsedMs, durationMs), false);
        }
        if (totalEntries == 0) {
            source.sendSuccess(() -> Component.literal("队列为空。").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> spacer(), false);
        source.sendSuccess(() -> sectionHeader("单点队列", "点击「置顶」可将歌曲提升到队列顶部"), false);
        List<SearchEntry> entries = MusicPlayerMod.queueService().queuedEntries(page.page(), page.pageSize());
        for (int index = 0; index < entries.size(); index++) {
            SearchEntry entry = entries.get(index);
            int order = (page.page() - 1) * page.pageSize() + index + 1;
            MutableComponent line = Component.literal(order + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Messages.clickableCommand("[下一首]", "将这首歌调整为下一首播放", "/music playlist next " + entry.id(), ChatFormatting.GREEN))
                    .append(Component.literal(" "))
                    .append(clickableText(entry.title(), entry.titleCommand(), "重新播放这首歌曲", ChatFormatting.AQUA));
            if (entry.subtitle() != null && !entry.subtitle().isBlank()) {
                line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
                line.append(clickableText(entry.subtitle(), entry.subtitleCommand(), "查看作者详情", ChatFormatting.GRAY));
            }
            source.sendSuccess(() -> line, false);
        }
        source.sendSuccess(() -> spacer(), false);
        sendNavigation(source, page.page(), page.totalPages(), "/music queue %d", true, "/music queue ");
        source.sendSuccess(() -> spacer(), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> playlist() {
        return Commands.literal("playlist")
                .executes(context -> showPlaylistStatus(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> showPlaylistTracks(context.getSource())))
                .then(Commands.literal("stop")
                        .executes(context -> {
                            MusicPlayerMod.queueService().stopPlaylist(context.getSource().getServer());
                            return 1;
                        }));
    }

    private static int showPlaylistStatus(CommandSourceStack source) {
        sendHeader(source);
        boolean isPlaylistMode = MusicPlayerMod.queueService().isPlaylistMode();
        int queueSize = MusicPlayerMod.queueService().queuedCount();
        int remaining = MusicPlayerMod.queueService().playlistRemainingCount();

        sendQuickBar(source,
                Messages.clickableCommand("[队列]", "查看队列", "/music queue", ChatFormatting.YELLOW),
                Messages.clickableCommand("[加载列表]", "查看当前歌单已加载的曲目", "/music playlist list", ChatFormatting.AQUA),
                isPlaylistMode ? Messages.clickableCommand("[停止歌单]", "停止歌单播放模式", "/music playlist stop", ChatFormatting.DARK_GRAY) : null,
                Messages.clickableCommand("[帮助]", "查看音乐模组帮助", "/music help", ChatFormatting.DARK_GRAY));

        TrackInfo currentTrack = MusicPlayerMod.queueService().currentTrack();
        if (currentTrack != null) {
            source.sendSuccess(() -> spacer(), false);
            long elapsedMs = MusicPlayerMod.queueService().playbackElapsedMillis();
            long durationMs = MusicPlayerMod.queueService().playbackDurationMillis();
            String requesterName = MusicPlayerMod.queueService().currentRequesterName();
            String elapsed = Messages.formatDuration(elapsedMs);
            String duration = durationMs > 0L ? Messages.formatDuration(durationMs) : "";
            source.sendSuccess(() -> renderCurrentTrack(source, currentTrack, elapsed, duration, requesterName), false);
            source.sendSuccess(() -> renderProgressLine(elapsed, duration, requesterName, MusicPlayerMod.queueService().isPaused(), elapsedMs, durationMs), false);
        }

        source.sendSuccess(() -> spacer(), false);
        MutableComponent statusLine = Component.literal("单点队列: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(String.valueOf(queueSize)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" 首").withStyle(ChatFormatting.GRAY));
        if (isPlaylistMode) {
            statusLine.append(Component.literal("  ·  歌单队列: ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(String.valueOf(remaining)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" 首").withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> statusLine, false);
        source.sendSuccess(() -> spacer(), false);
        return 1;
    }

    private static int showPlaylistTracks(CommandSourceStack source) {
        sendHeader(source);
        if (!MusicPlayerMod.queueService().isPlaylistMode()) {
            source.sendSuccess(() -> Component.literal("当前没有正在播放的歌单。").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> spacer(), false);
            return 1;
        }

        List<SearchEntry> entries = MusicPlayerMod.queueService().playlistEntries();
        int totalEntries = entries.size();
        int remaining = MusicPlayerMod.queueService().playlistRemainingCount();

        sendQuickBar(source,
                Messages.clickableCommand("[歌单状态]", "查看歌单播放状态", "/music playlist", ChatFormatting.AQUA),
                Messages.clickableCommand("[停止歌单]", "停止歌单播放模式", "/music playlist stop", ChatFormatting.DARK_GRAY),
                Messages.clickableCommand("[帮助]", "查看音乐模组帮助", "/music help", ChatFormatting.DARK_GRAY));

        source.sendSuccess(() -> spacer(), false);
        source.sendSuccess(() -> Component.literal("歌单队列: " + totalEntries + " 首，剩余 " + remaining + " 首").withStyle(ChatFormatting.DARK_GRAY), false);
        source.sendSuccess(() -> spacer(), false);

        for (int index = 0; index < entries.size(); index++) {
            SearchEntry entry = entries.get(index);
            MutableComponent line = Component.literal((index + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Messages.clickableCommand("[点歌]", "点击重新点播这首歌曲", "/music play song " + entry.id(), ChatFormatting.GREEN))
                    .append(Component.literal(" "))
                    .append(clickableText(entry.title(), entry.titleCommand(), "在浏览器中打开", ChatFormatting.AQUA));
            if (entry.subtitle() != null && !entry.subtitle().isBlank()) {
                line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
                line.append(clickableText(entry.subtitle(), entry.subtitleCommand(), "查看作者详情", ChatFormatting.GRAY));
            }
            source.sendSuccess(() -> line, false);
        }
        source.sendSuccess(() -> spacer(), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> lyrics() {
        return Commands.literal("lyrics")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    boolean now = MusicPlayerMod.queueService().toggleLyrics(player);
                    Messages.success(context.getSource(), now ? "歌词已开启。" : "歌词已关闭。", false);
                    return 1;
                })
                .then(Commands.literal("on").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MusicPlayerMod.queueService().toggleLyrics(player, true);
                    Messages.success(context.getSource(), "歌词已开启。", false);
                    return 1;
                }))
                .then(Commands.literal("off").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MusicPlayerMod.queueService().toggleLyrics(player, false);
                    Messages.success(context.getSource(), "歌词已关闭。", false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    boolean on = MusicPlayerMod.queueService().isLyricsEnabled(player);
                    Messages.info(context.getSource(), "歌词显示: " + (on ? "开启" : "关闭"), false);
                    return 1;
                }));
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

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> skip() {
        return Commands.literal("skip")
                .requires(source -> source.getEntity() instanceof ServerPlayer || source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer();
                    if (context.getSource().permissions().hasPermission(Permissions.COMMANDS_ADMIN)) {
                        MusicPlayerMod.queueService().skipNow(server, context.getSource());
                    } else {
                        MusicPlayerMod.queueService().voteSkip(server, context.getSource().getPlayerOrException());
                    }
                    return 1;
                });
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

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> burn() {
        return Commands.literal("burn")
                .then(Commands.literal("song")
                        .then(Commands.argument("song_id", StringArgumentType.string()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (!MusicDiscHelper.isBurnableDisc(player.getItemInHand(InteractionHand.MAIN_HAND))) {
                                Messages.warning(context.getSource(), "请先在主手持有一张可刻录的唱片。");
                                return 0;
                            }
                            String songId = StringArgumentType.getString(context, "song_id");
                            loading(context.getSource(), "正在刻录音乐唱片，请稍候...");
                            MinecraftServer server = context.getSource().getServer();
                            MusicPlayerMod.netease().resolveSong(songId).whenComplete((track, throwable) -> server.execute(() -> {
                                if (throwable != null) {
                                    Messages.warning(context.getSource(), "刻录失败: " + rootMessage(throwable));
                                    return;
                                }
                                burnHeldDisc(context.getSource(), player, track);
                            }));
                            return 1;
                        })));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> random() {
        return Commands.literal("random")
                .executes(context -> generateRandomList(context.getSource()))
                .then(Commands.literal("refresh").executes(context -> generateRandomList(context.getSource())));
    }

    private static int generateRandomList(CommandSourceStack source) {
        loading(source, "正在从热门歌单中随机挑选音乐，请稍候...");
        MinecraftServer server = source.getServer();
        MusicPlayerMod.netease().randomHotTracks(10).whenComplete((tracks, throwable) ->
                server.execute(() -> showRandomTracks(source, tracks, throwable)));
        return 1;
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
                }))
                .then(viewUrl());
    }

    private static final Pattern URL_ID_PATTERN = Pattern.compile("[?&]id=(\\d+)");

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> viewUrl() {
        return Commands.argument("url", StringArgumentType.greedyString()).executes(context -> {
            String url = StringArgumentType.getString(context, "url");
            Matcher m = URL_ID_PATTERN.matcher(url);
            if (!m.find()) {
                Messages.warning(context.getSource(), "无法从链接中解析出音乐 ID。");
                return 0;
            }
            String id = m.group(1);
            MinecraftServer server = context.getSource().getServer();

            if (url.contains("/song")) {
                loading(context.getSource(), "正在加载歌曲详情...");
                MusicPlayerMod.netease().resolveSong(id).whenComplete((track, throwable) ->
                        server.execute(() -> showSong(context.getSource(), track, throwable)));
            } else if (url.contains("/playlist")) {
                loading(context.getSource(), "正在加载歌单详情...");
                MusicPlayerMod.netease().playlistDetail(id).whenComplete((playlist, throwable) ->
                        server.execute(() -> showPlaylist(context.getSource(), id, 1, playlist, throwable)));
            } else {
                Messages.warning(context.getSource(), "不支持的链接类型，仅支持 music.163.com 的单曲和歌单链接。");
                return 0;
            }
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

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> config() {
        return Commands.literal("config")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .then(Commands.literal("reload").executes(context -> {
                    MusicPlayerConfigManager.load();
                    MusicPlayerMod.queueService().clearTrackCache();
                    Messages.success(context.getSource(), "配置已重新加载，音源缓存已清空。", false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {
                    MusicPlayerConfig config = MusicPlayerConfigManager.get();
                    Messages.info(context.getSource(), "当前音乐服务地址: " + config.neteaseBaseUrl, false);
                    Messages.info(context.getSource(), "歌曲点播: " + yesNo(config.allowSongRequest) + "，歌单点播: " + yesNo(config.allowPlaylistRequest), false);
                    Messages.info(context.getSource(), "自动切歌: " + yesNo(config.autoAdvance) + "，加载提示: " + yesNo(config.showLoadingHints), false);
                    Messages.info(context.getSource(), "实时歌词: " + yesNo(config.showLyrics), false);
                    Messages.info(context.getSource(), "代理模式: " + (!config.proxy.isBlank() ? ("手动代理 " + config.proxy) : (config.useSystemProxy ? "自动系统代理" : "直连")), false);
                    Messages.info(context.getSource(), "IPv4 优先: " + yesNo(config.preferIpv4) + "，连接超时: " + config.connectTimeoutSeconds + "s，读取超时: " + config.readTimeoutSeconds + "s", false);
                    Messages.info(context.getSource(), "搜索上限: " + config.searchLimit + "，队列上限: " + config.maxQueueSize + "，歌单导入上限: " + config.playlistQueueLimit + "，预缓存数量: " + config.queueCacheSize, false);
                    Messages.info(context.getSource(), "投票切歌阈值: " + config.voteSkipPercent, false);
                    Messages.info(context.getSource(), "战利品音乐唱片: " + yesNo(config.enableLootMusicDiscs) + "，生成概率: " + config.lootMusicDiscChance + "，每个容器数量: " + config.lootMusicDiscCount, false);
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
                        .then(boolSetting("showLyrics", value -> MusicPlayerConfigManager.get().showLyrics = value))
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
                        .then(boolSetting("enableLootMusicDiscs", value -> MusicPlayerConfigManager.get().enableLootMusicDiscs = value))
                        .then(intSetting("lootMusicDiscCount", 0, 5, value -> MusicPlayerConfigManager.get().lootMusicDiscCount = value))
                        .then(doubleSetting("lootMusicDiscChance", 0.0D, 1.0D, value -> MusicPlayerConfigManager.get().lootMusicDiscChance = value))
                        .then(doubleSetting("voteSkipPercent", 0.1D, 1.0D, value -> MusicPlayerConfigManager.get().voteSkipPercent = value)));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> pagedSearch(String literal, String loadingText, PagedSearchExecutor executor) {
        return Commands.literal(literal)
                .then(Commands.argument("keyword", StringArgumentType.string()).executes(context -> {
                    loading(context.getSource(), loadingText);
                    executor.execute(context.getSource(), StringArgumentType.getString(context, "keyword"), 1, literal);
                    return 1;
                })
                .then(Commands.literal("page")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
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
                })
                        .then(Commands.literal("page")
                                .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
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
        sendHeader(source);
        source.sendSuccess(() -> sectionHeader("歌曲搜索结果", "歌曲名、作者名与操作按钮均可点击"), false);
        source.sendSuccess(() -> spacer(), false);
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> renderEntry(entry, trackActions(source, entry.id(), "[点歌]", "点击点歌", ChatFormatting.GREEN), "点击点歌", "点击查看作者详情"), false);
        }
        source.sendSuccess(() -> spacer(), false);
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
        sendHeader(source);
        source.sendSuccess(() -> sectionHeader("作者搜索结果", "点击作者名或查看按钮进入详情"), false);
        source.sendSuccess(() -> spacer(), false);
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> renderEntry(entry, Messages.clickableCommand("[查看]", "查看作者详情", "/music view artist " + entry.id(), ChatFormatting.GREEN), "查看作者详情", ""), false);
        }
        source.sendSuccess(() -> spacer(), false);
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
        sendHeader(source);
        source.sendSuccess(() -> sectionHeader("歌单搜索结果", "可查看歌单详情或创建者信息"), false);
        source.sendSuccess(() -> spacer(), false);
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> renderEntry(entry, Messages.clickableCommand("[查看]", "查看歌单详情", "/music view playlist " + entry.id(), ChatFormatting.GREEN), "查看歌单详情", "点击查看创建者详情"), false);
        }
        source.sendSuccess(() -> spacer(), false);
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
        sendHeader(source);
        source.sendSuccess(() -> sectionHeader("用户搜索结果", "点击用户可进入歌单列表"), false);
        source.sendSuccess(() -> spacer(), false);
        for (SearchEntry entry : results) {
            source.sendSuccess(() -> renderEntry(entry, Messages.clickableCommand("[查看]", "查看用户歌单", "/music view user " + entry.id(), ChatFormatting.GREEN), "查看用户歌单", ""), false);
        }
        source.sendSuccess(() -> spacer(), false);
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void showRandomTracks(CommandSourceStack source, List<TrackInfo> tracks, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "生成随机热门音乐列表失败: " + rootMessage(throwable));
            return;
        }
        if (tracks == null || tracks.isEmpty()) {
            Messages.warning(source, "这次没有抽到可播放的热门音乐。");
            return;
        }
        sendHeader(source);
        source.sendSuccess(() -> sectionHeader("随机热门音乐", "每次随机生成 10 首，可直接点歌、刻录、查看作者"), false);
        source.sendSuccess(() -> spacer(), false);
        for (TrackInfo track : tracks) {
            source.sendSuccess(() -> renderRandomTrack(source, track), false);
        }
        source.sendSuccess(() -> spacer(), false);
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

        int pageSize = pageSize();
        int totalPages = Math.max(1, (int) Math.ceil((double) playlist.trackCount() / pageSize));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int offset = (page - 1) * pageSize;

        // Load tracks first, then render everything together
        MinecraftServer server = source.getServer();
        MusicPlayerMod.netease().playlistTracksPage(playlistId, offset, pageSize)
                .whenComplete((tracks, t) -> server.execute(() -> {
                    sendHeader(source);
                    source.sendSuccess(() -> Component.literal("歌单: ").withStyle(ChatFormatting.GOLD)
                            .append(clickableText(playlist.title(), "/music view playlist " + playlist.id(), "查看歌单详情", ChatFormatting.AQUA))
                            .append(Component.literal(" · 创建者: ").withStyle(ChatFormatting.GRAY))
                            .append(clickableText(playlist.ownerName(), "/music view user " + playlist.ownerId(), "查看创建者信息", ChatFormatting.YELLOW))
                            .append(Component.literal(" "))
                            .append(Messages.clickableCommand("[播放歌单]", "播放此歌单", "/music play playlist " + playlist.id(), ChatFormatting.GREEN)), false);
                    source.sendSuccess(() -> spacer(), false);

                    if (t != null) {
                        Messages.warning(source, "加载曲目失败: " + rootMessage(t));
                        return;
                    }
                    for (SearchEntry track : tracks) {
                        source.sendSuccess(() -> renderEntry(track,
                                trackActions(source, track.id(), "[点歌]", "点播这首歌曲", ChatFormatting.GREEN),
                                "点播这首歌曲", "点击查看作者详情"), false);
                    }
                    source.sendSuccess(() -> spacer(), false);
                    sendNavigation(source, page, totalPages,
                            "/music view playlist " + playlistId + " page %d", true,
                            "/music view playlist " + playlistId + " page ");
                    source.sendSuccess(() -> spacer(), false);
                }));
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
        sendHeader(source);
        source.sendSuccess(() -> Component.literal("用户: ").withStyle(ChatFormatting.GOLD)
                .append(clickableText(user.name(), "/music view user " + user.id(), "查看用户歌单", ChatFormatting.AQUA)), false);
        if (user.signature() != null && !user.signature().isBlank()) {
            source.sendSuccess(() -> spacer(), false);
            source.sendSuccess(() -> Component.literal(user.signature()).withStyle(ChatFormatting.GRAY), false);
        }
        if (user.playlists().isEmpty()) {
            Messages.warning(source, "该用户没有可显示的歌单。");
            return;
        }
        source.sendSuccess(() -> spacer(), false);
        PageWindow page = pageWindow(user.playlists().size(), requestedPage, pageSize());
        for (SearchEntry playlist : slicePage(user.playlists(), page)) {
            source.sendSuccess(() -> renderEntry(playlist, Messages.clickableCommand("[查看]", "查看歌单详情", "/music view playlist " + playlist.id(), ChatFormatting.GREEN), "查看歌单详情", ""), false);
        }
        source.sendSuccess(() -> spacer(), false);
        sendNavigation(source, page.page(), page.totalPages(), "/music view user " + userId + " page %d", true, "/music view user " + userId + " page ");
        source.sendSuccess(() -> spacer(), false);
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
        sendHeader(source);
        source.sendSuccess(() -> Component.literal("作者: ").withStyle(ChatFormatting.GOLD)
                .append(clickableText(artist.name(), "/music view " + literal + " " + artist.id(), "查看作者详情", ChatFormatting.AQUA)), false);
        if (artist.description() != null && !artist.description().isBlank()) {
            source.sendSuccess(() -> spacer(), false);
            source.sendSuccess(() -> Component.literal(artist.description()).withStyle(ChatFormatting.GRAY), false);
        }
        if (artist.topSongs().isEmpty()) {
            Messages.warning(source, "该作者没有可显示的热门歌曲。");
            return;
        }
        source.sendSuccess(() -> spacer(), false);
        PageWindow page = pageWindow(artist.topSongs().size(), requestedPage, pageSize());
        for (SearchEntry track : slicePage(artist.topSongs(), page)) {
            source.sendSuccess(() -> renderEntry(track, trackActions(source, track.id(), "[点歌]", "点播这首歌曲", ChatFormatting.GREEN), "点播这首歌曲", ""), false);
        }
        source.sendSuccess(() -> spacer(), false);
        sendNavigation(source, page.page(), page.totalPages(), "/music view " + literal + " " + artistId + " page %d", true, "/music view " + literal + " " + artistId + " page ");
        source.sendSuccess(() -> spacer(), false);
    }

    private static void showSong(CommandSourceStack source, TrackInfo track, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, "加载歌曲详情失败: " + rootMessage(throwable));
            return;
        }
        if (track == null || track.title() == null || track.title().isBlank()) {
            Messages.warning(source, "未找到歌曲详情。");
            return;
        }
        sendHeader(source);
        source.sendSuccess(() -> sectionHeader("歌曲详情", null), false);
        source.sendSuccess(() -> spacer(), false);

        source.sendSuccess(() -> Component.literal("歌曲: ").withStyle(ChatFormatting.GOLD)
                .append(clickableText(track.title(), "/music play song " + track.id(), "点击点播", ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(clickableText(track.artist(),
                        track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(),
                        "点击查看作者详情", ChatFormatting.GRAY)), false);

        if (track.durationMillis() > 0L) {
            source.sendSuccess(() -> Component.literal("时长: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(Messages.formatDuration(track.durationMillis())).withStyle(ChatFormatting.WHITE)), false);
        }

        if (track.coverUrl() != null && !track.coverUrl().isBlank()) {
            source.sendSuccess(() -> Component.literal("封面: ").withStyle(ChatFormatting.GRAY)
                    .append(Messages.clickableUrl("[点击查看]", "在浏览器中打开封面图片", track.coverUrl(), ChatFormatting.BLUE)), false);
        }

        source.sendSuccess(() -> Component.literal("ID: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(track.id()).withStyle(style -> style
                        .withClickEvent(new ClickEvent.CopyToClipboard(track.id()))
                        .withColor(ChatFormatting.WHITE)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("点击复制"))))), false);

        MutableComponent burnAction = buildBurnAction(source, track.id());
        if (burnAction != null) {
            source.sendSuccess(() -> spacer(), false);
            source.sendSuccess(() -> Component.literal("操作: ").withStyle(ChatFormatting.GOLD)
                    .append(burnAction), false);
        }

        source.sendSuccess(() -> spacer(), false);
    }

    private static MutableComponent renderRandomTrack(CommandSourceStack source, TrackInfo track) {
        MutableComponent line = trackActions(source, track.id(), "[点歌]", "点播这首随机热门歌曲", ChatFormatting.GREEN);
        line.append(Component.literal(" "));
        line.append(clickableText(track.title(), "/music play song " + track.id(), "点播这首随机热门歌曲", ChatFormatting.AQUA));
        line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(clickableText(track.artist(),
                track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(),
                "查看作者详情",
                ChatFormatting.GRAY));
        if (track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
            line.append(Component.literal(" "));
            line.append(Messages.clickableUrl("[下载]", "在浏览器中打开当前音乐直链", track.sourceUrls().getFirst(), ChatFormatting.BLUE));
        }
        return line;
    }

    private static MutableComponent renderCurrentTrack(CommandSourceStack source, TrackInfo track, String elapsed, String duration, String requesterName) {
        MutableComponent line = Component.literal("当前播放: ").withStyle(ChatFormatting.GOLD)
                .append(clickableText(track.title(), "/music play song " + track.id(), "点击重新点播这首歌曲", ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(clickableText(track.artist(), track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(), "点击查看作者详情", ChatFormatting.GRAY));
        MutableComponent burnAction = buildBurnAction(source, track.id());
        if (burnAction != null) {
            line.append(Component.literal(" "));
            line.append(burnAction);
        }
        if (!track.sourceUrls().isEmpty()) {
            line.append(Component.literal(" "));
            line.append(Messages.clickableUrl("[打开直链]", "点击在浏览器中打开当前歌曲直链", track.sourceUrls().getFirst(), ChatFormatting.GREEN));
        }
        return line;
    }

    private static Component renderProgressLine(String elapsed, String duration, String requesterName, boolean paused, long elapsedMs, long durationMs) {
        String progress = duration != null && !duration.isEmpty()
                ? elapsed + " / " + duration
                : elapsed;
        MutableComponent line = Component.literal("");

        if (durationMs > 0L) {
            int barLen = 20;
            int filled = (int) (barLen * elapsedMs / Math.max(1L, durationMs));
            filled = Math.max(0, Math.min(barLen, filled));
            line.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal("█".repeat(filled)).withStyle(ChatFormatting.GREEN));
            line.append(Component.literal("░".repeat(barLen - filled)).withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
        }

        line.append(Component.literal(progress).withStyle(ChatFormatting.WHITE));

        if (paused) {
            line.append(Component.literal("  ⏸").withStyle(ChatFormatting.YELLOW));
        }

        if (requesterName != null && !requesterName.isEmpty()) {
            line.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal("点歌: ").withStyle(ChatFormatting.GRAY));
            line.append(Component.literal(requesterName).withStyle(ChatFormatting.AQUA));
        }
        return line;
    }

    private static MutableComponent trackActions(CommandSourceStack source, String songId, String primaryLabel, String primaryHover, ChatFormatting primaryColor) {
        MutableComponent actions = Messages.clickableCommand(primaryLabel, primaryHover, "/music play song " + songId, primaryColor);
        MutableComponent burnAction = buildBurnAction(source, songId);
        if (burnAction != null) {
            actions.append(Component.literal(" "));
            actions.append(burnAction);
        }
        return actions;
    }

    private static MutableComponent buildBurnAction(CommandSourceStack source, String songId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return null;
        }
        if (!MusicDiscHelper.isBurnableDisc(player.getItemInHand(InteractionHand.MAIN_HAND))) {
            return null;
        }
        return Messages.clickableCommand("[刻录]", "将这首歌刻录到主手唱片", "/music burn song " + songId, ChatFormatting.LIGHT_PURPLE);
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
        String base = "/music search " + type + " \"" + keyword + "\"";
        sendNavigation(source, page, page + (hasNext ? 1 : 0), base + " page %d", hasNext, base + " page ");
        source.sendSuccess(() -> spacer(), false);
    }

    private static void sendNavigation(CommandSourceStack source, int page, int totalPages, String commandPattern, boolean hasKnownNext, String suggestCommand) {
        MutableComponent nav = Component.literal("");
        if (page > 1) {
            nav.append(Messages.clickableCommand("[‹ 上一页]", "查看上一页", String.format(commandPattern, page - 1), ChatFormatting.YELLOW));
            nav.append(Component.literal(" "));
        }
        nav.append(Component.literal(hasKnownNext ? ("· 第 " + page + "/" + Math.max(page, totalPages) + " 页 ·") : ("· 第 " + page + " 页 ·")).withStyle(ChatFormatting.DARK_GRAY));
        if (hasKnownNext && page < totalPages) {
            nav.append(Component.literal(" "));
            nav.append(Messages.clickableCommand("[下一页 ›]", "查看下一页", String.format(commandPattern, page + 1), ChatFormatting.YELLOW));
        }
        nav.append(Component.literal(" "));
        nav.append(Messages.suggestable("[跳转]", "点击后输入页数", suggestCommand, ChatFormatting.GRAY));
        source.sendSuccess(() -> nav, false);
    }

    private static void burnHeldDisc(CommandSourceStack source, ServerPlayer player, TrackInfo track) {
        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!MusicDiscHelper.isBurnableDisc(mainHand)) {
            Messages.warning(source, "主手中的唱片已变化，请重新手持唱片后再刻录。");
            return;
        }

        ItemStack burnedDisc = MusicDiscHelper.burn(mainHand.copyWithCount(1), track);
        if (mainHand.getCount() == 1) {
            player.setItemInHand(InteractionHand.MAIN_HAND, burnedDisc);
        } else {
            mainHand.shrink(1);
            if (!player.getInventory().add(burnedDisc)) {
                player.drop(burnedDisc, false, true);
            }
        }

        source.sendSuccess(() -> Component.literal("已刻录音乐唱片: ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(track.title()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(track.artist()).withStyle(ChatFormatting.GRAY)), false);
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
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            }
            line.append(action);
            first = false;
        }
        if (!first) {
            source.sendSuccess(() -> line, false);
        }
    }

    private static Component renderHeader() {
        return Component.literal("━━━━━━━━━━━━━━━━━━━━━━ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("♫").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" ━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component spacer() {
        return Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static void sendHeader(CommandSourceStack source) {
        source.sendSuccess(() -> spacer(), false);
        source.sendSuccess(() -> spacer(), false);
        source.sendSuccess(() -> renderHeader(), false);
        source.sendSuccess(() -> spacer(), false);
    }

    private static MutableComponent sectionHeader(String title, String subtitle) {
        MutableComponent line = Component.literal("◆ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (subtitle != null && !subtitle.isBlank()) {
            line.append(Component.literal("  " + subtitle).withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    private static <T> List<T> slicePage(List<T> entries, PageWindow page) {
        if (entries.isEmpty()) {
            return List.of();
        }
        int start = (page.page() - 1) * page.pageSize();
        if (start >= entries.size()) {
            return List.of();
        }
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
