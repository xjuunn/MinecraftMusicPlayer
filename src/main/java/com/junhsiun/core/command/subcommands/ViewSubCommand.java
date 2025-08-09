package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.command.platform.BasePlatform;
import com.junhsiun.core.command.platform.IPlayList;
import com.junhsiun.core.command.platform.IUserPlayList;
import com.junhsiun.core.command.platform.MusicPlatformRegistry;
import com.junhsiun.core.command.subcommands.vo.PlayListVO;
import com.junhsiun.core.command.subcommands.vo.UserVO;
import com.junhsiun.core.utils.ModLogger;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.Normalizer;

public class ViewSubCommand extends BaseSubCommand {
    @Override
    LiteralArgumentBuilder<ServerCommandSource> config(LiteralArgumentBuilder<ServerCommandSource> cmd) {
        MusicPlatformRegistry.all().forEach(platform -> {
            setPlatformCommand(cmd, platform);
        });
        return cmd;
    }

    void setPlatformCommand(LiteralArgumentBuilder<ServerCommandSource> cmd, BasePlatform platform) {
        if (platform instanceof IPlayList platformPlayList) {
            cmd.then(CommandManager.literal("playlist").then(CommandManager.literal(platform.getName()).then(CommandManager.argument("id", StringArgumentType.string()).executes(context -> {
                String id = StringArgumentType.getString(context, "id");
                PlayListVO playListVO = platformPlayList.playListInfo(id);
                if (playListVO == null || playListVO.getSongsList() == null || playListVO.getSongsList().isEmpty()) {
                    context.getSource().sendFeedback(() -> Text.literal(""), false);
                    context.getSource().sendFeedback(() -> Text.literal("  未搜索到相关歌单"), false);
                    context.getSource().sendFeedback(() -> Text.literal(""), false);
                    return 1;
                }
                context.getSource().sendFeedback(() -> Text.literal(""), false);
                context.getSource().sendFeedback(() -> Text.literal("  歌单：" + playListVO.getName() + "   ").setStyle(Style.EMPTY.withBold(true))
                        .append(Text.literal("创建者" + playListVO.getUsername()).setStyle(Style.EMPTY.withColor(Formatting.GRAY).withBold(false).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("查看用户")))
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/music view playlist " + platform.getName() + " \"" + playListVO.getUserId() + "\"")))), false);
                context.getSource().sendFeedback(() -> Text.literal(""), false);
                playListVO.getSongsList().forEach(song -> {
                    context.getSource().sendFeedback(() -> Text.literal("  " + song.getName())
                            .append(Text.literal(" - " + song.getDescribe()).setStyle(Style.EMPTY.withColor(Formatting.GRAY)
                            )).setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("播放")))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/music play " + platform.getName() + " " + song.getId()))), false);
                });
                context.getSource().sendFeedback(() -> Text.literal(""), false);
                return 1;
            }))));
        }

        if (platform instanceof IUserPlayList userPlayList) {
            cmd.then(CommandManager.literal("user").then(CommandManager.literal(platform.getName()).then(CommandManager.argument("id", StringArgumentType.string()).executes(context -> {
                String id = StringArgumentType.getString(context, "id");
                UserVO userVO = userPlayList.userPlayList(id);
                if(userVO == null || userVO.getPlaylist() == null || userVO.getPlaylist().isEmpty()) {
                    context.getSource().sendFeedback(() -> Text.literal(""), false);
                    context.getSource().sendFeedback(() -> Text.literal("  未搜索到相关用户"), false);
                    context.getSource().sendFeedback(() -> Text.literal(""), false);
                    return 1;
                }
                context.getSource().sendFeedback(() -> Text.literal(""), false);
                context.getSource().sendFeedback(() -> Text.literal("  用户：" + userVO.getName() + "   ").setStyle(Style.EMPTY.withBold(true)), false);
                context.getSource().sendFeedback(() -> Text.literal("   " + userVO.getSignature()).setStyle(Style.EMPTY.withColor(Formatting.GRAY)), false);
                context.getSource().sendFeedback(() -> Text.literal(""), false);
                userVO.getPlaylist().forEach(playlist -> {
                    context.getSource().sendFeedback(() -> Text.literal("  " + playlist.getName())
                            .append(Text.literal(" - " + playlist.getDescribe()).setStyle(Style.EMPTY.withColor(Formatting.GRAY)
                            )).setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("查看")))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/music view playlist " + platform.getName() + " " + playlist.getId()))), false);
                });
                context.getSource().sendFeedback(() -> Text.literal(""), false);
                return 1;
            }))));
        }

    }

    @Override
    public String setDescription() {
        return "查看";
    }

    @Override
    public String setName() {
        return "view";
    }
}
