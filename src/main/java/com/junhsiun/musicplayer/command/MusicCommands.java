package com.junhsiun.musicplayer.command;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.model.ArtistInfo;
import com.junhsiun.musicplayer.model.PlayOrder;
import com.junhsiun.musicplayer.model.PlaylistInfo;
import com.junhsiun.musicplayer.model.ProgramInfo;
import com.junhsiun.musicplayer.model.RadioInfo;
import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.model.UserPlaylistView;
import com.junhsiun.musicplayer.network.OpenUrlPayload;
import com.junhsiun.musicplayer.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
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
    private static final String AUTHOR_USER_ID = "1732443319";
    private static final String REPOSITORY_URL = "https://github.com/xjuunn/MinecraftMusicPlayer";

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
                .then(radio())
                .then(config())
                .then(Commands.literal("star").executes(ctx -> sendStarAchievement(ctx.getSource()))));
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
        MutableComponent title = sectionHeader(Component.translatable("musicplayer.help.title"), null);
        title.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
        title.append(Messages.clickableCommand(Component.literal("Junhsiun"), Component.translatable("musicplayer.help.author_hover"), "/music view user " + AUTHOR_USER_ID, ChatFormatting.AQUA));
        title.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
        title.append(Component.translatable("musicplayer.help.repository")
                .withStyle(style -> style
                        .withColor(ChatFormatting.WHITE)
                        .withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/music star"))
                        .withHoverEvent(new HoverEvent.ShowText(renderRepositoryHover()))));
        Messages.sendSuccess(source, title, false);
        helpEntry(source, "now", Component.translatable("musicplayer.help.desc.now"));
        helpEntry(source, "play", Component.translatable("musicplayer.help.desc.play"));
        helpEntry(source, "skip", Component.translatable("musicplayer.help.desc.skip"));
        helpEntry(source, "stop", Component.translatable("musicplayer.help.desc.stop"));
        helpEntry(source, "queue", Component.translatable("musicplayer.help.desc.queue"));
        helpEntry(source, "playlist", Component.translatable("musicplayer.help.desc.playlist"));
        helpEntry(source, "search", Component.translatable("musicplayer.help.desc.search"));
        helpEntry(source, "view", Component.translatable("musicplayer.help.desc.view"));
        helpEntry(source, "join", Component.translatable("musicplayer.help.desc.join"));
        helpEntry(source, "leave", Component.translatable("musicplayer.help.desc.leave"));
        helpEntry(source, "mute", Component.translatable("musicplayer.help.desc.mute"));
        helpEntry(source, "burn", Component.translatable("musicplayer.help.desc.burn"));
        helpEntry(source, "random", Component.translatable("musicplayer.help.desc.random"));
        helpEntry(source, "lyrics", Component.translatable("musicplayer.help.desc.lyrics"));
        helpEntry(source, "radio", Component.translatable("musicplayer.help.desc.radio"));
        helpEntry(source, "help", Component.translatable("musicplayer.help.desc.help"));
        helpEntry(source, "config", Component.translatable("musicplayer.help.desc.config"));
        Messages.sendSuccess(source, spacer(), false);
        Messages.sendSuccess(source, Component.translatable("musicplayer.help.tip").withStyle(ChatFormatting.DARK_GRAY), false);
        Messages.sendSuccess(source, spacer(), false);
        return 1;
    }

    private static int sendStarAchievement(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        grantStarAchievement(source.getServer(), player);
        if (ServerPlayNetworking.canSend(player, OpenUrlPayload.TYPE)) {
            ServerPlayNetworking.send(player, new OpenUrlPayload(REPOSITORY_URL));
        }
        return 1;
    }

    private static void grantStarAchievement(MinecraftServer server, ServerPlayer player) {
        Identifier advancementId = Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "star");
        AdvancementHolder holder = server.getAdvancements().get(advancementId);
        if (holder == null) {
            MusicPlayerMod.LOGGER.warn("未找到成就定义: {}", advancementId);
            return;
        }
        player.getAdvancements().award(holder, "star");
    }

    private static Component renderRepositoryHover() {
        return Component.literal("✦ ").withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("musicplayer.help.star_unlocked").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.translatable("musicplayer.help.star_call").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("musicplayer.help.star_open").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void helpEntry(CommandSourceStack source, String command, Component description) {
        MutableComponent line = Messages.clickableCommand(Component.literal(command), Component.translatable("musicplayer.help.view_usage", command), "/music help " + command, ChatFormatting.GOLD);
        line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(description.copy().withStyle(ChatFormatting.GRAY));
        Messages.sendSuccess(source, line, false);
    }

    private static int sendHelpFor(CommandSourceStack source, String subcommand) {
        sendHeader(source);
        switch (subcommand) {
            case "now" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("now"), null), false);
                detailLine(source, "/music now", Component.translatable("musicplayer.help.usage.now"));
            }
            case "queue" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("queue"), null), false);
                detailLine(source, "/music queue", Component.translatable("musicplayer.help.usage.queue"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.queue_promote"), Component.translatable("musicplayer.help.usage.queue_promote"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.queue_remove"), Component.translatable("musicplayer.help.usage.queue_remove"));
                detailLine(source, "/music queue clear", Component.translatable("musicplayer.help.usage.queue_clear"));
            }
            case "play" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("play"), null), false);
                detailLine(source, Component.translatable("musicplayer.help.cmd.play_song"), Component.translatable("musicplayer.help.usage.play_song"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.play_playlist"), Component.translatable("musicplayer.help.usage.play_playlist"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.play_playlist_reverse"), Component.translatable("musicplayer.help.usage.play_playlist_reverse"));
            }
            case "playlist" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("playlist"), null), false);
                detailLine(source, "/music playlist", Component.translatable("musicplayer.help.usage.playlist"));
                detailLine(source, "/music playlist list", Component.translatable("musicplayer.help.usage.playlist_list"));
                detailLine(source, "/music playlist stop", Component.translatable("musicplayer.help.usage.playlist_stop"));
                detailLine(source, "/music playlist order", Component.translatable("musicplayer.help.usage.playlist_order"));
            }
            case "skip" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("skip"), null), false);
                detailLine(source, "/music skip", Component.translatable("musicplayer.help.usage.skip"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.skip_requester"), Component.translatable("musicplayer.help.usage.skip_no_vote"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.skip_admin"), Component.translatable("musicplayer.help.usage.skip_no_vote"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.skip_vote"), Component.translatable("musicplayer.help.usage.skip_vote"));
            }
            case "search" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("search"), null), false);
                detailLine(source, Component.translatable("musicplayer.help.cmd.search_song"), Component.translatable("musicplayer.help.usage.search_song"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.search_artist"), Component.translatable("musicplayer.help.usage.search_artist"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.search_playlist"), Component.translatable("musicplayer.help.usage.search_playlist"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.search_user"), Component.translatable("musicplayer.help.usage.search_user"));
            }
            case "view" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("view"), null), false);
                detailLine(source, Component.translatable("musicplayer.help.cmd.view_playlist"), Component.translatable("musicplayer.help.usage.view_playlist"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.view_artist"), Component.translatable("musicplayer.help.usage.view_artist"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.view_user"), Component.translatable("musicplayer.help.usage.view_user"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.view_url"), Component.translatable("musicplayer.help.usage.view_url"));
            }
            case "join" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("join"), null), false);
                detailLine(source, "/music join", Component.translatable("musicplayer.help.usage.join"));
            }
            case "leave" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("leave"), null), false);
                detailLine(source, "/music leave", Component.translatable("musicplayer.help.usage.leave"));
            }
            case "mute" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("mute"), null), false);
                detailLine(source, "/music mute once", Component.translatable("musicplayer.help.usage.mute_once"));
            }
            case "burn" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("burn"), null), false);
                detailLine(source, Component.translatable("musicplayer.help.cmd.burn_song"), Component.translatable("musicplayer.help.usage.burn_song"));
            }
            case "random" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("random"), null), false);
                detailLine(source, "/music random", Component.translatable("musicplayer.help.usage.random"));
            }
            case "help" -> {
                return sendHelpOverview(source);
            }
            case "lyrics" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("lyrics"), null), false);
                detailLine(source, "/music lyrics", Component.translatable("musicplayer.help.usage.lyrics"));
                detailLine(source, "/music lyrics on", Component.translatable("musicplayer.help.usage.lyrics_on"));
                detailLine(source, "/music lyrics off", Component.translatable("musicplayer.help.usage.lyrics_off"));
                detailLine(source, "/music lyrics status", Component.translatable("musicplayer.help.usage.lyrics_status"));
            }
            case "stop" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("stop"), null), false);
                detailLine(source, "/music stop", Component.translatable("musicplayer.help.usage.stop"));
            }
            case "config" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("config"), null), false);
                detailLine(source, "/music config reload", Component.translatable("musicplayer.help.usage.config_reload"));
                detailLine(source, "/music config status", Component.translatable("musicplayer.help.usage.config_status"));
                detailLine(source, "/music config clearqueue", Component.translatable("musicplayer.help.usage.config_clearqueue"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.config_set"), Component.translatable("musicplayer.help.usage.config_set"));
            }
            case "radio" -> {
                Messages.sendSuccess(source,  sectionHeader(Component.literal("radio"), null), false);
                detailLine(source, Component.translatable("musicplayer.help.cmd.radio_hot"), Component.translatable("musicplayer.help.usage.radio_hot"));
                detailLine(source, "/music radio categories", Component.translatable("musicplayer.help.usage.radio_categories"));
                detailLine(source, "/music view radio <id>", Component.translatable("musicplayer.help.usage.view_radio"));
                detailLine(source, "/music view program <id>", Component.translatable("musicplayer.help.usage.view_program"));
                detailLine(source, Component.translatable("musicplayer.help.cmd.search_radio"), Component.translatable("musicplayer.help.usage.search_radio"));
                detailLine(source, "/music play program <id>", Component.translatable("musicplayer.help.usage.play_program"));
                detailLine(source, "/music play radio <id>", Component.translatable("musicplayer.help.usage.play_radio"));
                detailLine(source, "/music play radio <id> reverse", Component.translatable("musicplayer.help.usage.play_radio_reverse"));
            }
            default -> {
                Messages.sendSuccess(source,  Component.translatable("musicplayer.help.unknown_subcommand", subcommand).withStyle(ChatFormatting.RED), false);
                Messages.sendSuccess(source,  spacer(), false);
                return sendHelpOverview(source);
            }
        }
        Messages.sendSuccess(source,  spacer(), false);
        return 1;
    }

    private static void detailLine(CommandSourceStack source, String command, Component description) {
        detailLine(source, Component.literal(command), description);
    }

    private static void detailLine(CommandSourceStack source, Component command, Component description) {
        MutableComponent line = command.copy().withStyle(ChatFormatting.GOLD);
        line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(description.copy().withStyle(ChatFormatting.GRAY));
        Messages.sendSuccess(source,  line, false);
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
                Messages.clickableCommand(Component.translatable("musicplayer.now.rewind_label"), Component.translatable("musicplayer.now.rewind_hover"), "/music seek -5", ChatFormatting.GRAY),
                isPaused
                        ? (isAdmin ? Messages.clickableCommand(Component.translatable("musicplayer.now.resume_label"), Component.translatable("musicplayer.now.resume_hover"), "/music resume", ChatFormatting.GREEN) : null)
                        : (isAdmin ? Messages.clickableCommand(Component.translatable("musicplayer.now.pause_label"), Component.translatable("musicplayer.now.pause_hover"), "/music pause", ChatFormatting.YELLOW) : null),
                Messages.clickableCommand(Component.translatable("musicplayer.now.forward_label"), Component.translatable("musicplayer.now.forward_hover"), "/music seek 5", ChatFormatting.GRAY),
                Messages.clickableCommand(Component.translatable("musicplayer.common.skip_label"), Component.translatable("musicplayer.common.skip_hover"), "/music skip", ChatFormatting.YELLOW),
                Messages.clickableCommand(Component.translatable("musicplayer.common.queue_label"), Component.translatable("musicplayer.common.queue_hover"), "/music queue", ChatFormatting.GRAY),
                Messages.clickableCommand(Component.translatable("musicplayer.common.playlist_label"), Component.translatable("musicplayer.now.playlist_hover"), "/music playlist", ChatFormatting.AQUA),
                Messages.clickableCommand(Component.translatable("musicplayer.common.help_label"), Component.translatable("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));
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
                        player.sendSystemMessage(Component.literal(icon + "  ").withStyle(ChatFormatting.GRAY)
                                .append(Component.translatable("musicplayer.seek.seconds", Math.abs(delta)).withStyle(ChatFormatting.GRAY)));
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
                                Messages.warning(context.getSource(), Component.translatable("musicplayer.queue.promote_not_found"));
                                return 0;
                            }
                            Messages.success(context.getSource(), Component.translatable("musicplayer.queue.promoted"), false);
                            return showQueue(context.getSource(), 1);
                        })))
                .then(Commands.literal("remove")
                        .then(Commands.argument("song_id", StringArgumentType.string()).executes(context -> {
                            String songId = StringArgumentType.getString(context, "song_id");
                            if (!MusicPlayerMod.queueService().removeFromQueue(songId)) {
                                Messages.warning(context.getSource(), Component.translatable("musicplayer.queue.not_found"));
                                return 0;
                            }
                            Messages.success(context.getSource(), Component.translatable("musicplayer.queue.removed"), false);
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
                Messages.clickableCommand(Component.translatable("musicplayer.queue.now_label"), Component.translatable("musicplayer.queue.now_hover"), "/music now", ChatFormatting.AQUA),
                Messages.clickableCommand(Component.translatable("musicplayer.queue.refresh_label"), Component.translatable("musicplayer.queue.refresh_hover"), "/music queue " + page.page(), ChatFormatting.YELLOW),
                Messages.clickableCommand(Component.translatable("musicplayer.common.skip_label"), Component.translatable("musicplayer.common.skip_hover"), "/music skip", ChatFormatting.GRAY),
                Messages.clickableCommand(Component.translatable("musicplayer.common.help_label"), Component.translatable("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));
        if (currentTrack == null) {
            Messages.sendSuccess(source,  Component.translatable("musicplayer.queue.nothing_playing").withStyle(ChatFormatting.GRAY), false);
        } else {
            long elapsedMs = MusicPlayerMod.queueService().playbackElapsedMillis();
            long durationMs = MusicPlayerMod.queueService().playbackDurationMillis();
            String requesterName = MusicPlayerMod.queueService().currentRequesterName();
            String elapsed = Messages.formatDuration(elapsedMs);
            String duration = durationMs > 0L ? Messages.formatDuration(durationMs) : "";
            Messages.sendSuccess(source,  renderCurrentTrack(source, currentTrack, elapsed, duration, requesterName), false);
            Messages.sendSuccess(source,  renderProgressLine(elapsed, duration, requesterName, MusicPlayerMod.queueService().isPaused(), elapsedMs, durationMs), false);
        }
        if (totalEntries == 0) {
            Messages.sendSuccess(source,  Component.translatable("musicplayer.queue.empty").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        Messages.sendSuccess(source,  spacer(), false);
        Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.queue.title"), Component.translatable("musicplayer.queue.hint")), false);
        List<SearchEntry> entries = MusicPlayerMod.queueService().queuedEntries(page.page(), page.pageSize());
        for (int index = 0; index < entries.size(); index++) {
            SearchEntry entry = entries.get(index);
            int order = (page.page() - 1) * page.pageSize() + index + 1;
            MutableComponent line = Component.literal(order + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Messages.clickableCommand(Component.translatable("musicplayer.queue.next_label"), Component.translatable("musicplayer.queue.next_hover"), "/music queue promote " + entry.id(), ChatFormatting.GREEN))
                    .append(Component.literal(" "))
                    .append(clickableText(entry.title(), entry.titleCommand(), Component.translatable("musicplayer.queue.replay_hover"), ChatFormatting.AQUA));
            if (entry.hasSubtitle()) {
                line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
                line.append(clickableText(entry.subtitle(), entry.subtitleCommand(), Component.translatable("musicplayer.common.view_artist_hover"), ChatFormatting.GRAY));
            }
            Messages.sendSuccess(source,  line, false);
        }
        Messages.sendSuccess(source,  spacer(), false);
        sendNavigation(source, page.page(), page.totalPages(), "/music queue %d", true, "/music queue ");
        Messages.sendSuccess(source,  spacer(), false);
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
                        }))
                .then(Commands.literal("order")
                        .executes(context -> {
                            PlayOrder current = MusicPlayerMod.queueService().playOrder();
                            Messages.info(context.getSource(), Component.translatable("musicplayer.playlist.current_order", Component.translatable(current.translationKey())), false);
                            return 1;
                        })
                        .then(Commands.literal("sequential").executes(context -> {
                            MusicPlayerMod.queueService().setPlayOrder(PlayOrder.SEQUENTIAL);
                            Messages.success(context.getSource(), Component.translatable("musicplayer.playlist.order_changed", Component.translatable("musicplayer.play_order.sequential")), false);
                            return 1;
                        }))
                        .then(Commands.literal("reverse").executes(context -> {
                            MusicPlayerMod.queueService().setPlayOrder(PlayOrder.REVERSE);
                            Messages.success(context.getSource(), Component.translatable("musicplayer.playlist.order_changed", Component.translatable("musicplayer.play_order.reverse")), false);
                            return 1;
                        }))
                        .then(Commands.literal("shuffle").executes(context -> {
                            MusicPlayerMod.queueService().setPlayOrder(PlayOrder.SHUFFLE);
                            Messages.success(context.getSource(), Component.translatable("musicplayer.playlist.order_changed", Component.translatable("musicplayer.play_order.shuffle")), false);
                            return 1;
                        })));
    }

    private static int showPlaylistStatus(CommandSourceStack source) {
        sendHeader(source);
        boolean isPlaylistMode = MusicPlayerMod.queueService().isPlaylistMode();
        int queueSize = MusicPlayerMod.queueService().queuedCount();
        int remaining = MusicPlayerMod.queueService().playlistRemainingCount();

        sendQuickBar(source,
                Messages.clickableCommand(Component.translatable("musicplayer.common.queue_label"), Component.translatable("musicplayer.common.queue_hover"), "/music queue", ChatFormatting.YELLOW),
                Messages.clickableCommand(Component.translatable("musicplayer.playlist.loaded_label"), Component.translatable("musicplayer.playlist.loaded_hover"), "/music playlist list", ChatFormatting.AQUA),
                isPlaylistMode ? Messages.clickableCommand(Component.translatable("musicplayer.playlist.stop_label"), Component.translatable("musicplayer.playlist.stop_hover"), "/music playlist stop", ChatFormatting.DARK_GRAY) : null,
                Messages.clickableCommand(Component.translatable("musicplayer.common.help_label"), Component.translatable("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));

        TrackInfo currentTrack = MusicPlayerMod.queueService().currentTrack();
        if (currentTrack != null) {
            Messages.sendSuccess(source,  spacer(), false);
            long elapsedMs = MusicPlayerMod.queueService().playbackElapsedMillis();
            long durationMs = MusicPlayerMod.queueService().playbackDurationMillis();
            String requesterName = MusicPlayerMod.queueService().currentRequesterName();
            String elapsed = Messages.formatDuration(elapsedMs);
            String duration = durationMs > 0L ? Messages.formatDuration(durationMs) : "";
            Messages.sendSuccess(source,  renderCurrentTrack(source, currentTrack, elapsed, duration, requesterName), false);
            Messages.sendSuccess(source,  renderProgressLine(elapsed, duration, requesterName, MusicPlayerMod.queueService().isPaused(), elapsedMs, durationMs), false);
        }

        Messages.sendSuccess(source,  spacer(), false);
        MutableComponent statusLine = Component.translatable("musicplayer.playlist.queue_status",
                Component.literal(String.valueOf(queueSize)).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GOLD);
        if (isPlaylistMode) {
            statusLine.append(Component.literal("  ·  ").withStyle(ChatFormatting.GOLD))
                    .append(Component.translatable("musicplayer.playlist.playlist_queue_status",
                            Component.literal(String.valueOf(remaining)).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("  ·  ").withStyle(ChatFormatting.GOLD))
                    .append(Component.translatable("musicplayer.playlist.direction_prefix").withStyle(ChatFormatting.GOLD))
                    .append(Component.translatable(MusicPlayerMod.queueService().isPlaylistReversed() ? "musicplayer.play_order.reverse" : "musicplayer.play_order.sequential").withStyle(ChatFormatting.AQUA));
        }
        statusLine.append(Component.literal("  ·  ").withStyle(ChatFormatting.GOLD))
                .append(Component.translatable("musicplayer.playlist.order_prefix").withStyle(ChatFormatting.GOLD))
                .append(Messages.clickableCommand(
                        Component.translatable(MusicPlayerMod.queueService().playOrder().translationKey()),
                        Component.translatable("musicplayer.playlist.order_hover"),
                        "/music playlist order",
                        ChatFormatting.AQUA));
        Messages.sendSuccess(source,  statusLine, false);
        Messages.sendSuccess(source,  spacer(), false);
        return 1;
    }

    private static int showPlaylistTracks(CommandSourceStack source) {
        sendHeader(source);
        if (!MusicPlayerMod.queueService().isPlaylistMode()) {
            Messages.sendSuccess(source,  Component.translatable("musicplayer.playlist.none").withStyle(ChatFormatting.GRAY), false);
            Messages.sendSuccess(source,  spacer(), false);
            return 1;
        }

        List<SearchEntry> entries = MusicPlayerMod.queueService().playlistEntries();
        int totalEntries = entries.size();
        int remaining = MusicPlayerMod.queueService().playlistRemainingCount();

        sendQuickBar(source,
                Messages.clickableCommand(Component.translatable("musicplayer.playlist.status_label"), Component.translatable("musicplayer.playlist.status_hover"), "/music playlist", ChatFormatting.AQUA),
                Messages.clickableCommand(Component.translatable("musicplayer.playlist.stop_label"), Component.translatable("musicplayer.playlist.stop_hover"), "/music playlist stop", ChatFormatting.DARK_GRAY),
                Messages.clickableCommand(Component.translatable("musicplayer.common.help_label"), Component.translatable("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));

        Messages.sendSuccess(source,  spacer(), false);
        Messages.sendSuccess(source,  Component.translatable("musicplayer.playlist.list_status", totalEntries, remaining).withStyle(ChatFormatting.DARK_GRAY), false);
        Messages.sendSuccess(source,  spacer(), false);

        for (int index = 0; index < entries.size(); index++) {
            SearchEntry entry = entries.get(index);
            MutableComponent line = Component.literal((index + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Messages.clickableCommand(Component.translatable("musicplayer.common.request_label"), Component.translatable("musicplayer.common.rerequest_hover"), "/music play song " + entry.id(), ChatFormatting.GREEN))
                    .append(Component.literal(" "))
                    .append(clickableText(entry.title(), entry.titleCommand(), Component.translatable("musicplayer.common.open_browser_hover"), ChatFormatting.AQUA));
            if (entry.hasSubtitle()) {
                line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
                line.append(clickableText(entry.subtitle(), entry.subtitleCommand(), Component.translatable("musicplayer.common.view_artist_hover"), ChatFormatting.GRAY));
            }
            Messages.sendSuccess(source,  line, false);
        }
        Messages.sendSuccess(source,  spacer(), false);
        return 1;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> lyrics() {
        return Commands.literal("lyrics")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    boolean now = MusicPlayerMod.queueService().toggleLyrics(player);
                    Messages.success(context.getSource(), Component.translatable(now ? "musicplayer.lyrics.enabled" : "musicplayer.lyrics.disabled"), false);
                    return 1;
                })
                .then(Commands.literal("on").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MusicPlayerMod.queueService().toggleLyrics(player, true);
                    Messages.success(context.getSource(), Component.translatable("musicplayer.lyrics.enabled"), false);
                    return 1;
                }))
                .then(Commands.literal("off").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MusicPlayerMod.queueService().toggleLyrics(player, false);
                    Messages.success(context.getSource(), Component.translatable("musicplayer.lyrics.disabled"), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    boolean on = MusicPlayerMod.queueService().isLyricsEnabled(player);
                    Messages.info(context.getSource(), Component.translatable("musicplayer.lyrics.status", yesNo(on)), false);
                    return 1;
                }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> join() {
        return Commands.literal("join").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            MusicPlayerMod.queueService().joinPlayer(player);
            Messages.success(context.getSource(), Component.translatable("musicplayer.join.done"), false);
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> leave() {
        return Commands.literal("leave").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            MusicPlayerMod.queueService().leavePlayer(player);
            Messages.success(context.getSource(), Component.translatable("musicplayer.leave.done"), false);
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> muteOnce() {
        return Commands.literal("mute")
                .then(Commands.literal("once").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MusicPlayerMod.queueService().mutePlayerOnce(player);
                    Messages.success(context.getSource(), Component.translatable("musicplayer.mute.done"), false);
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
                            loading(context.getSource(), MusicPlayerMod.queueService().isPlaying() ? Component.translatable("musicplayer.play.loading_queue") : Component.translatable("musicplayer.play.loading_prepare"));
                            MusicPlayerMod.queueService().requestSong(context.getSource().getServer(), context.getSource(), player, songId);
                            return 1;
                        })))
                    .then(Commands.literal("playlist")
                            .then(Commands.argument("playlist_id", StringArgumentType.string()).executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                String playlistId = StringArgumentType.getString(context, "playlist_id");
                                loading(context.getSource(), Component.translatable("musicplayer.play.loading_playlist"));
                                MusicPlayerMod.queueService().requestPlaylist(context.getSource().getServer(), context.getSource(), player, playlistId);
                                return 1;
                            }).then(Commands.literal("reverse").executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                String playlistId = StringArgumentType.getString(context, "playlist_id");
                                loading(context.getSource(), Component.translatable("musicplayer.play.loading_playlist_reverse"));
                                MusicPlayerMod.queueService().requestPlaylist(context.getSource().getServer(), context.getSource(), player, playlistId, true);
                                return 1;
                            }))))
                    .then(Commands.literal("program")
                            .then(Commands.argument("program_id", StringArgumentType.string()).executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                String programId = StringArgumentType.getString(context, "program_id");
                                loading(context.getSource(), Component.translatable("musicplayer.play.loading_program"));
                                MusicPlayerMod.queueService().requestProgram(context.getSource().getServer(), context.getSource(), player, programId);
                                return 1;
                            })))
                    .then(Commands.literal("radio")
                            .then(Commands.argument("radio_id", StringArgumentType.string()).executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                String radioId = StringArgumentType.getString(context, "radio_id");
                                loading(context.getSource(), Component.translatable("musicplayer.play.loading_radio"));
                                MusicPlayerMod.queueService().requestRadio(context.getSource().getServer(), context.getSource(), player, radioId, false);
                                return 1;
                            }).then(Commands.literal("reverse").executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                String radioId = StringArgumentType.getString(context, "radio_id");
                                loading(context.getSource(), Component.translatable("musicplayer.play.loading_radio_reverse"));
                                MusicPlayerMod.queueService().requestRadio(context.getSource().getServer(), context.getSource(), player, radioId, true);
                                return 1;
                            }))));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> burn() {
        return Commands.literal("burn")
                .then(Commands.literal("song")
                        .then(Commands.argument("song_id", StringArgumentType.string()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (!MusicDiscHelper.isBurnableDisc(player.getItemInHand(InteractionHand.MAIN_HAND))) {
                                Messages.warning(context.getSource(), Component.translatable("musicplayer.burn.need_disc"));
                                return 0;
                            }
                            String songId = StringArgumentType.getString(context, "song_id");
                            loading(context.getSource(), Component.translatable("musicplayer.burn.loading"));
                            MinecraftServer server = context.getSource().getServer();
                            MusicPlayerMod.netease().resolveSong(songId).whenComplete((track, throwable) -> server.execute(() -> {
                                if (throwable != null) {
                                    Messages.warning(context.getSource(), Component.translatable("musicplayer.burn.failed", Messages.textOrTranslatable(rootMessage(throwable))));
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
        loading(source, Component.translatable("musicplayer.random.loading"));
        MinecraftServer server = source.getServer();
        MusicPlayerMod.netease().randomHotTracks(10).whenComplete((tracks, throwable) ->
                server.execute(() -> showRandomTracks(source, tracks, throwable)));
        return 1;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> radio() {
        return Commands.literal("radio")
                .executes(context -> {
                    sendHeader(context.getSource());
                    context.getSource().sendSuccess(() -> sectionHeader(Component.translatable("musicplayer.radio.title"), Component.translatable("musicplayer.radio.subtitle")), false);
                    return 1;
                })
                .then(Commands.literal("hot")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                            int page = IntegerArgumentType.getInteger(context, "page");
                            showHotRadios(context.getSource(), page);
                            return 1;
                        }))
                        .executes(context -> {
                            showHotRadios(context.getSource(), 1);
                            return 1;
                        }))
                .then(Commands.literal("categories")
                        .executes(context -> showRadioCategories(context.getSource())));
    }

    private static void showHotRadios(CommandSourceStack source, int page) {
        loading(source, Component.translatable("musicplayer.radio.loading_hot"));
        MinecraftServer server = source.getServer();
        int offset = (page - 1) * 30;
        MusicPlayerMod.netease().hotRadios(30, offset).whenComplete((results, throwable) -> server.execute(() -> {
            if (throwable != null) {
                Messages.warning(source, Component.translatable("musicplayer.radio.hot_failed", Messages.textOrTranslatable(rootMessage(throwable))));
                return;
            }
            if (results.isEmpty()) {
                Messages.warning(source, Component.translatable("musicplayer.radio.no_more_hot"));
                return;
            }
            sendHeader(source);
            Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.radio.hot_title"), Component.translatable("musicplayer.radio.page_hint", page)), false);
            Messages.sendSuccess(source,  spacer(), false);
            for (SearchEntry entry : results) {
                Messages.sendSuccess(source,  renderEntry(entry, Messages.clickableCommand(Component.translatable("musicplayer.common.view_label"), Component.translatable("musicplayer.common.view_radio_hover"), "/music view radio " + entry.id(), ChatFormatting.GREEN), Component.translatable("musicplayer.common.view_radio_hover"), Component.empty()), false);
            }
            Messages.sendSuccess(source,  spacer(), false);
        }));
    }

    private static int showRadioCategories(CommandSourceStack source) {
        loading(source, Component.translatable("musicplayer.radio.loading_categories"));
        MinecraftServer server = source.getServer();
        MusicPlayerMod.netease().radioCategories().whenComplete((categories, throwable) -> server.execute(() -> {
            if (throwable != null) {
                Messages.warning(source, Component.translatable("musicplayer.radio.categories_failed", Messages.textOrTranslatable(rootMessage(throwable))));
                return;
            }
            sendHeader(source);
            Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.radio.categories_title"), Component.translatable("musicplayer.radio.categories_hint")), false);
            Messages.sendSuccess(source,  spacer(), false);
            for (SearchEntry entry : categories) {
                Messages.sendSuccess(source,  renderEntry(entry, Component.literal("").withStyle(ChatFormatting.GREEN), Component.translatable("musicplayer.radio.category_hover"), Component.empty()), false);
            }
            Messages.sendSuccess(source,  spacer(), false);
        }));
        return 1;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> search() {
        return Commands.literal("search")
                .then(pagedSearch("song", Component.translatable("musicplayer.search.loading_song"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchSongs(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendSongResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("artist", Component.translatable("musicplayer.search.loading_artist"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchArtists(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendArtistResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("author", Component.translatable("musicplayer.search.loading_artist"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchArtists(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendArtistResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("playlist", Component.translatable("musicplayer.search.loading_playlist"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchPlaylists(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendPlaylistResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("user", Component.translatable("musicplayer.search.loading_user"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchUsers(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendUserResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("radio", Component.translatable("musicplayer.search.loading_radio"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchRadios(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendRadioResults(source, keyword, page, literal, results, throwable)));
                }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> view() {
        return Commands.literal("view")
                .then(pagedView("playlist", "playlist_id", Component.translatable("musicplayer.view.loading_playlist"), (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().playlistDetail(id).whenComplete((playlist, throwable) -> server.execute(() -> showPlaylist(source, id, page, playlist, throwable)));
                }))
                .then(pagedView("user", "user_id", Component.translatable("musicplayer.view.loading_user"), (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().userPlaylists(id).whenComplete((user, throwable) -> server.execute(() -> showUserPlaylists(source, id, page, user, throwable)));
                }))
                .then(pagedView("artist", "artist_id", Component.translatable("musicplayer.view.loading_artist"), (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().artistDetail(id).whenComplete((artist, throwable) -> server.execute(() -> showArtist(source, id, page, artist, throwable, "artist")));
                }))
                .then(pagedView("author", "artist_id", Component.translatable("musicplayer.view.loading_artist"), (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().artistDetail(id).whenComplete((artist, throwable) -> server.execute(() -> showArtist(source, id, page, artist, throwable, "author")));
                }))
                .then(viewUrl())
                .then(pagedView("radio", "radio_id", Component.translatable("musicplayer.view.loading_radio"), (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().radioDetail(id).whenComplete((radio, throwable) ->
                            server.execute(() -> showRadioDetail(source, id, page, radio, throwable)));
                }))
                .then(pagedView("program", "program_id", Component.translatable("musicplayer.view.loading_program"), (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().programDetail(id).whenComplete((program, throwable) ->
                            server.execute(() -> showProgramDetail(source, program, throwable)));
                }));
    }

    private static final Pattern URL_ID_PATTERN = Pattern.compile("[?&]id=(\\d+)");

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> viewUrl() {
        return Commands.argument("url", StringArgumentType.greedyString()).executes(context -> {
            String url = StringArgumentType.getString(context, "url");
            Matcher m = URL_ID_PATTERN.matcher(url);
            if (!m.find()) {
                Messages.warning(context.getSource(), Component.translatable("musicplayer.view.url_no_id"));
                return 0;
            }
            String id = m.group(1);
            MinecraftServer server = context.getSource().getServer();

            if (url.contains("/song")) {
                loading(context.getSource(), Component.translatable("musicplayer.view.loading_song"));
                MusicPlayerMod.netease().resolveSong(id).whenComplete((track, throwable) ->
                        server.execute(() -> showSong(context.getSource(), track, throwable)));
            } else if (url.contains("/playlist")) {
                loading(context.getSource(), Component.translatable("musicplayer.view.loading_playlist"));
                MusicPlayerMod.netease().playlistDetail(id).whenComplete((playlist, throwable) ->
                        server.execute(() -> showPlaylist(context.getSource(), id, 1, playlist, throwable)));
            } else if (url.contains("/djradio") || url.contains("/dj")) {
                loading(context.getSource(), Component.translatable("musicplayer.view.loading_radio"));
                MusicPlayerMod.netease().radioDetail(id).whenComplete((radio, throwable) ->
                        server.execute(() -> showRadioDetail(context.getSource(), id, 1, radio, throwable)));
            } else {
                Messages.warning(context.getSource(), Component.translatable("musicplayer.view.unsupported_url"));
                return 0;
            }
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> stop() {
        return Commands.literal("stop")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(context -> {
                    MusicPlayerMod.queueService().stop(context.getSource().getServer(), "musicplayer.stop.admin");
                    return 1;
                });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> config() {
        return Commands.literal("config")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .then(Commands.literal("reload").executes(context -> {
                    MusicPlayerConfigManager.load();
                    MusicPlayerMod.queueService().clearTrackCache();
                    Messages.success(context.getSource(), Component.translatable("musicplayer.config.reloaded"), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {
                    MusicPlayerConfig config = MusicPlayerConfigManager.get();
                    Messages.info(context.getSource(), Component.translatable("musicplayer.config.status_base_url", config.neteaseBaseUrl), false);
                    Messages.info(context.getSource(), Component.translatable("musicplayer.config.status_requests", yesNo(config.allowSongRequest), yesNo(config.allowPlaylistRequest)), false);
                    Messages.info(context.getSource(), Component.translatable("musicplayer.config.status_auto", yesNo(config.autoAdvance), yesNo(config.showLoadingHints)), false);
                    Messages.info(context.getSource(), Component.translatable("musicplayer.config.status_lyrics", yesNo(config.showLyrics)), false);
                    Messages.info(context.getSource(), Component.translatable("musicplayer.config.status_proxy",
                            !config.proxy.isBlank()
                                    ? Component.translatable("musicplayer.config.proxy_manual", config.proxy)
                                    : Component.translatable(config.useSystemProxy ? "musicplayer.config.proxy_auto" : "musicplayer.config.proxy_direct")), false);
                    Messages.info(context.getSource(), Component.translatable("musicplayer.config.status_network", yesNo(config.preferIpv4), config.connectTimeoutSeconds, config.readTimeoutSeconds), false);
                    Messages.info(context.getSource(), Component.translatable("musicplayer.config.status_limits", config.searchLimit, config.maxQueueSize, config.maxSongsPerPlayer, config.playlistQueueLimit, config.queueCacheSize), false);
                    Messages.info(context.getSource(), Component.translatable("musicplayer.config.status_vote_skip", config.voteSkipPercent), false);
                    Messages.info(context.getSource(), Component.translatable("musicplayer.config.status_loot", yesNo(config.enableLootMusicDiscs), config.lootMusicDiscChance, config.lootMusicDiscCount), false);
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
                                Messages.warning(context.getSource(), Component.translatable("musicplayer.config.custom_server_disabled"));
                                return 0;
                            }
                            config.neteaseBaseUrl = "default".equalsIgnoreCase(value) ? MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL : value;
                            MusicPlayerConfigManager.save();
                            Messages.success(context.getSource(), Component.translatable("musicplayer.config.base_url_updated", config.neteaseBaseUrl), false);
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
                            Messages.success(context.getSource(), Component.translatable("musicplayer.config.proxy_updated", MusicPlayerConfigManager.get().proxy.isBlank() ? "<none>" : MusicPlayerConfigManager.get().proxy), false);
                            return 1;
                        })))
                        .then(intSetting("connectTimeoutSeconds", 3, 60, value -> MusicPlayerConfigManager.get().connectTimeoutSeconds = value))
                        .then(intSetting("readTimeoutSeconds", 3, 120, value -> MusicPlayerConfigManager.get().readTimeoutSeconds = value))
                        .then(intSetting("searchLimit", 3, 20, value -> MusicPlayerConfigManager.get().searchLimit = value))
                        .then(intSetting("maxQueueSize", 1, 200, value -> MusicPlayerConfigManager.get().maxQueueSize = value))
                        .then(intSetting("maxSongsPerPlayer", 1, 50, value -> MusicPlayerConfigManager.get().maxSongsPerPlayer = value))
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

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> pagedSearch(String literal, Component loadingText, PagedSearchExecutor executor) {
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

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> pagedView(String literal, String idArgument, Component loadingText, PagedViewExecutor executor) {
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
            Messages.success(context.getSource(), Component.translatable("musicplayer.config.updated", name, value), false);
            return 1;
        }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> intSetting(String name, int min, int max, IntConsumer setter) {
        return Commands.literal(name).then(Commands.argument("value", IntegerArgumentType.integer(min, max)).executes(context -> {
            int value = IntegerArgumentType.getInteger(context, "value");
            setter.accept(value);
            MusicPlayerConfigManager.save();
            Messages.success(context.getSource(), Component.translatable("musicplayer.config.updated", name, value), false);
            return 1;
        }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> doubleSetting(String name, double min, double max, DoubleConsumer setter) {
        return Commands.literal(name).then(Commands.argument("value", DoubleArgumentType.doubleArg(min, max)).executes(context -> {
            double value = DoubleArgumentType.getDouble(context, "value");
            setter.accept(value);
            MusicPlayerConfigManager.save();
            Messages.success(context.getSource(), Component.translatable("musicplayer.config.updated", name, value), false);
            return 1;
        }));
    }

    private static void sendSongResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, Component.translatable("musicplayer.search.song_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, Component.translatable("musicplayer.search.no_songs"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.search.songs_title"), Component.translatable("musicplayer.search.songs_hint")), false);
        Messages.sendSuccess(source,  spacer(), false);
        for (SearchEntry entry : results) {
            Messages.sendSuccess(source,  renderEntry(entry, trackActions(source, entry.id(), Component.translatable("musicplayer.common.request_label"), Component.translatable("musicplayer.common.request_hover"), ChatFormatting.GREEN), Component.translatable("musicplayer.common.request_hover"), Component.translatable("musicplayer.common.click_artist_hover")), false);
        }
        Messages.sendSuccess(source,  spacer(), false);
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void sendArtistResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, Component.translatable("musicplayer.search.artist_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, Component.translatable("musicplayer.search.no_artists"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.search.artists_title"), Component.translatable("musicplayer.search.artists_hint")), false);
        Messages.sendSuccess(source,  spacer(), false);
        for (SearchEntry entry : results) {
            Messages.sendSuccess(source,  renderEntry(entry, Messages.clickableCommand(Component.translatable("musicplayer.common.view_label"), Component.translatable("musicplayer.common.view_artist_hover"), "/music view artist " + entry.id(), ChatFormatting.GREEN), Component.translatable("musicplayer.common.view_artist_hover"), Component.empty()), false);
        }
        Messages.sendSuccess(source,  spacer(), false);
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void sendPlaylistResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, Component.translatable("musicplayer.search.playlist_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, Component.translatable("musicplayer.search.no_playlists"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.search.playlists_title"), Component.translatable("musicplayer.search.playlists_hint")), false);
        Messages.sendSuccess(source,  spacer(), false);
        for (SearchEntry entry : results) {
            Messages.sendSuccess(source,  renderEntry(entry, Messages.clickableCommand(Component.translatable("musicplayer.common.view_label"), Component.translatable("musicplayer.common.view_playlist_hover"), "/music view playlist " + entry.id(), ChatFormatting.GREEN), Component.translatable("musicplayer.common.view_playlist_hover"), Component.translatable("musicplayer.common.click_creator_hover")), false);
        }
        Messages.sendSuccess(source,  spacer(), false);
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void sendUserResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, Component.translatable("musicplayer.search.user_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, Component.translatable("musicplayer.search.no_users"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.search.users_title"), Component.translatable("musicplayer.search.users_hint")), false);
        Messages.sendSuccess(source,  spacer(), false);
        for (SearchEntry entry : results) {
            Messages.sendSuccess(source,  renderEntry(entry, Messages.clickableCommand(Component.translatable("musicplayer.common.view_label"), Component.translatable("musicplayer.common.view_user_playlists_hover"), "/music view user " + entry.id(), ChatFormatting.GREEN), Component.translatable("musicplayer.common.view_user_playlists_hover"), Component.empty()), false);
        }
        Messages.sendSuccess(source,  spacer(), false);
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void showRandomTracks(CommandSourceStack source, List<TrackInfo> tracks, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, Component.translatable("musicplayer.random.failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (tracks == null || tracks.isEmpty()) {
            Messages.warning(source, Component.translatable("musicplayer.random.empty"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.random.title"), Component.translatable("musicplayer.random.hint")), false);
        Messages.sendSuccess(source,  spacer(), false);
        for (TrackInfo track : tracks) {
            Messages.sendSuccess(source,  renderRandomTrack(source, track), false);
        }
        Messages.sendSuccess(source,  spacer(), false);
    }

    private static void showPlaylist(CommandSourceStack source, String playlistId, int requestedPage, PlaylistInfo playlist, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, Component.translatable("musicplayer.playlist.load_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (playlist == null) {
            Messages.warning(source, Component.translatable("musicplayer.playlist.not_found"));
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
                    Messages.sendSuccess(source,  Component.translatable("musicplayer.playlist.header_prefix").withStyle(ChatFormatting.GOLD)
                            .append(clickableText(playlist.title(), "/music view playlist " + playlist.id(), Component.translatable("musicplayer.common.view_playlist_hover"), ChatFormatting.AQUA))
                            .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                            .append(Component.translatable("musicplayer.playlist.owner_prefix").withStyle(ChatFormatting.GRAY))
                            .append(clickableText(playlist.ownerName(), "/music view user " + playlist.ownerId(), Component.translatable("musicplayer.common.view_creator_hover"), ChatFormatting.YELLOW))
                            .append(Component.literal(" "))
                            .append(Messages.clickableCommand(Component.translatable("musicplayer.playlist.play_label"), Component.translatable("musicplayer.playlist.play_hover"), "/music play playlist " + playlist.id(), ChatFormatting.GREEN))
                            .append(Component.literal(" "))
                            .append(Messages.clickableCommand(Component.translatable("musicplayer.playlist.reverse_label"), Component.translatable("musicplayer.playlist.reverse_hover"), "/music play playlist " + playlist.id() + " reverse", ChatFormatting.GOLD)), false);
                    Messages.sendSuccess(source,  spacer(), false);

                    if (t != null) {
                        Messages.warning(source, Component.translatable("musicplayer.playlist.tracks_failed", Messages.textOrTranslatable(rootMessage(t))));
                        return;
                    }
                    for (SearchEntry track : tracks) {
                        Messages.sendSuccess(source,  renderEntry(track,
                                trackActions(source, track.id(), Component.translatable("musicplayer.common.request_label"), Component.translatable("musicplayer.play.request_hover"), ChatFormatting.GREEN),
                                Component.translatable("musicplayer.play.request_hover"), Component.translatable("musicplayer.common.click_artist_hover")), false);
                    }
                    Messages.sendSuccess(source,  spacer(), false);
                    sendNavigation(source, page, totalPages,
                            "/music view playlist " + playlistId + " page %d", true,
                            "/music view playlist " + playlistId + " page ");
                    Messages.sendSuccess(source,  spacer(), false);
                }));
    }

    private static void showUserPlaylists(CommandSourceStack source, String userId, int requestedPage, UserPlaylistView user, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, Component.translatable("musicplayer.view.user_playlists_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (user == null) {
            Messages.warning(source, Component.translatable("musicplayer.view.user_playlists_not_found"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  Component.translatable("musicplayer.view.user_prefix").withStyle(ChatFormatting.GOLD)
                .append(clickableText(user.name(), "/music view user " + user.id(), Component.translatable("musicplayer.common.view_user_playlists_hover"), ChatFormatting.AQUA)), false);
        if (user.signature() != null && !user.signature().isBlank()) {
            Messages.sendSuccess(source,  spacer(), false);
            Messages.sendSuccess(source,  Component.literal(user.signature()).withStyle(ChatFormatting.GRAY), false);
        }
        if (user.playlists().isEmpty()) {
            Messages.warning(source, Component.translatable("musicplayer.view.user_no_playlists"));
            return;
        }
        Messages.sendSuccess(source,  spacer(), false);
        PageWindow page = pageWindow(user.playlists().size(), requestedPage, pageSize());
        for (SearchEntry playlist : slicePage(user.playlists(), page)) {
            Messages.sendSuccess(source,  renderEntry(playlist, Messages.clickableCommand(Component.translatable("musicplayer.common.view_label"), Component.translatable("musicplayer.common.view_playlist_hover"), "/music view playlist " + playlist.id(), ChatFormatting.GREEN), Component.translatable("musicplayer.common.view_playlist_hover"), Component.empty()), false);
        }
        Messages.sendSuccess(source,  spacer(), false);
        sendNavigation(source, page.page(), page.totalPages(), "/music view user " + userId + " page %d", true, "/music view user " + userId + " page ");
        Messages.sendSuccess(source,  spacer(), false);
    }

    private static void showArtist(CommandSourceStack source, String artistId, int requestedPage, ArtistInfo artist, Throwable throwable, String literal) {
        if (throwable != null) {
            Messages.warning(source, Component.translatable("musicplayer.view.artist_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (artist == null) {
            Messages.warning(source, Component.translatable("musicplayer.view.artist_not_found"));
            return;
        }

        int pageSize = pageSize();
        int totalPages = Math.max(1, (int) Math.ceil((double) artist.songCount() / pageSize));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int offset = (page - 1) * pageSize;

        MinecraftServer server = source.getServer();
        MusicPlayerMod.netease().artistSongsPage(artistId, offset, pageSize)
                .whenComplete((tracks, t) -> server.execute(() -> {
                    sendHeader(source);
                    Messages.sendSuccess(source,  Component.translatable("musicplayer.view.artist_prefix").withStyle(ChatFormatting.GOLD)
                            .append(clickableText(artist.name(), "/music view " + literal + " " + artist.id(), Component.translatable("musicplayer.common.view_artist_hover"), ChatFormatting.AQUA)), false);
                    if (artist.description() != null && !artist.description().isBlank()) {
                        Messages.sendSuccess(source,  spacer(), false);
                        Messages.sendSuccess(source,  Component.literal(artist.description()).withStyle(ChatFormatting.GRAY), false);
                    }
                    if (t != null) {
                        Messages.warning(source, Component.translatable("musicplayer.view.songs_failed", Messages.textOrTranslatable(rootMessage(t))));
                        return;
                    }
                    if (tracks.isEmpty()) {
                        Messages.warning(source, Component.translatable("musicplayer.view.artist_no_songs"));
                        return;
                    }
                    Messages.sendSuccess(source,  spacer(), false);
                    for (SearchEntry track : tracks) {
                        Messages.sendSuccess(source,  renderEntry(track,
                                trackActions(source, track.id(), Component.translatable("musicplayer.common.request_label"), Component.translatable("musicplayer.play.request_hover"), ChatFormatting.GREEN),
                                Component.translatable("musicplayer.play.request_hover"), Component.empty()), false);
                    }
                    Messages.sendSuccess(source,  spacer(), false);
                    sendNavigation(source, page, totalPages, "/music view " + literal + " " + artistId + " page %d", true, "/music view " + literal + " " + artistId + " page ");
                    Messages.sendSuccess(source,  spacer(), false);
                }));
    }

    private static void showSong(CommandSourceStack source, TrackInfo track, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, Component.translatable("musicplayer.view.song_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (track == null || track.title() == null || track.title().isBlank()) {
            Messages.warning(source, Component.translatable("musicplayer.view.song_not_found"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.view.song_title"), null), false);
        Messages.sendSuccess(source,  spacer(), false);

        Messages.sendSuccess(source,  Component.translatable("musicplayer.view.song_prefix").withStyle(ChatFormatting.GOLD)
                .append(clickableText(track.title(), "/music play song " + track.id(), Component.translatable("musicplayer.common.tap_request_hover"), ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(clickableText(track.artist(),
                        track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(),
                        Component.translatable("musicplayer.common.click_artist_hover"), ChatFormatting.GRAY)), false);

        if (track.durationMillis() > 0L) {
            Messages.sendSuccess(source,  Component.translatable("musicplayer.view.duration_prefix").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(Messages.formatDuration(track.durationMillis())).withStyle(ChatFormatting.WHITE)), false);
        }

        if (track.coverUrl() != null && !track.coverUrl().isBlank()) {
            Messages.sendSuccess(source,  Component.translatable("musicplayer.view.cover_prefix").withStyle(ChatFormatting.GRAY)
                    .append(Messages.clickableUrl(Component.translatable("musicplayer.common.click_view_label"), Component.translatable("musicplayer.view.cover_hover"), track.coverUrl(), ChatFormatting.BLUE)), false);
        }

        Messages.sendSuccess(source,  Component.literal("ID: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(track.id()).withStyle(style -> style
                        .withClickEvent(new ClickEvent.CopyToClipboard(track.id()))
                        .withColor(ChatFormatting.WHITE)
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("musicplayer.common.copy_hover"))))), false);

        MutableComponent burnAction = buildBurnAction(source, track.id());
        if (burnAction != null) {
            Messages.sendSuccess(source,  spacer(), false);
            Messages.sendSuccess(source,  Component.translatable("musicplayer.common.actions_prefix").withStyle(ChatFormatting.GOLD)
                    .append(burnAction), false);
        }

        Messages.sendSuccess(source,  spacer(), false);
    }

    private static void sendRadioResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, Component.translatable("musicplayer.search.radio_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, Component.translatable("musicplayer.search.no_radios"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.search.radios_title"), Component.translatable("musicplayer.search.radios_hint")), false);
        Messages.sendSuccess(source,  spacer(), false);
        for (SearchEntry entry : results) {
            MutableComponent actions = Messages.clickableCommand(Component.translatable("musicplayer.radio.play_label"), Component.translatable("musicplayer.common.play_in_order_hover"), "/music play radio " + entry.id(), ChatFormatting.GREEN)
                    .append(Component.literal(" "))
                    .append(Messages.clickableCommand(Component.translatable("musicplayer.common.view_label"), Component.translatable("musicplayer.common.view_radio_hover"), "/music view radio " + entry.id(), ChatFormatting.GRAY));
            Messages.sendSuccess(source,  renderEntry(entry, actions, Component.translatable("musicplayer.radio.play_hover"), Component.translatable("musicplayer.radio.play_hover")), false);
        }
        Messages.sendSuccess(source,  spacer(), false);
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void showRadioDetail(CommandSourceStack source, String radioId, int requestedPage, RadioInfo radio, Throwable throwable) {
        if (throwable != null || radio == null) {
            Messages.warning(source, Component.translatable("musicplayer.radio.load_failed"));
            return;
        }

        int pageSize = pageSize();
        int totalPrograms = radio.programCount();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalPrograms / pageSize));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int offset = (page - 1) * pageSize;

        // Load programs for the current page
        MinecraftServer server = source.getServer();
        MusicPlayerMod.netease().radioPrograms(radioId, pageSize, offset, false)
                .whenComplete((pagePrograms, t) -> server.execute(() -> {
                    sendHeader(source);
                    sendQuickBar(source,
                            Messages.clickableCommand(Component.translatable("musicplayer.radio.play_all_label"), Component.translatable("musicplayer.common.play_in_order_hover"), "/music play radio " + radioId, ChatFormatting.GREEN),
                            Messages.clickableCommand(Component.translatable("musicplayer.radio.reverse_label"), Component.translatable("musicplayer.radio.reverse_hover"), "/music play radio " + radioId + " reverse", ChatFormatting.GOLD),
                            Messages.clickableCommand(Component.translatable("musicplayer.common.help_label"), Component.translatable("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));
                    MutableComponent headerLine = Component.literal("──").withStyle(ChatFormatting.GOLD)
                            .append(Component.translatable("musicplayer.radio.detail_title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    if (!radio.name().isBlank()) {
                        headerLine.append(Component.literal("  " + radio.name()).withStyle(ChatFormatting.GRAY));
                    }
                    Messages.sendSuccess(source, headerLine, false);
                    Messages.sendSuccess(source,  spacer(), false);

                    if (radio.playCount() > 0) {
                        Messages.sendSuccess(source,  Component.translatable("musicplayer.radio.play_count_prefix").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(radio.playCount())).withStyle(ChatFormatting.WHITE)), false);
                    }
                    if (radio.radioFeeType() != 0) {
                        Messages.sendSuccess(source,  Component.translatable("musicplayer.radio.paid_notice").withStyle(ChatFormatting.RED), false);
                    }
                    if (radio.description() != null && !radio.description().isBlank()) {
                        Messages.sendSuccess(source,  spacer(), false);
                        Messages.sendSuccess(source,  Component.literal(radio.description()).withStyle(ChatFormatting.GRAY), false);
                    }

                    if (t != null || pagePrograms == null || pagePrograms.isEmpty()) {
                        Messages.sendSuccess(source,  spacer(), false);
                        Messages.sendSuccess(source,  Component.translatable("musicplayer.radio.no_programs").withStyle(ChatFormatting.GRAY), false);
                        return;
                    }

                    Messages.sendSuccess(source,  spacer(), false);
                    Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.radio.programs_title", page, totalPages), Component.translatable("musicplayer.radio.programs_hint")), false);
                    for (ProgramInfo prog : pagePrograms) {
                        Messages.sendSuccess(source,  renderProgramEntry(source, prog), false);
                    }
                    Messages.sendSuccess(source,  spacer(), false);
                    if (totalPages > 1) {
                        String baseCmd = "/music view radio " + radioId;
                        sendNavigation(source, page, totalPages, baseCmd + " page %d", true, baseCmd + " page ");
                    }
                    Messages.sendSuccess(source,  spacer(), false);
                }));
    }

    private static void showProgramDetail(CommandSourceStack source, ProgramInfo program, Throwable throwable) {
        if (throwable != null || program == null) {
            Messages.warning(source, Component.translatable("musicplayer.view.program_failed"));
            return;
        }
        sendHeader(source);
        sendQuickBar(source,
                Messages.clickableCommand(Component.translatable("musicplayer.common.play_label"), Component.translatable("musicplayer.view.play_program_hover"), "/music play program " + program.id(), ChatFormatting.GREEN),
                program.radioId().isBlank() ? null : Messages.clickableCommand(Component.translatable("musicplayer.view.radio_label"), Component.translatable("musicplayer.view.owning_radio_hover"), "/music view radio " + program.radioId(), ChatFormatting.AQUA),
                Messages.clickableCommand(Component.translatable("musicplayer.common.help_label"), Component.translatable("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));
        Messages.sendSuccess(source,  sectionHeader(Component.translatable("musicplayer.view.program_title"), Component.literal(program.name())), false);
        Messages.sendSuccess(source,  spacer(), false);
        Messages.sendSuccess(source,  Component.translatable("musicplayer.view.program_prefix").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(program.name()).withStyle(ChatFormatting.WHITE)), false);
        if (!program.radioName().isBlank()) {
            Messages.sendSuccess(source,  Component.translatable("musicplayer.view.radio_prefix").withStyle(ChatFormatting.GRAY)
                    .append(clickableText(program.radioName(), "/music view radio " + program.radioId(), Component.translatable("musicplayer.common.view_radio_hover"), ChatFormatting.AQUA)), false);
        }
        if (program.durationMillis() > 0L) {
            Messages.sendSuccess(source,  Component.translatable("musicplayer.view.duration_prefix").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(Messages.formatDuration(program.durationMillis())).withStyle(ChatFormatting.WHITE)), false);
        }
        if (program.coverUrl() != null && !program.coverUrl().isBlank()) {
            Messages.sendSuccess(source,  Component.translatable("musicplayer.view.cover_prefix").withStyle(ChatFormatting.GRAY)
                    .append(Messages.clickableUrl(Component.translatable("musicplayer.common.click_view_label"), Component.translatable("musicplayer.view.cover_hover"), program.coverUrl(), ChatFormatting.BLUE)), false);
        }
        if (program.description() != null && !program.description().isBlank()) {
            Messages.sendSuccess(source,  spacer(), false);
            Messages.sendSuccess(source,  Component.literal(program.description()).withStyle(ChatFormatting.GRAY), false);
        }
        Messages.sendSuccess(source,  spacer(), false);
        Messages.sendSuccess(source,  Component.translatable("musicplayer.common.actions_prefix").withStyle(ChatFormatting.GOLD)
                .append(Messages.clickableCommand(Component.translatable("musicplayer.common.play_label"), Component.translatable("musicplayer.view.play_program_hover"), "/music play program " + program.id(), ChatFormatting.GREEN)), false);
        Messages.sendSuccess(source,  spacer(), false);
    }

    private static MutableComponent renderProgramEntry(CommandSourceStack source, ProgramInfo program) {
        MutableComponent line = Messages.clickableCommand(Component.translatable("musicplayer.common.play_label"), Component.translatable("musicplayer.view.play_now_hover"), "/music play program " + program.id(), ChatFormatting.GREEN);
        line.append(Component.literal(" "));
        line.append(Messages.clickableCommand(Component.literal(program.name()), Component.translatable("musicplayer.view.play_now_hover"), "/music play program " + program.id(), ChatFormatting.AQUA));
        if (program.durationMillis() > 0L) {
            line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal(Messages.formatDuration(program.durationMillis())).withStyle(ChatFormatting.GRAY));
        }
        line.append(Component.literal(" "));
        line.append(Messages.clickableCommand(Component.translatable("musicplayer.view.detail_label"), Component.translatable("musicplayer.view.program_detail_hover"), "/music view program " + program.id(), ChatFormatting.GRAY));
        return line;
    }

    private static MutableComponent renderRandomTrack(CommandSourceStack source, TrackInfo track) {
        MutableComponent line = trackActions(source, track.id(), Component.translatable("musicplayer.common.request_label"), Component.translatable("musicplayer.random.request_hover"), ChatFormatting.GREEN);
        line.append(Component.literal(" "));
        line.append(clickableText(track.title(), "/music play song " + track.id(), Component.translatable("musicplayer.random.request_hover"), ChatFormatting.AQUA));
        line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(clickableText(track.artist(),
                track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(),
                Component.translatable("musicplayer.common.view_artist_hover"),
                ChatFormatting.GRAY));
        if (track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
            line.append(Component.literal(" "));
            line.append(Messages.clickableUrl(Component.translatable("musicplayer.common.download_label"), Component.translatable("musicplayer.common.download_hover"), track.sourceUrls().getFirst(), ChatFormatting.BLUE));
        }
        return line;
    }

    private static MutableComponent renderCurrentTrack(CommandSourceStack source, TrackInfo track, String elapsed, String duration, String requesterName) {
        MutableComponent line = Component.translatable("musicplayer.now.playing_prefix").withStyle(ChatFormatting.GOLD)
                .append(clickableText(track.title(), "/music play song " + track.id(), Component.translatable("musicplayer.common.rerequest_hover"), ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(clickableText(track.artist(), track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(), Component.translatable("musicplayer.common.click_artist_hover"), ChatFormatting.GRAY));
        MutableComponent burnAction = buildBurnAction(source, track.id());
        if (burnAction != null) {
            line.append(Component.literal(" "));
            line.append(burnAction);
        }
        if (!track.sourceUrls().isEmpty()) {
            line.append(Component.literal(" "));
            line.append(Messages.clickableUrl(Component.translatable("musicplayer.now.open_url_label"), Component.translatable("musicplayer.now.open_url_hover"), track.sourceUrls().getFirst(), ChatFormatting.GREEN));
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
            line.append(Component.translatable("musicplayer.progress.requester_prefix").withStyle(ChatFormatting.GRAY));
            line.append(Component.literal(requesterName).withStyle(ChatFormatting.AQUA));
        }
        return line;
    }

    private static MutableComponent trackActions(CommandSourceStack source, String songId, Component primaryLabel, Component primaryHover, ChatFormatting primaryColor) {
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
        return Messages.clickableCommand(Component.translatable("musicplayer.burn.action_label"), Component.translatable("musicplayer.burn.action_hover"), "/music burn song " + songId, ChatFormatting.LIGHT_PURPLE);
    }

    private static MutableComponent renderEntry(SearchEntry entry, MutableComponent action, Component titleHover, Component subtitleHover) {
        MutableComponent line = action.copy();
        line.append(Component.literal(" "));
        line.append(clickableText(entry.title(), entry.titleCommand(), titleHover, ChatFormatting.AQUA));
        if (entry.hasSubtitle()) {
            line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(clickableText(entry.subtitle(), entry.subtitleCommand(), subtitleHover, ChatFormatting.GRAY));
        }
        return line;
    }

    private static MutableComponent clickableText(String text, String command, Component hover, ChatFormatting color) {
        return clickableText(Component.literal(text), command, hover, color);
    }

    private static MutableComponent clickableText(Component text, String command, Component hover, ChatFormatting color) {
        return command != null && !command.isBlank()
                ? Messages.clickableCommand(text, hover == null || hover.getString().isBlank() ? text : hover, command, color)
                : text.copy().withStyle(color);
    }

    private static void sendSearchNavigation(CommandSourceStack source, String type, String keyword, int page, int resultSize) {
        boolean hasNext = resultSize >= pageSize();
        String base = "/music search " + type + " \"" + keyword + "\"";
        sendNavigation(source, page, page + (hasNext ? 1 : 0), base + " page %d", hasNext, base + " page ");
        Messages.sendSuccess(source,  spacer(), false);
    }

    private static void sendNavigation(CommandSourceStack source, int page, int totalPages, String commandPattern, boolean hasKnownNext, String suggestCommand) {
        MutableComponent nav = Component.literal("");
        if (page > 1) {
            nav.append(Messages.clickableCommand(Component.translatable("musicplayer.common.prev_label"), Component.translatable("musicplayer.common.prev_hover"), String.format(commandPattern, page - 1), ChatFormatting.YELLOW));
            nav.append(Component.literal(" "));
        }
        nav.append((hasKnownNext
                ? Component.translatable("musicplayer.common.page_indicator", page, Math.max(page, totalPages))
                : Component.translatable("musicplayer.common.page_indicator_single", page)).withStyle(ChatFormatting.DARK_GRAY));
        if (hasKnownNext && page < totalPages) {
            nav.append(Component.literal(" "));
            nav.append(Messages.clickableCommand(Component.translatable("musicplayer.common.next_label"), Component.translatable("musicplayer.common.next_hover"), String.format(commandPattern, page + 1), ChatFormatting.YELLOW));
        }
        nav.append(Component.literal(" "));
        nav.append(Messages.suggestable(Component.translatable("musicplayer.common.jump_label"), Component.translatable("musicplayer.common.jump_hover"), suggestCommand, ChatFormatting.GRAY));
        Messages.sendSuccess(source,  nav, false);
    }

    private static void burnHeldDisc(CommandSourceStack source, ServerPlayer player, TrackInfo track) {
        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!MusicDiscHelper.isBurnableDisc(mainHand)) {
            Messages.warning(source, Component.translatable("musicplayer.burn.disc_changed"));
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

        Messages.sendSuccess(source,  Component.translatable("musicplayer.burn.done").withStyle(ChatFormatting.GREEN)
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
            Messages.sendSuccess(source,  line, false);
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
        Messages.sendSuccess(source,  spacer(), false);
        Messages.sendSuccess(source,  spacer(), false);
        Messages.sendSuccess(source,  renderHeader(), false);
        Messages.sendSuccess(source,  spacer(), false);
    }

    private static MutableComponent sectionHeader(Component title, Component subtitle) {
        MutableComponent line = Component.literal("◆ ").withStyle(ChatFormatting.GOLD)
                .append(title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (subtitle != null && !subtitle.getString().isBlank()) {
            line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(subtitle.copy().withStyle(ChatFormatting.DARK_GRAY));
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

    private static void loading(CommandSourceStack source, Component text) {
        if (MusicPlayerConfigManager.get().showLoadingHints) {
            Messages.loading(source, text);
        }
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(value ? "musicplayer.common.on" : "musicplayer.common.off");
    }

    private static String rootMessage(Throwable throwable) {
        if (throwable == null) return "musicplayer.common.unknown_error";
        Throwable current = throwable;
        int depth = 0;
        while (current.getCause() != null && depth < 100) {
            current = current.getCause();
            depth++;
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
