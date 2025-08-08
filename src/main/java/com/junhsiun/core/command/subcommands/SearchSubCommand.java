package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.command.platform.IMusicPlatform;
import com.junhsiun.core.command.platform.MusicPlatformRegistry;
import com.junhsiun.core.command.subcommands.vo.SearchVO;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.ArrayList;

public class SearchSubCommand extends BaseSubCommand {
    @Override
    LiteralArgumentBuilder<ServerCommandSource> config(LiteralArgumentBuilder<ServerCommandSource> cmd) {
        MusicPlatformRegistry.all().forEach(platform -> {
            LiteralArgumentBuilder<ServerCommandSource> searchCmd = CommandManager.literal(platform.getName());
            setSubCommand(searchCmd, platform);
            cmd.then(searchCmd);

        });
        return cmd;
    }

    private void setSubCommand(LiteralArgumentBuilder<ServerCommandSource> cmd, IMusicPlatform platform) {
        cmd.then(CommandManager.literal("song").then(CommandManager.argument("keyword", StringArgumentType.string()).executes(context -> {
            String keyword = StringArgumentType.getString(context, "keyword");
            ArrayList<SearchVO> searchVOS = platform.searchSong(keyword);
            if (searchVOS.isEmpty()) {
                context.getSource().sendFeedback(() -> Text.literal("  未搜索到相关音乐"), false);
                return 1;
            }
            context.getSource().sendFeedback(() -> Text.literal(""), false);
            context.getSource().sendFeedback(() -> Text.literal("  音乐搜索结果").setStyle(Style.EMPTY.withBold(true)), false);
            context.getSource().sendFeedback(() -> Text.literal(""), false);
            searchVOS.forEach(song -> {
                context.getSource().sendFeedback(() -> {
                    MutableText txt = Text.literal("  " + song.getName()).setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/music play " + platform.getName() + " " + song.getId()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击播放"))));
                    MutableText describe = Text.literal(" - " + song.getDescribe()).setStyle(Style.EMPTY.withColor(Formatting.GRAY));
                    txt.append(describe);
                    return txt;
                }, false);
            });
            context.getSource().sendFeedback(() -> Text.literal(""), false);
            return 1;
        })));

        cmd.then(CommandManager.literal("playlist").then(CommandManager.argument("keyword", StringArgumentType.string()).executes(context -> {
            String keyword = StringArgumentType.getString(context, "keyword");
            ArrayList<SearchVO> searchVOS = platform.searchPlayList(keyword);
            if (searchVOS.isEmpty()) {
                context.getSource().sendFeedback(() -> Text.literal("  未搜索到相关歌单"), false);
                return 1;
            }
            context.getSource().sendFeedback(() -> Text.literal(""), false);
            context.getSource().sendFeedback(() -> Text.literal("  歌单搜索结果").setStyle(Style.EMPTY.withBold(true)), false);
            context.getSource().sendFeedback(() -> Text.literal(""), false);
            searchVOS.forEach(songList -> {
                context.getSource().sendFeedback(() -> {
                    MutableText txt = Text.literal("  " + songList.getName()).setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/music view playlist " + platform.getName() + " \"" + songList.getId() + "\""))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("查看歌单"))));
                    MutableText describe = Text.literal(" - " + songList.getDescribe()).setStyle(Style.EMPTY.withColor(Formatting.GRAY));
                    txt.append(describe);
                    return txt;
                }, false);
            });
            context.getSource().sendFeedback(() -> Text.literal(""), false);
            return 1;
        })));

        cmd.then(CommandManager.literal("user").then(CommandManager.argument("keyword", StringArgumentType.string()).executes(context -> {
            String keyword = StringArgumentType.getString(context, "keyword");
            ArrayList<SearchVO> searchVOS = platform.searchUser(keyword);
            if (searchVOS.isEmpty()) {
                context.getSource().sendFeedback(() -> Text.literal("  未搜索到相关用户"), false);
                return 1;
            }
            context.getSource().sendFeedback(() -> Text.literal(""), false);
            context.getSource().sendFeedback(() -> Text.literal("  用户搜索结果").setStyle(Style.EMPTY.withBold(true)), false);
            context.getSource().sendFeedback(() -> Text.literal(""), false);
            searchVOS.forEach(user -> {
                context.getSource().sendFeedback(() -> {
                    MutableText txt = Text.literal("  " + user.getName()).setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/music view user " + platform.getName() + " " + user.getId()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("查看用户"))));
                    if (user.getDescribe() != "") {
                        MutableText describe = Text.literal(" - " + user.getDescribe()).setStyle(Style.EMPTY.withColor(Formatting.GRAY));
                        txt.append(describe);
                    }
                    return txt;
                }, false);
            });
            context.getSource().sendFeedback(() -> Text.literal(""), false);
            return 1;
        })));


    }

    @Override
    public String setDescription() {
        return "搜索";
    }

    @Override
    public String setName() {
        return "search";
    }
}
