package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.command.platform.IMusicPlatform;
import com.junhsiun.core.command.platform.MusicPlatformRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

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
        cmd.then(CommandManager.literal("song").then(CommandManager.argument("keyword", StringArgumentType.word()).executes(context -> {
            String keyword = StringArgumentType.getString(context, "keyword");
            platform.searchSong(keyword);
            return 1;
        })));

        cmd.then(CommandManager.literal("playlist").then(CommandManager.argument("keyword", StringArgumentType.word()).executes(context -> {
            String keyword = StringArgumentType.getString(context, "keyword");
            platform.searchPlayList(keyword);
            return 1;
        })));

        cmd.then(CommandManager.literal("user").then(CommandManager.argument("keyword", StringArgumentType.word()).executes(context -> {
            String keyword = StringArgumentType.getString(context, "keyword");
            platform.searchUser(keyword);
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
