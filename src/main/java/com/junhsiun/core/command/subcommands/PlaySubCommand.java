package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.command.platform.MusicPlatformRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class PlaySubCommand extends BaseSubCommand {

    @Override
    LiteralArgumentBuilder<ServerCommandSource> config(LiteralArgumentBuilder<ServerCommandSource> cmd) {
        MusicPlatformRegistry.all().forEach(platform -> {
            String name = platform.getName();
            cmd.then(CommandManager.literal(name)
                    .then(CommandManager.argument("music_id", StringArgumentType.word()).executes(context -> {
                        String musicId = StringArgumentType.getString(context, "music_id");
                        platform.play(musicId);
                        return 1;
                    }))
            );
        });
        return cmd;
    }

    @Override
    public String setDescription() {
        return "播放";
    }

    @Override
    public String setName() {
        return "play";
    }
}
