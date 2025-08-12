package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.channel.MusicChannel;
import com.junhsiun.core.utils.ModLogger;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class MuteSubCommand extends BaseSubCommand {

    @Override
    LiteralArgumentBuilder<ServerCommandSource> config(LiteralArgumentBuilder<ServerCommandSource> cmd) {
        cmd.then(CommandManager.literal("once").executes(context -> {
            ModLogger.info("静音一次");
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player != null) {
                MusicChannel.send(player, player.getId() + " muteOnce");
            }
            return 1;
        }));
//        cmd.then(CommandManager.literal("true").executes(context -> {
//            ModLogger.info("静音");
//            return 1;
//        }));
//        cmd.then(CommandManager.literal("false").executes(context -> {
//            ModLogger.info("取消静音");
//            return 1;
//        }));
        return cmd;
    }

    @Override
    public String setDescription() {
        return "静音";
    }

    @Override
    public String setName() {
        return "mute";
    }
}
