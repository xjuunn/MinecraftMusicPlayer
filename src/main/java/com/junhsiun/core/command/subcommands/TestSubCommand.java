package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.channel.MusicChannel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class TestSubCommand extends BaseSubCommand {
    @Override
    public String setDescription() {
        return "测试命令";
    }

    @Override
    public String setName() {
        return "test";
    }

    @Override
    LiteralArgumentBuilder<ServerCommandSource> config(LiteralArgumentBuilder<ServerCommandSource> cmd) {
        cmd.executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            MusicChannel.send(player, "test");
            return 1;
        });
        return cmd;
    }
}
