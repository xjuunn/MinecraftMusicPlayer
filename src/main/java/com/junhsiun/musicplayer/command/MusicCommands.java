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
import com.junhsiun.musicplayer.platform.api.MusicApiMode;
import com.junhsiun.musicplayer.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean dedicated) {
        dispatcher.register(Commands.literal("music")
                .executes(commandContext -> sendHelpOverview(commandContext.getSource()))
                .then(help())
                .then(now())
                .then(seek())
                .then(pause())
                .then(resume())
                .then(play())
                .then(skip())
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
                .then(share())
                .then(radio())
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
        MutableComponent title = sectionHeader(new TranslatableComponent("musicplayer.help.title"), null);
        title.append(new TextComponent("  ").withStyle(ChatFormatting.DARK_GRAY));
        title.append(Messages.clickableCommand(new TextComponent("Junhsiun"), new TranslatableComponent("musicplayer.help.author_hover"), "/music view user " + AUTHOR_USER_ID, ChatFormatting.AQUA));
        title.append(new TextComponent("  ").withStyle(ChatFormatting.DARK_GRAY));
        title.append(new TranslatableComponent("musicplayer.help.repository")
                .withStyle(style -> style
                        .withColor(ChatFormatting.WHITE)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/music help star"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, renderRepositoryHover()))));
        Messages.sendSuccess(source, title);
        helpEntry(source, "now", new TranslatableComponent("musicplayer.help.desc.now"));
        helpEntry(source, "play", new TranslatableComponent("musicplayer.help.desc.play"));
        helpEntry(source, "skip", new TranslatableComponent("musicplayer.help.desc.skip"));
        helpEntry(source, "pause", new TranslatableComponent("musicplayer.help.desc.pause"));
        helpEntry(source, "queue", new TranslatableComponent("musicplayer.help.desc.queue"));
        helpEntry(source, "playlist", new TranslatableComponent("musicplayer.help.desc.playlist"));
        helpEntry(source, "search", new TranslatableComponent("musicplayer.help.desc.search"));
        helpEntry(source, "view", new TranslatableComponent("musicplayer.help.desc.view"));
        helpEntry(source, "share", new TranslatableComponent("musicplayer.help.desc.share"));
        helpEntry(source, "join", new TranslatableComponent("musicplayer.help.desc.join"));
        helpEntry(source, "leave", new TranslatableComponent("musicplayer.help.desc.leave"));
        helpEntry(source, "mute", new TranslatableComponent("musicplayer.help.desc.mute"));
        helpEntry(source, "burn", new TranslatableComponent("musicplayer.help.desc.burn"));
        helpEntry(source, "random", new TranslatableComponent("musicplayer.help.desc.random"));
        helpEntry(source, "lyrics", new TranslatableComponent("musicplayer.help.desc.lyrics"));
        helpEntry(source, "radio", new TranslatableComponent("musicplayer.help.desc.radio"));
        helpEntry(source, "help", new TranslatableComponent("musicplayer.help.desc.help"));
        helpEntry(source, "config", new TranslatableComponent("musicplayer.help.desc.config"));
        Messages.sendSuccess(source, spacer());
        Messages.sendSuccess(source, new TranslatableComponent("musicplayer.help.tip").withStyle(ChatFormatting.DARK_GRAY));
        Messages.sendSuccess(source, spacer());
        return 1;
    }

    private static int sendStarAchievement(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        grantStarAchievement(source.getServer(), player);
        if (ServerPlayNetworking.canSend(player, OpenUrlPayload.ID)) {
            OpenUrlPayload.send(player, new OpenUrlPayload(REPOSITORY_URL));
        }
        return 1;
    }

    private static void grantStarAchievement(MinecraftServer server, ServerPlayer player) {
        ResourceLocation advancementId = new ResourceLocation(MusicPlayerMod.MOD_ID, "star");
        Advancement holder = server.getAdvancements().getAdvancement(advancementId);
        if (holder == null) {
            MusicPlayerMod.LOGGER.warn("未找到成就定义: {}", advancementId);
            return;
        }
        player.getAdvancements().award(holder, "star");
    }

    private static Component renderRepositoryHover() {
        return new TextComponent("✦ ").withStyle(ChatFormatting.YELLOW)
                .append(new TranslatableComponent("musicplayer.help.star_unlocked").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TranslatableComponent("musicplayer.help.star_call").withStyle(ChatFormatting.GOLD))
                .append(new TextComponent("\n").withStyle(ChatFormatting.DARK_GRAY))
                .append(new TranslatableComponent("musicplayer.help.star_open").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void helpEntry(CommandSourceStack source, String command, Component description) {
        MutableComponent line = Messages.clickableCommand(new TextComponent(command), new TranslatableComponent("musicplayer.help.view_usage", command), "/music help " + command, ChatFormatting.GOLD);
        line.append(new TextComponent("  ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(description.copy().withStyle(ChatFormatting.GRAY));
        Messages.sendSuccess(source, line);
    }

    private static int sendHelpFor(CommandSourceStack source, String subcommand) {
        if ("star".equals(subcommand)) {
            // 仓库入口彩蛋：点击 /music help 中的仓库链接触发（不作为可见子命令注册）
            sendStarAchievement(source);
            return 1;
        }
        sendHeader(source);
        switch (subcommand) {
            case "now" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("now"), null));
                detailLine(source, "/music now", new TranslatableComponent("musicplayer.help.usage.now"));
            }
            case "queue" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("queue"), null));
                detailLine(source, "/music queue", new TranslatableComponent("musicplayer.help.usage.queue"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.queue_promote"), new TranslatableComponent("musicplayer.help.usage.queue_promote"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.queue_remove"), new TranslatableComponent("musicplayer.help.usage.queue_remove"));
                detailLine(source, "/music queue clear", new TranslatableComponent("musicplayer.help.usage.queue_clear"));
            }
            case "play" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("play"), null));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.play_song"), new TranslatableComponent("musicplayer.help.usage.play_song"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.play_playlist"), new TranslatableComponent("musicplayer.help.usage.play_playlist"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.play_playlist_reverse"), new TranslatableComponent("musicplayer.help.usage.play_playlist_reverse"));
            }
            case "playlist" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("playlist"), null));
                detailLine(source, "/music playlist", new TranslatableComponent("musicplayer.help.usage.playlist"));
                detailLine(source, "/music playlist list", new TranslatableComponent("musicplayer.help.usage.playlist_list"));
                detailLine(source, "/music playlist stop", new TranslatableComponent("musicplayer.help.usage.playlist_stop"));
                detailLine(source, "/music playlist order", new TranslatableComponent("musicplayer.help.usage.playlist_order"));
            }
            case "skip" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("skip"), null));
                detailLine(source, "/music skip", new TranslatableComponent("musicplayer.help.usage.skip"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.skip_requester"), new TranslatableComponent("musicplayer.help.usage.skip_no_vote"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.skip_admin"), new TranslatableComponent("musicplayer.help.usage.skip_no_vote"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.skip_vote"), new TranslatableComponent("musicplayer.help.usage.skip_vote"));
            }
            case "search" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("search"), null));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.search_song"), new TranslatableComponent("musicplayer.help.usage.search_song"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.search_artist"), new TranslatableComponent("musicplayer.help.usage.search_artist"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.search_playlist"), new TranslatableComponent("musicplayer.help.usage.search_playlist"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.search_user"), new TranslatableComponent("musicplayer.help.usage.search_user"));
            }
            case "view" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("view"), null));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.view_playlist"), new TranslatableComponent("musicplayer.help.usage.view_playlist"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.view_artist"), new TranslatableComponent("musicplayer.help.usage.view_artist"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.view_user"), new TranslatableComponent("musicplayer.help.usage.view_user"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.view_url"), new TranslatableComponent("musicplayer.help.usage.view_url"));
            }
            case "share" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("share"), null));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.share"), new TranslatableComponent("musicplayer.help.usage.share"));
            }
            case "join" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("join"), null));
                detailLine(source, "/music join", new TranslatableComponent("musicplayer.help.usage.join"));
            }
            case "leave" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("leave"), null));
                detailLine(source, "/music leave", new TranslatableComponent("musicplayer.help.usage.leave"));
            }
            case "mute" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("mute"), null));
                detailLine(source, "/music mute once", new TranslatableComponent("musicplayer.help.usage.mute_once"));
            }
            case "burn" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("burn"), null));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.burn_song"), new TranslatableComponent("musicplayer.help.usage.burn_song"));
            }
            case "random" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("random"), null));
                detailLine(source, "/music random", new TranslatableComponent("musicplayer.help.usage.random"));
            }
            case "help" -> {
                return sendHelpOverview(source);
            }
            case "lyrics" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("lyrics"), null));
                detailLine(source, "/music lyrics", new TranslatableComponent("musicplayer.help.usage.lyrics"));
                detailLine(source, "/music lyrics on", new TranslatableComponent("musicplayer.help.usage.lyrics_on"));
                detailLine(source, "/music lyrics off", new TranslatableComponent("musicplayer.help.usage.lyrics_off"));
                detailLine(source, "/music lyrics status", new TranslatableComponent("musicplayer.help.usage.lyrics_status"));
            }
            case "pause" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("pause"), null));
                detailLine(source, "/music pause", new TranslatableComponent("musicplayer.help.usage.pause"));
                detailLine(source, "/music resume", new TranslatableComponent("musicplayer.help.usage.resume"));
            }
            case "config" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("config"), null));
                detailLine(source, "/music config reload", new TranslatableComponent("musicplayer.help.usage.config_reload"));
                detailLine(source, "/music config status", new TranslatableComponent("musicplayer.help.usage.config_status"));
                detailLine(source, "/music config clearqueue", new TranslatableComponent("musicplayer.help.usage.config_clearqueue"));
            detailLine(source, "/music config reset <bgmPlaylist|all>", new TranslatableComponent("musicplayer.help.usage.config_reset"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.config_set"), new TranslatableComponent("musicplayer.help.usage.config_set"));
            }
            case "radio" -> {
                Messages.sendSuccess(source,  sectionHeader(new TextComponent("radio"), null));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.radio_hot"), new TranslatableComponent("musicplayer.help.usage.radio_hot"));
                detailLine(source, "/music radio categories", new TranslatableComponent("musicplayer.help.usage.radio_categories"));
                detailLine(source, "/music view radio <id>", new TranslatableComponent("musicplayer.help.usage.view_radio"));
                detailLine(source, "/music view program <id>", new TranslatableComponent("musicplayer.help.usage.view_program"));
                detailLine(source, new TranslatableComponent("musicplayer.help.cmd.search_radio"), new TranslatableComponent("musicplayer.help.usage.search_radio"));
                detailLine(source, "/music play program <id>", new TranslatableComponent("musicplayer.help.usage.play_program"));
                detailLine(source, "/music play radio <id>", new TranslatableComponent("musicplayer.help.usage.play_radio"));
                detailLine(source, "/music play radio <id> reverse", new TranslatableComponent("musicplayer.help.usage.play_radio_reverse"));
            }
            default -> {
                Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.help.unknown_subcommand", subcommand).withStyle(ChatFormatting.RED));
                Messages.sendSuccess(source,  spacer());
                return sendHelpOverview(source);
            }
        }
        Messages.sendSuccess(source,  spacer());
        return 1;
    }

    private static void detailLine(CommandSourceStack source, String command, Component description) {
        detailLine(source, new TextComponent(command), description);
    }

    private static void detailLine(CommandSourceStack source, Component command, Component description) {
        MutableComponent line = command.copy().withStyle(ChatFormatting.GOLD);
        line.append(new TextComponent("  ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(description.copy().withStyle(ChatFormatting.GRAY));
        Messages.sendSuccess(source,  line);
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> now() {
        return Commands.literal("now").executes(context -> {
            TrackInfo track = MusicPlayerMod.queueService().currentTrack();
            if (track == null) {
                sendHeader(context.getSource());
                context.getSource().sendSuccess(MusicPlayerMod.queueService().describeNowPlaying(), false);
                return 1;
            }
        sendHeader(context.getSource());
        boolean isAdmin = context.getSource().hasPermission(2);
        boolean isPaused = MusicPlayerMod.queueService().isPaused();
        sendQuickBar(context.getSource(),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.now.rewind_label"), new TranslatableComponent("musicplayer.now.rewind_hover"), "/music seek -5", ChatFormatting.GRAY),
                isPaused
                        ? (isAdmin ? Messages.clickableCommand(new TranslatableComponent("musicplayer.now.resume_label"), new TranslatableComponent("musicplayer.now.resume_hover"), "/music resume", ChatFormatting.GREEN) : null)
                        : (isAdmin ? Messages.clickableCommand(new TranslatableComponent("musicplayer.now.pause_label"), new TranslatableComponent("musicplayer.now.pause_hover"), "/music pause", ChatFormatting.YELLOW) : null),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.now.forward_label"), new TranslatableComponent("musicplayer.now.forward_hover"), "/music seek 5", ChatFormatting.GRAY),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.common.skip_label"), new TranslatableComponent("musicplayer.common.skip_hover"), "/music skip", ChatFormatting.YELLOW),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.common.queue_label"), new TranslatableComponent("musicplayer.common.queue_hover"), "/music queue", ChatFormatting.GRAY),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.common.playlist_label"), new TranslatableComponent("musicplayer.now.playlist_hover"), "/music playlist", ChatFormatting.AQUA),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.common.help_label"), new TranslatableComponent("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));
        context.getSource().sendSuccess(spacer(), false);
        long elapsedMs = MusicPlayerMod.queueService().playbackElapsedMillis();
        long durationMs = MusicPlayerMod.queueService().playbackDurationMillis();
        String requesterName = MusicPlayerMod.queueService().currentRequesterName();
        String elapsed = Messages.formatDuration(elapsedMs);
        String duration = durationMs > 0L ? Messages.formatDuration(durationMs) : "";
        context.getSource().sendSuccess(renderCurrentTrack(context.getSource(), track, elapsed, duration, requesterName), false);
        context.getSource().sendSuccess(renderProgressLine(elapsed, duration, requesterName, isPaused, durationMs > 0L ? MusicPlayerMod.queueService().playbackElapsedMillis() : 0L, durationMs), false);
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
                        player.displayClientMessage(new TextComponent(icon + "  ").withStyle(ChatFormatting.GRAY)
                                .append(new TranslatableComponent("musicplayer.seek.seconds", Math.abs(delta)).withStyle(ChatFormatting.GRAY)), false);
                    }
                    return 1;
                }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> pause() {
        return Commands.literal("pause")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (MusicPlayerMod.queueService().currentTrack() == null) {
                        Messages.warning(source, new TranslatableComponent("musicplayer.queue.nothing_playing"));
                        return 0;
                    }
                    MusicPlayerMod.queueService().pause(source.getServer());
                    broadcastState(source,
                            controlEcho("musicplayer.control.paused",
                                    "musicplayer.now.resume_label", "musicplayer.now.resume_hover",
                                    "/music resume", ChatFormatting.GREEN),
                            null);
                    return 1;
                });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> resume() {
        return Commands.literal("resume")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (MusicPlayerMod.queueService().currentTrack() == null) {
                        Messages.warning(source, new TranslatableComponent("musicplayer.queue.nothing_playing"));
                        return 0;
                    }
                    MusicPlayerMod.queueService().resume(source.getServer());
                    broadcastState(source,
                            controlEcho("musicplayer.control.resumed",
                                    "musicplayer.now.pause_label", "musicplayer.now.pause_hover",
                                    "/music pause", ChatFormatting.YELLOW),
                            null);
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
                                Messages.warning(context.getSource(), new TranslatableComponent("musicplayer.queue.promote_not_found"));
                                return 0;
                            }
                            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.queue.promoted"));
                            return showQueue(context.getSource(), 1);
                        })))
                .then(Commands.literal("remove")
                        .then(Commands.argument("song_id", StringArgumentType.string()).executes(context -> {
                            String songId = StringArgumentType.getString(context, "song_id");
                            if (!MusicPlayerMod.queueService().removeFromQueue(songId)) {
                                Messages.warning(context.getSource(), new TranslatableComponent("musicplayer.queue.not_found"));
                                return 0;
                            }
                            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.queue.removed"));
                            return showQueue(context.getSource(), 1);
                        })))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
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
                Messages.clickableCommand(new TranslatableComponent("musicplayer.queue.now_label"), new TranslatableComponent("musicplayer.queue.now_hover"), "/music now", ChatFormatting.AQUA),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.queue.refresh_label"), new TranslatableComponent("musicplayer.queue.refresh_hover"), "/music queue " + page.page(), ChatFormatting.YELLOW),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.common.skip_label"), new TranslatableComponent("musicplayer.common.skip_hover"), "/music skip", ChatFormatting.GRAY),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.common.help_label"), new TranslatableComponent("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));
        if (currentTrack == null) {
            Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.queue.nothing_playing").withStyle(ChatFormatting.GRAY));
        } else {
            long elapsedMs = MusicPlayerMod.queueService().playbackElapsedMillis();
            long durationMs = MusicPlayerMod.queueService().playbackDurationMillis();
            String requesterName = MusicPlayerMod.queueService().currentRequesterName();
            String elapsed = Messages.formatDuration(elapsedMs);
            String duration = durationMs > 0L ? Messages.formatDuration(durationMs) : "";
            Messages.sendSuccess(source,  renderCurrentTrack(source, currentTrack, elapsed, duration, requesterName));
            Messages.sendSuccess(source,  renderProgressLine(elapsed, duration, requesterName, MusicPlayerMod.queueService().isPaused(), elapsedMs, durationMs));
        }
        if (totalEntries == 0) {
            Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.queue.empty").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        Messages.sendSuccess(source,  spacer());
        Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.queue.title"), new TranslatableComponent("musicplayer.queue.hint")));
        List<SearchEntry> entries = MusicPlayerMod.queueService().queuedEntries(page.page(), page.pageSize());
        for (int index = 0; index < entries.size(); index++) {
            SearchEntry entry = entries.get(index);
            int order = (page.page() - 1) * page.pageSize() + index + 1;
            MutableComponent line = new TextComponent(order + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Messages.clickableCommand(new TranslatableComponent("musicplayer.queue.next_label"), new TranslatableComponent("musicplayer.queue.next_hover"), "/music queue promote " + entry.id(), ChatFormatting.GREEN))
                    .append(new TextComponent(" "))
                    .append(clickableText(entry.title(), entry.titleCommand(), new TranslatableComponent("musicplayer.queue.replay_hover"), ChatFormatting.AQUA));
            if (entry.hasSubtitle()) {
                line.append(new TextComponent(" - ").withStyle(ChatFormatting.DARK_GRAY));
                line.append(clickableText(entry.subtitle(), entry.subtitleCommand(), new TranslatableComponent("musicplayer.common.view_artist_hover"), ChatFormatting.GRAY));
            }
            Messages.sendSuccess(source,  line);
        }
        Messages.sendSuccess(source,  spacer());
        sendNavigation(source, page.page(), page.totalPages(), "/music queue %d", true, "/music queue ");
        Messages.sendSuccess(source,  spacer());
        return 1;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> playlist() {
        return Commands.literal("playlist")
                .executes(context -> showPlaylistStatus(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> showPlaylistTracks(context.getSource())))
                .then(Commands.literal("stop")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            var queueService = MusicPlayerMod.queueService();
                            String playlistId = queueService.currentPlaylistId();
                            if (!queueService.stopPlaylist(source.getServer())) {
                                Messages.warning(source, new TranslatableComponent("musicplayer.playlist.none"));
                                return 0;
                            }
                            MutableComponent echo = new TextComponent("◆ ").withStyle(ChatFormatting.GOLD)
                                    .append(new TranslatableComponent("musicplayer.control.playlist_stopped").withStyle(ChatFormatting.GOLD));
                            if (!playlistId.isBlank()) {
                                echo.append(new TextComponent("  "))
                                        .append(Messages.suggestable(
                                                new TranslatableComponent("musicplayer.control.replay_label"),
                                                new TranslatableComponent("musicplayer.control.replay_hover"),
                                                "/music play playlist " + playlistId,
                                                ChatFormatting.GREEN));
                            }
                            broadcastState(source, echo,
                                    new TranslatableComponent("musicplayer.playlist.stopped").withStyle(ChatFormatting.YELLOW));
                            return 1;
                        }))
                .then(Commands.literal("order")
                        .executes(context -> {
                            PlayOrder current = MusicPlayerMod.queueService().playOrder();
                            Messages.info(context.getSource(), new TranslatableComponent("musicplayer.playlist.current_order", new TranslatableComponent(current.translationKey())));
                            return 1;
                        })
                        .then(Commands.literal("sequential").executes(context -> {
                            MusicPlayerMod.queueService().setPlayOrder(PlayOrder.SEQUENTIAL);
                            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.playlist.order_changed", new TranslatableComponent("musicplayer.play_order.sequential")));
                            return 1;
                        }))
                        .then(Commands.literal("reverse").executes(context -> {
                            MusicPlayerMod.queueService().setPlayOrder(PlayOrder.REVERSE);
                            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.playlist.order_changed", new TranslatableComponent("musicplayer.play_order.reverse")));
                            return 1;
                        }))
                        .then(Commands.literal("shuffle").executes(context -> {
                            MusicPlayerMod.queueService().setPlayOrder(PlayOrder.SHUFFLE);
                            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.playlist.order_changed", new TranslatableComponent("musicplayer.play_order.shuffle")));
                            return 1;
                        })));
    }

    private static int showPlaylistStatus(CommandSourceStack source) {
        sendHeader(source);
        boolean isPlaylistMode = MusicPlayerMod.queueService().isPlaylistMode();
        int queueSize = MusicPlayerMod.queueService().queuedCount();
        int remaining = MusicPlayerMod.queueService().playlistRemainingCount();

        sendQuickBar(source,
                Messages.clickableCommand(new TranslatableComponent("musicplayer.common.queue_label"), new TranslatableComponent("musicplayer.common.queue_hover"), "/music queue", ChatFormatting.YELLOW),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.playlist.loaded_label"), new TranslatableComponent("musicplayer.playlist.loaded_hover"), "/music playlist list", ChatFormatting.AQUA),
                isPlaylistMode ? Messages.clickableCommand(new TranslatableComponent("musicplayer.playlist.stop_label"), new TranslatableComponent("musicplayer.playlist.stop_hover"), "/music playlist stop", ChatFormatting.DARK_GRAY) : null,
                Messages.clickableCommand(new TranslatableComponent("musicplayer.common.help_label"), new TranslatableComponent("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));

        TrackInfo currentTrack = MusicPlayerMod.queueService().currentTrack();
        if (currentTrack != null) {
            Messages.sendSuccess(source,  spacer());
            long elapsedMs = MusicPlayerMod.queueService().playbackElapsedMillis();
            long durationMs = MusicPlayerMod.queueService().playbackDurationMillis();
            String requesterName = MusicPlayerMod.queueService().currentRequesterName();
            String elapsed = Messages.formatDuration(elapsedMs);
            String duration = durationMs > 0L ? Messages.formatDuration(durationMs) : "";
            Messages.sendSuccess(source,  renderCurrentTrack(source, currentTrack, elapsed, duration, requesterName));
            Messages.sendSuccess(source,  renderProgressLine(elapsed, duration, requesterName, MusicPlayerMod.queueService().isPaused(), elapsedMs, durationMs));
        }

        Messages.sendSuccess(source,  spacer());
        MutableComponent statusLine = new TranslatableComponent("musicplayer.playlist.queue_status",
                new TextComponent(String.valueOf(queueSize)).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GOLD);
        if (isPlaylistMode) {
            statusLine.append(new TextComponent("  ·  ").withStyle(ChatFormatting.GOLD))
                    .append(new TranslatableComponent("musicplayer.playlist.playlist_queue_status",
                            new TextComponent(String.valueOf(remaining)).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GOLD))
                    .append(new TextComponent("  ·  ").withStyle(ChatFormatting.GOLD))
                    .append(new TranslatableComponent("musicplayer.playlist.direction_prefix").withStyle(ChatFormatting.GOLD))
                    .append(new TranslatableComponent(MusicPlayerMod.queueService().isPlaylistReversed() ? "musicplayer.play_order.reverse" : "musicplayer.play_order.sequential").withStyle(ChatFormatting.AQUA));
        }
        statusLine.append(new TextComponent("  ·  ").withStyle(ChatFormatting.GOLD))
                .append(new TranslatableComponent("musicplayer.playlist.order_prefix").withStyle(ChatFormatting.GOLD))
                .append(Messages.clickableCommand(
                        new TranslatableComponent(MusicPlayerMod.queueService().playOrder().translationKey()),
                        new TranslatableComponent("musicplayer.playlist.order_hover"),
                        "/music playlist order",
                        ChatFormatting.AQUA));
        Messages.sendSuccess(source,  statusLine);
        Messages.sendSuccess(source,  spacer());
        return 1;
    }

    private static int showPlaylistTracks(CommandSourceStack source) {
        sendHeader(source);
        if (!MusicPlayerMod.queueService().isPlaylistMode()) {
            Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.playlist.none").withStyle(ChatFormatting.GRAY));
            Messages.sendSuccess(source,  spacer());
            return 1;
        }

        List<SearchEntry> entries = MusicPlayerMod.queueService().playlistEntries();
        int totalEntries = entries.size();
        int remaining = MusicPlayerMod.queueService().playlistRemainingCount();

        sendQuickBar(source,
                Messages.clickableCommand(new TranslatableComponent("musicplayer.playlist.status_label"), new TranslatableComponent("musicplayer.playlist.status_hover"), "/music playlist", ChatFormatting.AQUA),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.playlist.stop_label"), new TranslatableComponent("musicplayer.playlist.stop_hover"), "/music playlist stop", ChatFormatting.DARK_GRAY),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.common.help_label"), new TranslatableComponent("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));

        Messages.sendSuccess(source,  spacer());
        Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.playlist.list_status", totalEntries, remaining).withStyle(ChatFormatting.DARK_GRAY));
        Messages.sendSuccess(source,  spacer());

        for (int index = 0; index < entries.size(); index++) {
            SearchEntry entry = entries.get(index);
            MutableComponent line = new TextComponent((index + 1) + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Messages.clickableCommand(new TranslatableComponent("musicplayer.common.request_label"), new TranslatableComponent("musicplayer.common.rerequest_hover"), "/music play song " + entry.id(), ChatFormatting.GREEN))
                    .append(new TextComponent(" "))
                    .append(clickableText(entry.title(), entry.titleCommand(), new TranslatableComponent("musicplayer.common.open_browser_hover"), ChatFormatting.AQUA));
            if (entry.hasSubtitle()) {
                line.append(new TextComponent(" - ").withStyle(ChatFormatting.DARK_GRAY));
                line.append(clickableText(entry.subtitle(), entry.subtitleCommand(), new TranslatableComponent("musicplayer.common.view_artist_hover"), ChatFormatting.GRAY));
            }
            Messages.sendSuccess(source,  line);
        }
        Messages.sendSuccess(source,  spacer());
        return 1;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> lyrics() {
        return Commands.literal("lyrics")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    boolean now = MusicPlayerMod.queueService().toggleLyrics(player);
                    Messages.success(context.getSource(), new TranslatableComponent(now ? "musicplayer.lyrics.enabled" : "musicplayer.lyrics.disabled"));
                    return 1;
                })
                .then(Commands.literal("on").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MusicPlayerMod.queueService().toggleLyrics(player, true);
                    Messages.success(context.getSource(), new TranslatableComponent("musicplayer.lyrics.enabled"));
                    return 1;
                }))
                .then(Commands.literal("off").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MusicPlayerMod.queueService().toggleLyrics(player, false);
                    Messages.success(context.getSource(), new TranslatableComponent("musicplayer.lyrics.disabled"));
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    boolean on = MusicPlayerMod.queueService().isLyricsEnabled(player);
                    Messages.info(context.getSource(), new TranslatableComponent("musicplayer.lyrics.status", yesNo(on)));
                    return 1;
                }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> join() {
        return Commands.literal("join").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            MusicPlayerMod.queueService().joinPlayer(player);
            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.join.done"));
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> leave() {
        return Commands.literal("leave").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            MusicPlayerMod.queueService().leavePlayer(player);
            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.leave.done"));
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> muteOnce() {
        return Commands.literal("mute")
                .then(Commands.literal("once").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MusicPlayerMod.queueService().mutePlayerOnce(player);
                    Messages.success(context.getSource(), new TranslatableComponent("musicplayer.mute.done"));
                    return 1;
                }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> skip() {
        return Commands.literal("skip")
                .requires(source -> source.getEntity() instanceof ServerPlayer || source.hasPermission(2))
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer();
                    if (context.getSource().hasPermission(2)) {
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
                            loading(context.getSource(), MusicPlayerMod.queueService().isPlaying() ? new TranslatableComponent("musicplayer.play.loading_queue") : new TranslatableComponent("musicplayer.play.loading_prepare"));
                            MusicPlayerMod.queueService().requestSong(context.getSource().getServer(), context.getSource(), player, songId);
                            return 1;
                        })))
                    .then(Commands.literal("playlist")
                            .then(Commands.argument("playlist_id", StringArgumentType.string()).executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                String playlistId = StringArgumentType.getString(context, "playlist_id");
                                loading(context.getSource(), new TranslatableComponent("musicplayer.play.loading_playlist"));
                                MusicPlayerMod.queueService().requestPlaylist(context.getSource().getServer(), context.getSource(), player, playlistId);
                                return 1;
                            }).then(Commands.literal("reverse").executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                String playlistId = StringArgumentType.getString(context, "playlist_id");
                                loading(context.getSource(), new TranslatableComponent("musicplayer.play.loading_playlist_reverse"));
                                MusicPlayerMod.queueService().requestPlaylist(context.getSource().getServer(), context.getSource(), player, playlistId, true);
                                return 1;
                            }))))
                    .then(Commands.literal("program")
                            .then(Commands.argument("program_id", StringArgumentType.string()).executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                String programId = StringArgumentType.getString(context, "program_id");
                                loading(context.getSource(), new TranslatableComponent("musicplayer.play.loading_program"));
                                MusicPlayerMod.queueService().requestProgram(context.getSource().getServer(), context.getSource(), player, programId);
                                return 1;
                            })))
                    .then(Commands.literal("radio")
                            .then(Commands.argument("radio_id", StringArgumentType.string()).executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                String radioId = StringArgumentType.getString(context, "radio_id");
                                loading(context.getSource(), new TranslatableComponent("musicplayer.play.loading_radio"));
                                MusicPlayerMod.queueService().requestRadio(context.getSource().getServer(), context.getSource(), player, radioId, false);
                                return 1;
                            }).then(Commands.literal("reverse").executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                String radioId = StringArgumentType.getString(context, "radio_id");
                                loading(context.getSource(), new TranslatableComponent("musicplayer.play.loading_radio_reverse"));
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
                                Messages.warning(context.getSource(), new TranslatableComponent("musicplayer.burn.need_disc"));
                                return 0;
                            }
                            String songId = StringArgumentType.getString(context, "song_id");
                            loading(context.getSource(), new TranslatableComponent("musicplayer.burn.loading"));
                            MinecraftServer server = context.getSource().getServer();
                            MusicPlayerMod.netease().resolveSong(songId).whenComplete((track, throwable) -> server.execute(() -> {
                                if (throwable != null) {
                                    Messages.warning(context.getSource(), new TranslatableComponent("musicplayer.burn.failed", Messages.textOrTranslatable(rootMessage(throwable))));
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
        loading(source, new TranslatableComponent("musicplayer.random.loading"));
        MinecraftServer server = source.getServer();
        MusicPlayerMod.netease().randomHotTracks(10).whenComplete((tracks, throwable) ->
                server.execute(() -> showRandomTracks(source, tracks, throwable)));
        return 1;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> radio() {
        return Commands.literal("radio")
                .executes(context -> {
                    sendHeader(context.getSource());
                    context.getSource().sendSuccess(sectionHeader(new TranslatableComponent("musicplayer.radio.title"), new TranslatableComponent("musicplayer.radio.subtitle")), false);
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
        loading(source, new TranslatableComponent("musicplayer.radio.loading_hot"));
        MinecraftServer server = source.getServer();
        int offset = (page - 1) * 30;
        MusicPlayerMod.netease().hotRadios(30, offset).whenComplete((results, throwable) -> server.execute(() -> {
            if (throwable != null) {
                Messages.warning(source, new TranslatableComponent("musicplayer.radio.hot_failed", Messages.textOrTranslatable(rootMessage(throwable))));
                return;
            }
            if (results.isEmpty()) {
                Messages.warning(source, new TranslatableComponent("musicplayer.radio.no_more_hot"));
                return;
            }
            sendHeader(source);
            Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.radio.hot_title"), new TranslatableComponent("musicplayer.radio.page_hint", page)));
            Messages.sendSuccess(source,  spacer());
            for (SearchEntry entry : results) {
                Messages.sendSuccess(source,  renderEntry(entry, Messages.clickableCommand(new TranslatableComponent("musicplayer.common.view_label"), new TranslatableComponent("musicplayer.common.view_radio_hover"), "/music view radio " + entry.id(), ChatFormatting.GREEN), new TranslatableComponent("musicplayer.common.view_radio_hover"), new TextComponent("")));
            }
            Messages.sendSuccess(source,  spacer());
        }));
    }

    private static int showRadioCategories(CommandSourceStack source) {
        loading(source, new TranslatableComponent("musicplayer.radio.loading_categories"));
        MinecraftServer server = source.getServer();
        MusicPlayerMod.netease().radioCategories().whenComplete((categories, throwable) -> server.execute(() -> {
            if (throwable != null) {
                Messages.warning(source, new TranslatableComponent("musicplayer.radio.categories_failed", Messages.textOrTranslatable(rootMessage(throwable))));
                return;
            }
            sendHeader(source);
            Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.radio.categories_title"), new TranslatableComponent("musicplayer.radio.categories_hint")));
            Messages.sendSuccess(source,  spacer());
            for (SearchEntry entry : categories) {
                Messages.sendSuccess(source,  renderEntry(entry, new TextComponent("").withStyle(ChatFormatting.GREEN), new TranslatableComponent("musicplayer.radio.category_hover"), new TextComponent("")));
            }
            Messages.sendSuccess(source,  spacer());
        }));
        return 1;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> search() {
        return Commands.literal("search")
                .then(pagedSearch("song", new TranslatableComponent("musicplayer.search.loading_song"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchSongs(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendSongResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("artist", new TranslatableComponent("musicplayer.search.loading_artist"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchArtists(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendArtistResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("author", new TranslatableComponent("musicplayer.search.loading_artist"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchArtists(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendArtistResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("playlist", new TranslatableComponent("musicplayer.search.loading_playlist"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchPlaylists(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendPlaylistResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("user", new TranslatableComponent("musicplayer.search.loading_user"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchUsers(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendUserResults(source, keyword, page, literal, results, throwable)));
                }))
                .then(pagedSearch("radio", new TranslatableComponent("musicplayer.search.loading_radio"), (source, keyword, page, literal) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().searchRadios(keyword, page).whenComplete((results, throwable) -> server.execute(() -> sendRadioResults(source, keyword, page, literal, results, throwable)));
                }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> view() {
        return Commands.literal("view")
                .then(pagedView("playlist", "playlist_id", new TranslatableComponent("musicplayer.view.loading_playlist"), (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().playlistDetail(id).whenComplete((playlist, throwable) -> server.execute(() -> showPlaylist(source, id, page, playlist, throwable)));
                }))
                .then(pagedView("user", "user_id", new TranslatableComponent("musicplayer.view.loading_user"), (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().userPlaylists(id).whenComplete((user, throwable) -> server.execute(() -> showUserPlaylists(source, id, page, user, throwable)));
                }))
                .then(pagedView("artist", "artist_id", new TranslatableComponent("musicplayer.view.loading_artist"), (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().artistDetail(id).whenComplete((artist, throwable) -> server.execute(() -> showArtist(source, id, page, artist, throwable, "artist")));
                }))
                .then(pagedView("author", "artist_id", new TranslatableComponent("musicplayer.view.loading_artist"), (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().artistDetail(id).whenComplete((artist, throwable) -> server.execute(() -> showArtist(source, id, page, artist, throwable, "author")));
                }))
                .then(viewUrl())
                .then(pagedView("radio", "radio_id", new TranslatableComponent("musicplayer.view.loading_radio"), (source, id, page) -> {
                    MinecraftServer server = source.getServer();
                    MusicPlayerMod.netease().radioDetail(id).whenComplete((radio, throwable) ->
                            server.execute(() -> showRadioDetail(source, id, page, radio, throwable)));
                }))
                .then(pagedView("program", "program_id", new TranslatableComponent("musicplayer.view.loading_program"), (source, id, page) -> {
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
                Messages.warning(context.getSource(), new TranslatableComponent("musicplayer.view.url_no_id"));
                return 0;
            }
            String id = m.group(1);
            MinecraftServer server = context.getSource().getServer();

            if (url.contains("/song")) {
                loading(context.getSource(), new TranslatableComponent("musicplayer.view.loading_song"));
                MusicPlayerMod.netease().resolveSong(id).whenComplete((track, throwable) ->
                        server.execute(() -> showSong(context.getSource(), track, throwable)));
            } else if (url.contains("/playlist")) {
                loading(context.getSource(), new TranslatableComponent("musicplayer.view.loading_playlist"));
                MusicPlayerMod.netease().playlistDetail(id).whenComplete((playlist, throwable) ->
                        server.execute(() -> showPlaylist(context.getSource(), id, 1, playlist, throwable)));
            } else if (url.contains("/djradio") || url.contains("/dj")) {
                loading(context.getSource(), new TranslatableComponent("musicplayer.view.loading_radio"));
                MusicPlayerMod.netease().radioDetail(id).whenComplete((radio, throwable) ->
                        server.execute(() -> showRadioDetail(context.getSource(), id, 1, radio, throwable)));
            } else {
                Messages.warning(context.getSource(), new TranslatableComponent("musicplayer.view.unsupported_url"));
                return 0;
            }
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> share() {
        return Commands.literal("share")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(sharePagedView("playlist"))
                        .then(sharePagedView("user"))
                        .then(sharePagedView("artist"))
                        .then(sharePagedView("author"))
                        .then(sharePagedView("radio"))
                        .then(sharePagedView("program"))
                        .then(Commands.argument("url", StringArgumentType.greedyString()).executes(context -> {
                            String url = StringArgumentType.getString(context, "url");
                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                            sendShare(context.getSource(), targets, "/music view " + url);
                            return targets.size();
                        })));
    }

    /** 与 /music view 的分页子命令结构一致：{@code <ID>} 与 {@code <ID> page <页码>}。 */
    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> sharePagedView(String literal) {
        String argumentName = literal + "_id";
        return Commands.literal(literal)
                .then(Commands.argument(argumentName, StringArgumentType.string()).executes(context -> {
                    String id = StringArgumentType.getString(context, argumentName);
                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                    sendShare(context.getSource(), targets, "/music view " + literal + " " + id);
                    return targets.size();
                }).then(Commands.literal("page")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                            String id = StringArgumentType.getString(context, argumentName);
                            int page = IntegerArgumentType.getInteger(context, "page");
                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                            sendShare(context.getSource(), targets, "/music view " + literal + " " + id + " page " + page);
                            return targets.size();
                        }))));
    }

    /** 向目标玩家发送可点击的分享消息，点击后等价于执行对应的 /music view 命令。 */
    private static void sendShare(CommandSourceStack source, Collection<ServerPlayer> targets, String viewCommand) {
        MutableComponent message = new TranslatableComponent("musicplayer.share.received",
                new TextComponent(source.getTextName()).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY)
                .append(" ")
                .append(Messages.clickableCommand(
                        new TranslatableComponent("musicplayer.share.click_view"),
                        new TranslatableComponent("musicplayer.share.click_view_hover"),
                        viewCommand,
                        ChatFormatting.GREEN));
        for (ServerPlayer player : targets) {
            player.displayClientMessage(message, false);
        }
        Messages.success(source, new TranslatableComponent("musicplayer.share.sent", targets.size()));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> config() {
        return Commands.literal("config")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload").executes(context -> {
                    MusicPlayerConfigManager.load();
                    MusicPlayerMod.queueService().clearTrackCache();
                    // 重新加载配置文件中的 bgmPlaylistId 并立即同步在线客户端
                    MusicPlayerMod.syncBgmConfig(context.getSource().getServer());
                    Messages.success(context.getSource(), new TranslatableComponent("musicplayer.config.reloaded"));
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {
                    MusicPlayerConfig config = MusicPlayerConfigManager.get();
                    MusicApiMode mode = MusicApiMode.fromConfig(config.apiMode);
                    switch (mode) {
                        case DIRECT -> Messages.info(context.getSource(),
                                new TranslatableComponent("musicplayer.config.status_base_url_direct"));
                        case AUTO -> Messages.info(context.getSource(),
                                new TranslatableComponent("musicplayer.config.status_base_url_auto", config.neteaseBaseUrl));
                        default -> Messages.info(context.getSource(),
                                new TranslatableComponent("musicplayer.config.status_base_url", config.neteaseBaseUrl));
                    }
                    Messages.info(context.getSource(), new TranslatableComponent("musicplayer.config.status_api_mode",
                            new TranslatableComponent(mode.statusKey())));
                    Messages.info(context.getSource(), new TranslatableComponent("musicplayer.config.status_requests", yesNo(config.allowSongRequest), yesNo(config.allowPlaylistRequest)));
                    Messages.info(context.getSource(), new TranslatableComponent("musicplayer.config.status_auto", yesNo(config.autoAdvance), yesNo(config.showLoadingHints)));
                    Messages.info(context.getSource(), new TranslatableComponent("musicplayer.config.status_lyrics", yesNo(config.showLyrics)));
                    Messages.info(context.getSource(), new TranslatableComponent("musicplayer.config.status_bgm",
                            config.bgmPlaylistId == null || config.bgmPlaylistId.isBlank() ? "off" : config.bgmPlaylistId));
                    Messages.info(context.getSource(), new TranslatableComponent("musicplayer.config.status_proxy",
                            !config.proxy.isBlank()
                                    ? new TranslatableComponent("musicplayer.config.proxy_manual", config.proxy)
                                    : new TranslatableComponent(config.useSystemProxy ? "musicplayer.config.proxy_auto" : "musicplayer.config.proxy_direct")));
                    Messages.info(context.getSource(), new TranslatableComponent("musicplayer.config.status_network", yesNo(config.preferIpv4), config.connectTimeoutSeconds, config.readTimeoutSeconds));
                    Messages.info(context.getSource(), new TranslatableComponent("musicplayer.config.status_limits", config.searchLimit, config.maxQueueSize, config.maxSongsPerPlayer, config.playlistQueueLimit, config.queueCacheSize));
                    Messages.info(context.getSource(), new TranslatableComponent("musicplayer.config.status_vote_skip", config.voteSkipPercent));
                    Messages.info(context.getSource(), new TranslatableComponent("musicplayer.config.status_loot", yesNo(config.enableLootMusicDiscs), config.lootMusicDiscChance, config.lootMusicDiscCount));
                    return 1;
                }))
                .then(Commands.literal("clearqueue").executes(context -> {
                    MusicPlayerMod.queueService().clearQueue(context.getSource());
                    return 1;
                }))
                .then(Commands.literal("set")
                        .then(Commands.literal("baseUrl").then(Commands.argument("value", StringArgumentType.greedyString()).executes(context -> {
                            MusicPlayerConfig config = MusicPlayerConfigManager.get();
                            String value = MusicPlayerConfig.normalizeBaseUrl(StringArgumentType.getString(context, "value"));
                            if (value.isEmpty()) {
                                Messages.warning(context.getSource(), new TranslatableComponent("musicplayer.error.invalid_api_url"));
                                return 0;
                            }
                            if (!config.allowCustomServer && !"default".equalsIgnoreCase(value) && !MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL.equalsIgnoreCase(value)) {
                                Messages.warning(context.getSource(), new TranslatableComponent("musicplayer.config.custom_server_disabled"));
                                return 0;
                            }
                            config.neteaseBaseUrl = "default".equalsIgnoreCase(value) ? MusicPlayerConfig.DEFAULT_NETEASE_BASE_URL : value;
                            MusicPlayerConfigManager.save();
                            // 更换 API 地址可能导致缓存资源 URL 失效，清掉广播资源缓存
                            MusicPlayerMod.queueService().clearTrackCache();
                            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.config.base_url_updated", config.neteaseBaseUrl));
                            return 1;
                        })))
                        .then(boolSetting("allowCustomServer", value -> MusicPlayerConfigManager.get().allowCustomServer = value))
                        .then(Commands.literal("apiMode").then(Commands.argument("value", StringArgumentType.word())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        new String[]{"auto", "direct", "proxy"}, builder))
                                .executes(context -> {
                                    String value = MusicApiMode.normalize(StringArgumentType.getString(context, "value"));
                                    if (value == null) {
                                        Messages.warning(context.getSource(), new TranslatableComponent("musicplayer.config.api_mode_invalid"));
                                        return 0;
                                    }
                                    MusicPlayerConfigManager.get().apiMode = value;
                                    MusicPlayerConfigManager.save();
                                    // 切换取数模式后旧缓存可能来自另一条链路，清空音源缓存并重置熔断状态
                                    MusicPlayerMod.queueService().clearTrackCache();
                                    MusicPlayerMod.musicApiRouter().resetHealth();
                                    Messages.success(context.getSource(), new TranslatableComponent("musicplayer.config.api_mode_updated",
                                            new TranslatableComponent(MusicApiMode.fromConfig(value).statusKey())));
                                    return 1;
                                })))
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
                            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.config.proxy_updated", MusicPlayerConfigManager.get().proxy.isBlank() ? "<none>" : MusicPlayerConfigManager.get().proxy));
                            return 1;
                        })))
                        .then(Commands.literal("bgmPlaylist").then(Commands.argument("value", StringArgumentType.string()).executes(context -> {
                            String value = StringArgumentType.getString(context, "value").trim();
                            String normalized;
                            if ("off".equalsIgnoreCase(value)) {
                                normalized = "";
                            } else if ("reset".equalsIgnoreCase(value)) {
                                normalized = MusicPlayerConfig.DEFAULT_BGM_PLAYLIST_ID;
                            } else if (!value.isEmpty() && value.length() <= 20
                                    && value.chars().allMatch(c -> c >= '0' && c <= '9')) {
                                normalized = value;
                            } else {
                                Messages.warning(context.getSource(), new TranslatableComponent("musicplayer.config.bgm_playlist_invalid"));
                                return 0;
                            }
                            MusicPlayerConfigManager.get().bgmPlaylistId = normalized;
                            MusicPlayerConfigManager.save();
                            MusicPlayerMod.syncBgmConfig(context.getSource().getServer());
                            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.config.bgm_playlist_updated",
                                    normalized.isBlank() ? "off" : normalized));
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
                        .then(doubleSetting("voteSkipPercent", 0.1D, 1.0D, value -> MusicPlayerConfigManager.get().voteSkipPercent = value)))
                .then(Commands.literal("reset")
                        .then(Commands.literal("bgmPlaylist").executes(context -> {
                            MusicPlayerConfigManager.get().bgmPlaylistId = MusicPlayerConfig.DEFAULT_BGM_PLAYLIST_ID;
                            MusicPlayerConfigManager.save();
                            MusicPlayerMod.syncBgmConfig(context.getSource().getServer());
                            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.config.bgm_playlist_updated",
                                    MusicPlayerConfig.DEFAULT_BGM_PLAYLIST_ID));
                            return 1;
                        }))
                        .then(Commands.literal("all").executes(context -> {
                            MusicPlayerConfigManager.resetAll();
                            MusicPlayerMod.queueService().clearTrackCache();
                            MusicPlayerMod.syncBgmConfig(context.getSource().getServer());
                            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.config.reset_all"));
                            return 1;
                        }))
                        .then(resetSetting("baseUrl", (config, defaults) -> config.neteaseBaseUrl = defaults.neteaseBaseUrl,
                                context -> MusicPlayerMod.queueService().clearTrackCache()))
                        .then(resetSetting("apiMode", (config, defaults) -> config.apiMode = defaults.apiMode,
                                context -> {
                                    MusicPlayerMod.queueService().clearTrackCache();
                                    MusicPlayerMod.musicApiRouter().resetHealth();
                                }))
                        .then(resetSetting("allowCustomServer", (config, defaults) -> config.allowCustomServer = defaults.allowCustomServer))
                        .then(resetSetting("allowSongRequest", (config, defaults) -> config.allowSongRequest = defaults.allowSongRequest))
                        .then(resetSetting("allowPlaylistRequest", (config, defaults) -> config.allowPlaylistRequest = defaults.allowPlaylistRequest))
                        .then(resetSetting("autoAdvance", (config, defaults) -> config.autoAdvance = defaults.autoAdvance))
                        .then(resetSetting("announceQueueChanges", (config, defaults) -> config.announceQueueChanges = defaults.announceQueueChanges))
                        .then(resetSetting("showLoadingHints", (config, defaults) -> config.showLoadingHints = defaults.showLoadingHints))
                        .then(resetSetting("showLyrics", (config, defaults) -> config.showLyrics = defaults.showLyrics))
                        .then(resetSetting("useSystemProxy", (config, defaults) -> config.useSystemProxy = defaults.useSystemProxy))
                        .then(resetSetting("preferIpv4", (config, defaults) -> config.preferIpv4 = defaults.preferIpv4))
                        .then(resetSetting("proxy", (config, defaults) -> config.proxy = defaults.proxy))
                        .then(resetSetting("connectTimeoutSeconds", (config, defaults) -> config.connectTimeoutSeconds = defaults.connectTimeoutSeconds))
                        .then(resetSetting("readTimeoutSeconds", (config, defaults) -> config.readTimeoutSeconds = defaults.readTimeoutSeconds))
                        .then(resetSetting("searchLimit", (config, defaults) -> config.searchLimit = defaults.searchLimit))
                        .then(resetSetting("maxQueueSize", (config, defaults) -> config.maxQueueSize = defaults.maxQueueSize))
                        .then(resetSetting("maxSongsPerPlayer", (config, defaults) -> config.maxSongsPerPlayer = defaults.maxSongsPerPlayer))
                        .then(resetSetting("playlistQueueLimit", (config, defaults) -> config.playlistQueueLimit = defaults.playlistQueueLimit))
                        .then(resetSetting("queueCacheSize", (config, defaults) -> config.queueCacheSize = defaults.queueCacheSize,
                                context -> MusicPlayerMod.queueService().refreshCacheSettings()))
                        .then(resetSetting("enableLootMusicDiscs", (config, defaults) -> config.enableLootMusicDiscs = defaults.enableLootMusicDiscs))
                        .then(resetSetting("lootMusicDiscCount", (config, defaults) -> config.lootMusicDiscCount = defaults.lootMusicDiscCount))
                        .then(resetSetting("lootMusicDiscChance", (config, defaults) -> config.lootMusicDiscChance = defaults.lootMusicDiscChance))
                        .then(resetSetting("voteSkipPercent", (config, defaults) -> config.voteSkipPercent = defaults.voteSkipPercent)));
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
        return boolSetting(name, setter, context -> { });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> resetSetting(String name, BiConsumer<MusicPlayerConfig, MusicPlayerConfig> resetter) {
        return resetSetting(name, resetter, context -> { });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> resetSetting(String name, BiConsumer<MusicPlayerConfig, MusicPlayerConfig> resetter, Consumer<com.mojang.brigadier.context.CommandContext<CommandSourceStack>> afterSet) {
        return Commands.literal(name).executes(context -> {
            resetter.accept(MusicPlayerConfigManager.get(), new MusicPlayerConfig());
            MusicPlayerConfigManager.save();
            afterSet.accept(context);
            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.config.reset_done", name));
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> boolSetting(String name, Consumer<Boolean> setter, Consumer<com.mojang.brigadier.context.CommandContext<CommandSourceStack>> afterSet) {
        return Commands.literal(name).then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
            boolean value = BoolArgumentType.getBool(context, "value");
            setter.accept(value);
            MusicPlayerConfigManager.save();
            afterSet.accept(context);
            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.config.updated", name, value));
            return 1;
        }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> intSetting(String name, int min, int max, IntConsumer setter) {
        return Commands.literal(name).then(Commands.argument("value", IntegerArgumentType.integer(min, max)).executes(context -> {
            int value = IntegerArgumentType.getInteger(context, "value");
            setter.accept(value);
            MusicPlayerConfigManager.save();
            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.config.updated", name, value));
            return 1;
        }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> doubleSetting(String name, double min, double max, DoubleConsumer setter) {
        return Commands.literal(name).then(Commands.argument("value", DoubleArgumentType.doubleArg(min, max)).executes(context -> {
            double value = DoubleArgumentType.getDouble(context, "value");
            setter.accept(value);
            MusicPlayerConfigManager.save();
            Messages.success(context.getSource(), new TranslatableComponent("musicplayer.config.updated", name, value));
            return 1;
        }));
    }

    /** 列表中的分享按钮：点击后向聊天框填入 /music share 命令（默认目标 @a，可修改后发送）。 */
    private static MutableComponent shareSuggest(String viewType, String id, int page) {
        return Messages.suggestable(
                new TranslatableComponent("musicplayer.common.share_label"),
                new TranslatableComponent("musicplayer.common.share_hover"),
                "/music share @a " + viewType + " " + id + " page " + page,
                ChatFormatting.AQUA);
    }

    private static void sendSongResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.search.song_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, new TranslatableComponent("musicplayer.search.no_songs"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.search.songs_title"), new TranslatableComponent("musicplayer.search.songs_hint")));
        Messages.sendSuccess(source,  spacer());
        for (SearchEntry entry : results) {
            Messages.sendSuccess(source,  renderEntry(entry, trackActions(source, entry.id(), new TranslatableComponent("musicplayer.common.request_label"), new TranslatableComponent("musicplayer.common.request_hover"), ChatFormatting.GREEN), new TranslatableComponent("musicplayer.common.request_hover"), new TranslatableComponent("musicplayer.common.click_artist_hover")));
        }
        Messages.sendSuccess(source,  spacer());
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void sendArtistResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.search.artist_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, new TranslatableComponent("musicplayer.search.no_artists"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.search.artists_title"), new TranslatableComponent("musicplayer.search.artists_hint")));
        Messages.sendSuccess(source,  spacer());
        for (SearchEntry entry : results) {
            Messages.sendSuccess(source,  renderEntry(entry, Messages.clickableCommand(new TranslatableComponent("musicplayer.common.view_label"), new TranslatableComponent("musicplayer.common.view_artist_hover"), "/music view artist " + entry.id(), ChatFormatting.GREEN)
                    .append(new TextComponent(" "))
                    .append(shareSuggest("artist", entry.id(), 1)), new TranslatableComponent("musicplayer.common.view_artist_hover"), new TextComponent("")));
        }
        Messages.sendSuccess(source,  spacer());
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void sendPlaylistResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.search.playlist_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, new TranslatableComponent("musicplayer.search.no_playlists"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.search.playlists_title"), new TranslatableComponent("musicplayer.search.playlists_hint")));
        Messages.sendSuccess(source,  spacer());
        for (SearchEntry entry : results) {
            Messages.sendSuccess(source,  renderEntry(entry, Messages.clickableCommand(new TranslatableComponent("musicplayer.common.view_label"), new TranslatableComponent("musicplayer.common.view_playlist_hover"), "/music view playlist " + entry.id(), ChatFormatting.GREEN)
                    .append(new TextComponent(" "))
                    .append(shareSuggest("playlist", entry.id(), 1)), new TranslatableComponent("musicplayer.common.view_playlist_hover"), new TranslatableComponent("musicplayer.common.click_creator_hover")));
        }
        Messages.sendSuccess(source,  spacer());
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void sendUserResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.search.user_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, new TranslatableComponent("musicplayer.search.no_users"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.search.users_title"), new TranslatableComponent("musicplayer.search.users_hint")));
        Messages.sendSuccess(source,  spacer());
        for (SearchEntry entry : results) {
            Messages.sendSuccess(source,  renderEntry(entry, Messages.clickableCommand(new TranslatableComponent("musicplayer.common.view_label"), new TranslatableComponent("musicplayer.common.view_user_playlists_hover"), "/music view user " + entry.id(), ChatFormatting.GREEN)
                    .append(new TextComponent(" "))
                    .append(shareSuggest("user", entry.id(), 1)), new TranslatableComponent("musicplayer.common.view_user_playlists_hover"), new TextComponent("")));
        }
        Messages.sendSuccess(source,  spacer());
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void showRandomTracks(CommandSourceStack source, List<TrackInfo> tracks, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.random.failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (tracks == null || tracks.isEmpty()) {
            Messages.warning(source, new TranslatableComponent("musicplayer.random.empty"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.random.title"), new TranslatableComponent("musicplayer.random.hint")));
        Messages.sendSuccess(source,  spacer());
        for (TrackInfo track : tracks) {
            Messages.sendSuccess(source,  renderRandomTrack(source, track));
        }
        Messages.sendSuccess(source,  spacer());
    }

    private static void showPlaylist(CommandSourceStack source, String playlistId, int requestedPage, PlaylistInfo playlist, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.playlist.load_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (playlist == null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.playlist.not_found"));
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
                    Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.playlist.header_prefix").withStyle(ChatFormatting.GOLD)
                            .append(clickableText(playlist.title(), "/music view playlist " + playlist.id(), new TranslatableComponent("musicplayer.common.view_playlist_hover"), ChatFormatting.AQUA))
                            .append(new TextComponent(" · ").withStyle(ChatFormatting.GRAY))
                            .append(new TranslatableComponent("musicplayer.playlist.owner_prefix").withStyle(ChatFormatting.GRAY))
                            .append(clickableText(playlist.ownerName(), "/music view user " + playlist.ownerId(), new TranslatableComponent("musicplayer.common.view_creator_hover"), ChatFormatting.YELLOW))
                            .append(new TextComponent(" "))
                            .append(Messages.clickableCommand(new TranslatableComponent("musicplayer.playlist.play_label"), new TranslatableComponent("musicplayer.playlist.play_hover"), "/music play playlist " + playlist.id(), ChatFormatting.GREEN))
                            .append(new TextComponent(" "))
                            .append(Messages.clickableCommand(new TranslatableComponent("musicplayer.playlist.reverse_label"), new TranslatableComponent("musicplayer.playlist.reverse_hover"), "/music play playlist " + playlist.id() + " reverse", ChatFormatting.GOLD))
                            .append(new TextComponent(" "))
                            .append(shareSuggest("playlist", playlist.id(), page)));
                    Messages.sendSuccess(source,  spacer());

                    if (t != null) {
                        Messages.warning(source, new TranslatableComponent("musicplayer.playlist.tracks_failed", Messages.textOrTranslatable(rootMessage(t))));
                        return;
                    }
                    for (SearchEntry track : tracks) {
                        Messages.sendSuccess(source,  renderEntry(track,
                                trackActions(source, track.id(), new TranslatableComponent("musicplayer.common.request_label"), new TranslatableComponent("musicplayer.play.request_hover"), ChatFormatting.GREEN),
                                new TranslatableComponent("musicplayer.play.request_hover"), new TranslatableComponent("musicplayer.common.click_artist_hover")));
                    }
                    Messages.sendSuccess(source,  spacer());
                    sendNavigation(source, page, totalPages,
                            "/music view playlist " + playlistId + " page %d", true,
                            "/music view playlist " + playlistId + " page ");
                    Messages.sendSuccess(source,  spacer());
                }));
    }

    private static void showUserPlaylists(CommandSourceStack source, String userId, int requestedPage, UserPlaylistView user, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.view.user_playlists_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (user == null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.view.user_playlists_not_found"));
            return;
        }
        sendHeader(source);
        // 隐藏作为背景音乐的歌单，仅展示其他歌单
        List<SearchEntry> visiblePlaylists = new ArrayList<>(user.playlists());
        String bgmPlaylistId = MusicPlayerConfigManager.get().bgmPlaylistId;
        if (bgmPlaylistId != null && !bgmPlaylistId.isBlank()) {
            String hiddenId = bgmPlaylistId.trim();
            visiblePlaylists.removeIf(playlist -> hiddenId.equals(playlist.id()));
        }
        PageWindow page = pageWindow(visiblePlaylists.size(), requestedPage, pageSize());
        Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.view.user_prefix").withStyle(ChatFormatting.GOLD)
                .append(clickableText(user.name(), "/music view user " + user.id(), new TranslatableComponent("musicplayer.common.view_user_playlists_hover"), ChatFormatting.AQUA))
                .append(new TextComponent(" "))
                .append(shareSuggest("user", user.id(), page.page())));
        if (user.signature() != null && !user.signature().isBlank()) {
            Messages.sendSuccess(source,  spacer());
            Messages.sendSuccess(source,  new TextComponent(user.signature()).withStyle(ChatFormatting.GRAY));
        }
        if (visiblePlaylists.isEmpty()) {
            Messages.warning(source, new TranslatableComponent("musicplayer.view.user_no_playlists"));
            return;
        }
        Messages.sendSuccess(source,  spacer());
        for (SearchEntry playlist : slicePage(visiblePlaylists, page)) {
            Messages.sendSuccess(source,  renderEntry(playlist, Messages.clickableCommand(new TranslatableComponent("musicplayer.common.view_label"), new TranslatableComponent("musicplayer.common.view_playlist_hover"), "/music view playlist " + playlist.id(), ChatFormatting.GREEN), new TranslatableComponent("musicplayer.common.view_playlist_hover"), new TextComponent("")));
        }
        Messages.sendSuccess(source,  spacer());
        sendNavigation(source, page.page(), page.totalPages(), "/music view user " + userId + " page %d", true, "/music view user " + userId + " page ");
        Messages.sendSuccess(source,  spacer());
    }

    private static void showArtist(CommandSourceStack source, String artistId, int requestedPage, ArtistInfo artist, Throwable throwable, String literal) {
        if (throwable != null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.view.artist_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (artist == null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.view.artist_not_found"));
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
                    Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.view.artist_prefix").withStyle(ChatFormatting.GOLD)
                            .append(clickableText(artist.name(), "/music view " + literal + " " + artist.id(), new TranslatableComponent("musicplayer.common.view_artist_hover"), ChatFormatting.AQUA))
                            .append(new TextComponent(" "))
                            .append(shareSuggest(literal, artist.id(), page)));
                    if (artist.description() != null && !artist.description().isBlank()) {
                        Messages.sendSuccess(source,  spacer());
                        Messages.sendSuccess(source,  new TextComponent(artist.description()).withStyle(ChatFormatting.GRAY));
                    }
                    if (t != null) {
                        Messages.warning(source, new TranslatableComponent("musicplayer.view.songs_failed", Messages.textOrTranslatable(rootMessage(t))));
                        return;
                    }
                    if (tracks.isEmpty()) {
                        Messages.warning(source, new TranslatableComponent("musicplayer.view.artist_no_songs"));
                        return;
                    }
                    Messages.sendSuccess(source,  spacer());
                    for (SearchEntry track : tracks) {
                        Messages.sendSuccess(source,  renderEntry(track,
                                trackActions(source, track.id(), new TranslatableComponent("musicplayer.common.request_label"), new TranslatableComponent("musicplayer.play.request_hover"), ChatFormatting.GREEN),
                                new TranslatableComponent("musicplayer.play.request_hover"), new TextComponent("")));
                    }
                    Messages.sendSuccess(source,  spacer());
                    sendNavigation(source, page, totalPages, "/music view " + literal + " " + artistId + " page %d", true, "/music view " + literal + " " + artistId + " page ");
                    Messages.sendSuccess(source,  spacer());
                }));
    }

    private static void showSong(CommandSourceStack source, TrackInfo track, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.view.song_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (track == null || track.title() == null || track.title().isBlank()) {
            Messages.warning(source, new TranslatableComponent("musicplayer.view.song_not_found"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.view.song_title"), null));
        Messages.sendSuccess(source,  spacer());

        Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.view.song_prefix").withStyle(ChatFormatting.GOLD)
                .append(clickableText(track.title(), "/music play song " + track.id(), new TranslatableComponent("musicplayer.common.tap_request_hover"), ChatFormatting.AQUA))
                .append(new TextComponent(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(clickableText(track.artist(),
                        track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(),
                        new TranslatableComponent("musicplayer.common.click_artist_hover"), ChatFormatting.GRAY)));

        if (track.durationMillis() > 0L) {
            Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.view.duration_prefix").withStyle(ChatFormatting.GRAY)
                    .append(new TextComponent(Messages.formatDuration(track.durationMillis())).withStyle(ChatFormatting.WHITE)));
        }

        if (track.coverUrl() != null && !track.coverUrl().isBlank()) {
            Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.view.cover_prefix").withStyle(ChatFormatting.GRAY)
                    .append(Messages.clickableUrl(new TranslatableComponent("musicplayer.common.click_view_label"), new TranslatableComponent("musicplayer.view.cover_hover"), track.coverUrl(), ChatFormatting.BLUE)));
        }

        Messages.sendSuccess(source,  new TextComponent("ID: ").withStyle(ChatFormatting.GRAY)
                .append(new TextComponent(track.id()).withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, track.id()))
                        .withColor(ChatFormatting.WHITE)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TranslatableComponent("musicplayer.common.copy_hover"))))));

        MutableComponent burnAction = buildBurnAction(source, track.id());
        if (burnAction != null) {
            Messages.sendSuccess(source,  spacer());
            Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.common.actions_prefix").withStyle(ChatFormatting.GOLD)
                    .append(burnAction));
        }

        Messages.sendSuccess(source,  spacer());
    }

    private static void sendRadioResults(CommandSourceStack source, String keyword, int page, String literal, List<SearchEntry> results, Throwable throwable) {
        if (throwable != null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.search.radio_failed", Messages.textOrTranslatable(rootMessage(throwable))));
            return;
        }
        if (results.isEmpty()) {
            Messages.warning(source, new TranslatableComponent("musicplayer.search.no_radios"));
            return;
        }
        sendHeader(source);
        Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.search.radios_title"), new TranslatableComponent("musicplayer.search.radios_hint")));
        Messages.sendSuccess(source,  spacer());
        for (SearchEntry entry : results) {
            MutableComponent actions = Messages.clickableCommand(new TranslatableComponent("musicplayer.radio.play_label"), new TranslatableComponent("musicplayer.common.play_in_order_hover"), "/music play radio " + entry.id(), ChatFormatting.GREEN)
                    .append(new TextComponent(" "))
                    .append(Messages.clickableCommand(new TranslatableComponent("musicplayer.common.view_label"), new TranslatableComponent("musicplayer.common.view_radio_hover"), "/music view radio " + entry.id(), ChatFormatting.GRAY))
                    .append(new TextComponent(" "))
                    .append(shareSuggest("radio", entry.id(), 1));
            Messages.sendSuccess(source,  renderEntry(entry, actions, new TranslatableComponent("musicplayer.radio.play_hover"), new TranslatableComponent("musicplayer.radio.play_hover")));
        }
        Messages.sendSuccess(source,  spacer());
        sendSearchNavigation(source, literal, keyword, page, results.size());
    }

    private static void showRadioDetail(CommandSourceStack source, String radioId, int requestedPage, RadioInfo radio, Throwable throwable) {
        if (throwable != null || radio == null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.radio.load_failed"));
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
                            Messages.clickableCommand(new TranslatableComponent("musicplayer.radio.play_all_label"), new TranslatableComponent("musicplayer.common.play_in_order_hover"), "/music play radio " + radioId, ChatFormatting.GREEN),
                            Messages.clickableCommand(new TranslatableComponent("musicplayer.radio.reverse_label"), new TranslatableComponent("musicplayer.radio.reverse_hover"), "/music play radio " + radioId + " reverse", ChatFormatting.GOLD),
                            shareSuggest("radio", radioId, page),
                            Messages.clickableCommand(new TranslatableComponent("musicplayer.common.help_label"), new TranslatableComponent("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));
                    MutableComponent headerLine = new TextComponent("──").withStyle(ChatFormatting.GOLD)
                            .append(new TranslatableComponent("musicplayer.radio.detail_title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    if (!radio.name().isBlank()) {
                        headerLine.append(new TextComponent("  " + radio.name()).withStyle(ChatFormatting.GRAY));
                    }
                    Messages.sendSuccess(source, headerLine);
                    Messages.sendSuccess(source,  spacer());

                    if (radio.playCount() > 0) {
                        Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.radio.play_count_prefix").withStyle(ChatFormatting.GRAY)
                                .append(new TextComponent(String.valueOf(radio.playCount())).withStyle(ChatFormatting.WHITE)));
                    }
                    if (radio.radioFeeType() != 0) {
                        Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.radio.paid_notice").withStyle(ChatFormatting.RED));
                    }
                    if (radio.description() != null && !radio.description().isBlank()) {
                        Messages.sendSuccess(source,  spacer());
                        Messages.sendSuccess(source,  new TextComponent(radio.description()).withStyle(ChatFormatting.GRAY));
                    }

                    if (t != null || pagePrograms == null || pagePrograms.isEmpty()) {
                        Messages.sendSuccess(source,  spacer());
                        Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.radio.no_programs").withStyle(ChatFormatting.GRAY));
                        return;
                    }

                    Messages.sendSuccess(source,  spacer());
                    Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.radio.programs_title", page, totalPages), new TranslatableComponent("musicplayer.radio.programs_hint")));
                    for (ProgramInfo prog : pagePrograms) {
                        Messages.sendSuccess(source,  renderProgramEntry(source, prog));
                    }
                    Messages.sendSuccess(source,  spacer());
                    if (totalPages > 1) {
                        String baseCmd = "/music view radio " + radioId;
                        sendNavigation(source, page, totalPages, baseCmd + " page %d", true, baseCmd + " page ");
                    }
                    Messages.sendSuccess(source,  spacer());
                }));
    }

    private static void showProgramDetail(CommandSourceStack source, ProgramInfo program, Throwable throwable) {
        if (throwable != null || program == null) {
            Messages.warning(source, new TranslatableComponent("musicplayer.view.program_failed"));
            return;
        }
        sendHeader(source);
        sendQuickBar(source,
                Messages.clickableCommand(new TranslatableComponent("musicplayer.common.play_label"), new TranslatableComponent("musicplayer.view.play_program_hover"), "/music play program " + program.id(), ChatFormatting.GREEN),
                program.radioId().isBlank() ? null : Messages.clickableCommand(new TranslatableComponent("musicplayer.view.radio_label"), new TranslatableComponent("musicplayer.view.owning_radio_hover"), "/music view radio " + program.radioId(), ChatFormatting.AQUA),
                shareSuggest("program", program.id(), 1),
                Messages.clickableCommand(new TranslatableComponent("musicplayer.common.help_label"), new TranslatableComponent("musicplayer.common.help_hover"), "/music help", ChatFormatting.DARK_GRAY));
        Messages.sendSuccess(source,  sectionHeader(new TranslatableComponent("musicplayer.view.program_title"), new TextComponent(program.name())));
        Messages.sendSuccess(source,  spacer());
        Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.view.program_prefix").withStyle(ChatFormatting.GOLD)
                .append(new TextComponent(program.name()).withStyle(ChatFormatting.WHITE)));
        if (!program.radioName().isBlank()) {
            Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.view.radio_prefix").withStyle(ChatFormatting.GRAY)
                    .append(clickableText(program.radioName(), "/music view radio " + program.radioId(), new TranslatableComponent("musicplayer.common.view_radio_hover"), ChatFormatting.AQUA)));
        }
        if (program.durationMillis() > 0L) {
            Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.view.duration_prefix").withStyle(ChatFormatting.GRAY)
                    .append(new TextComponent(Messages.formatDuration(program.durationMillis())).withStyle(ChatFormatting.WHITE)));
        }
        if (program.coverUrl() != null && !program.coverUrl().isBlank()) {
            Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.view.cover_prefix").withStyle(ChatFormatting.GRAY)
                    .append(Messages.clickableUrl(new TranslatableComponent("musicplayer.common.click_view_label"), new TranslatableComponent("musicplayer.view.cover_hover"), program.coverUrl(), ChatFormatting.BLUE)));
        }
        if (program.description() != null && !program.description().isBlank()) {
            Messages.sendSuccess(source,  spacer());
            Messages.sendSuccess(source,  new TextComponent(program.description()).withStyle(ChatFormatting.GRAY));
        }
        Messages.sendSuccess(source,  spacer());
        Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.common.actions_prefix").withStyle(ChatFormatting.GOLD)
                .append(Messages.clickableCommand(new TranslatableComponent("musicplayer.common.play_label"), new TranslatableComponent("musicplayer.view.play_program_hover"), "/music play program " + program.id(), ChatFormatting.GREEN)));
        Messages.sendSuccess(source,  spacer());
    }

    private static MutableComponent renderProgramEntry(CommandSourceStack source, ProgramInfo program) {
        MutableComponent line = Messages.clickableCommand(new TranslatableComponent("musicplayer.common.play_label"), new TranslatableComponent("musicplayer.view.play_now_hover"), "/music play program " + program.id(), ChatFormatting.GREEN);
        line.append(new TextComponent(" "));
        line.append(Messages.clickableCommand(new TextComponent(program.name()), new TranslatableComponent("musicplayer.view.play_now_hover"), "/music play program " + program.id(), ChatFormatting.AQUA));
        if (program.durationMillis() > 0L) {
            line.append(new TextComponent("  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(new TextComponent(Messages.formatDuration(program.durationMillis())).withStyle(ChatFormatting.GRAY));
        }
        line.append(new TextComponent(" "));
        line.append(Messages.clickableCommand(new TranslatableComponent("musicplayer.view.detail_label"), new TranslatableComponent("musicplayer.view.program_detail_hover"), "/music view program " + program.id(), ChatFormatting.GRAY));
        return line;
    }

    private static MutableComponent renderRandomTrack(CommandSourceStack source, TrackInfo track) {
        MutableComponent line = trackActions(source, track.id(), new TranslatableComponent("musicplayer.common.request_label"), new TranslatableComponent("musicplayer.random.request_hover"), ChatFormatting.GREEN);
        line.append(new TextComponent(" "));
        line.append(clickableText(track.title(), "/music play song " + track.id(), new TranslatableComponent("musicplayer.random.request_hover"), ChatFormatting.AQUA));
        line.append(new TextComponent(" - ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(clickableText(track.artist(),
                track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(),
                new TranslatableComponent("musicplayer.common.view_artist_hover"),
                ChatFormatting.GRAY));
        if (track.sourceUrls() != null && !track.sourceUrls().isEmpty()) {
            line.append(new TextComponent(" "));
            line.append(Messages.clickableUrl(new TranslatableComponent("musicplayer.common.download_label"), new TranslatableComponent("musicplayer.common.download_hover"), track.sourceUrls().get(0), ChatFormatting.BLUE));
        }
        return line;
    }

    private static MutableComponent renderCurrentTrack(CommandSourceStack source, TrackInfo track, String elapsed, String duration, String requesterName) {
        MutableComponent line = new TranslatableComponent("musicplayer.now.playing_prefix").withStyle(ChatFormatting.GOLD)
                .append(clickableText(track.title(), "/music play song " + track.id(), new TranslatableComponent("musicplayer.common.rerequest_hover"), ChatFormatting.AQUA))
                .append(new TextComponent(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(clickableText(track.artist(), track.artistId() == null || track.artistId().isBlank() ? "" : "/music view artist " + track.artistId(), new TranslatableComponent("musicplayer.common.click_artist_hover"), ChatFormatting.GRAY));
        MutableComponent burnAction = buildBurnAction(source, track.id());
        if (burnAction != null) {
            line.append(new TextComponent(" "));
            line.append(burnAction);
        }
        if (!track.sourceUrls().isEmpty()) {
            line.append(new TextComponent(" "));
            line.append(Messages.clickableUrl(new TranslatableComponent("musicplayer.now.open_url_label"), new TranslatableComponent("musicplayer.now.open_url_hover"), track.sourceUrls().get(0), ChatFormatting.GREEN));
        }
        return line;
    }

    private static Component renderProgressLine(String elapsed, String duration, String requesterName, boolean paused, long elapsedMs, long durationMs) {
        String progress = duration != null && !duration.isEmpty()
                ? elapsed + " / " + duration
                : elapsed;
        MutableComponent line = new TextComponent("");

        if (durationMs > 0L) {
            int barLen = 20;
            int filled = (int) (barLen * elapsedMs / Math.max(1L, durationMs));
            filled = Math.max(0, Math.min(barLen, filled));
            line.append(new TextComponent("[").withStyle(ChatFormatting.DARK_GRAY));
            line.append(new TextComponent("█".repeat(filled)).withStyle(ChatFormatting.GREEN));
            line.append(new TextComponent("░".repeat(barLen - filled)).withStyle(ChatFormatting.DARK_GRAY));
            line.append(new TextComponent("] ").withStyle(ChatFormatting.DARK_GRAY));
        }

        line.append(new TextComponent(progress).withStyle(ChatFormatting.WHITE));

        if (paused) {
            line.append(new TextComponent("  ⏸").withStyle(ChatFormatting.YELLOW));
        }

        if (requesterName != null && !requesterName.isEmpty()) {
            line.append(new TextComponent("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(new TranslatableComponent("musicplayer.progress.requester_prefix").withStyle(ChatFormatting.GRAY));
            line.append(new TextComponent(requesterName).withStyle(ChatFormatting.AQUA));
        }
        return line;
    }

    private static MutableComponent trackActions(CommandSourceStack source, String songId, Component primaryLabel, Component primaryHover, ChatFormatting primaryColor) {
        MutableComponent actions = Messages.clickableCommand(primaryLabel, primaryHover, "/music play song " + songId, primaryColor);
        MutableComponent burnAction = buildBurnAction(source, songId);
        if (burnAction != null) {
            actions.append(new TextComponent(" "));
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
        return Messages.clickableCommand(new TranslatableComponent("musicplayer.burn.action_label"), new TranslatableComponent("musicplayer.burn.action_hover"), "/music burn song " + songId, ChatFormatting.LIGHT_PURPLE);
    }

    private static MutableComponent renderEntry(SearchEntry entry, MutableComponent action, Component titleHover, Component subtitleHover) {
        MutableComponent line = action.copy();
        line.append(new TextComponent(" "));
        line.append(clickableText(entry.title(), entry.titleCommand(), titleHover, ChatFormatting.AQUA));
        if (entry.hasSubtitle()) {
            line.append(new TextComponent(" - ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(clickableText(entry.subtitle(), entry.subtitleCommand(), subtitleHover, ChatFormatting.GRAY));
        }
        return line;
    }

    private static MutableComponent clickableText(String text, String command, Component hover, ChatFormatting color) {
        return clickableText(new TextComponent(text), command, hover, color);
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
        Messages.sendSuccess(source,  spacer());
    }

    private static void sendNavigation(CommandSourceStack source, int page, int totalPages, String commandPattern, boolean hasKnownNext, String suggestCommand) {
        MutableComponent nav = new TextComponent("");
        if (page > 1) {
            nav.append(Messages.clickableCommand(new TranslatableComponent("musicplayer.common.prev_label"), new TranslatableComponent("musicplayer.common.prev_hover"), String.format(commandPattern, page - 1), ChatFormatting.YELLOW));
            nav.append(new TextComponent(" "));
        }
        nav.append((hasKnownNext
                ? new TranslatableComponent("musicplayer.common.page_indicator", page, Math.max(page, totalPages))
                : new TranslatableComponent("musicplayer.common.page_indicator_single", page)).withStyle(ChatFormatting.DARK_GRAY));
        if (hasKnownNext && page < totalPages) {
            nav.append(new TextComponent(" "));
            nav.append(Messages.clickableCommand(new TranslatableComponent("musicplayer.common.next_label"), new TranslatableComponent("musicplayer.common.next_hover"), String.format(commandPattern, page + 1), ChatFormatting.YELLOW));
        }
        nav.append(new TextComponent(" "));
        nav.append(Messages.suggestable(new TranslatableComponent("musicplayer.common.jump_label"), new TranslatableComponent("musicplayer.common.jump_hover"), suggestCommand, ChatFormatting.GRAY));
        Messages.sendSuccess(source,  nav);
    }

    private static void burnHeldDisc(CommandSourceStack source, ServerPlayer player, TrackInfo track) {
        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!MusicDiscHelper.isBurnableDisc(mainHand)) {
            Messages.warning(source, new TranslatableComponent("musicplayer.burn.disc_changed"));
            return;
        }

        ItemStack burnBase = mainHand.copy();
        burnBase.setCount(1);
        ItemStack burnedDisc = MusicDiscHelper.burn(burnBase, track);
        if (mainHand.getCount() == 1) {
            player.setItemInHand(InteractionHand.MAIN_HAND, burnedDisc);
        } else {
            mainHand.shrink(1);
            if (!player.getInventory().add(burnedDisc)) {
                player.drop(burnedDisc, false, true);
            }
        }

        Messages.sendSuccess(source,  new TranslatableComponent("musicplayer.burn.done").withStyle(ChatFormatting.GREEN)
                .append(new TextComponent(track.title()).withStyle(ChatFormatting.AQUA))
                .append(new TextComponent(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(new TextComponent(track.artist()).withStyle(ChatFormatting.GRAY)));
    }

    /** 播放状态回显：在线管理员看到带操作按钮的回显；plainForOthers 非空时其他玩家看到纯状态文本。 */
    private static void broadcastState(CommandSourceStack source, MutableComponent adminEcho, Component plainForOthers) {
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (player.createCommandSourceStack().hasPermission(2)) {
                player.displayClientMessage(adminEcho.copy(), false);
            } else if (plainForOthers != null) {
                player.displayClientMessage(plainForOthers.copy(), false);
            }
        }
    }

    /** 极简状态回显：◆ 状态文本 + 操作按钮（点击后把命令填充到输入框）。 */
    private static MutableComponent controlEcho(String textKey, String labelKey, String hoverKey, String command, ChatFormatting color) {
        return new TextComponent("◆ ").withStyle(ChatFormatting.GOLD)
                .append(new TranslatableComponent(textKey).withStyle(ChatFormatting.GOLD))
                .append(new TextComponent("  "))
                .append(Messages.suggestable(
                        new TranslatableComponent(labelKey),
                        new TranslatableComponent(hoverKey),
                        command,
                        color));
    }

    @SafeVarargs
    private static void sendQuickBar(CommandSourceStack source, MutableComponent... actions) {
        MutableComponent line = new TextComponent("");
        boolean first = true;
        for (MutableComponent action : actions) {
            if (action == null) {
                continue;
            }
            if (!first) {
                line.append(new TextComponent(" · ").withStyle(ChatFormatting.DARK_GRAY));
            }
            line.append(action);
            first = false;
        }
        if (!first) {
            Messages.sendSuccess(source,  line);
        }
    }

    private static Component renderHeader() {
        return new TextComponent("━━━━━━━━━━━━━━━━━━━━━━ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(new TextComponent("♫").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent(" ━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component spacer() {
        return new TextComponent(" ").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static void sendHeader(CommandSourceStack source) {
        Messages.sendSuccess(source,  spacer());
        Messages.sendSuccess(source,  spacer());
        Messages.sendSuccess(source,  renderHeader());
        Messages.sendSuccess(source,  spacer());
    }

    private static MutableComponent sectionHeader(Component title, Component subtitle) {
        MutableComponent line = new TextComponent("◆ ").withStyle(ChatFormatting.GOLD)
                .append(title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (subtitle != null && !subtitle.getString().isBlank()) {
            line.append(new TextComponent("  ").withStyle(ChatFormatting.DARK_GRAY));
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
        return new TranslatableComponent(value ? "musicplayer.common.on" : "musicplayer.common.off");
    }

    private static String rootMessage(Throwable throwable) {
        return com.junhsiun.musicplayer.platform.url.UrlUtil.rootMessageKey(throwable);
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
