package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.utils.ModLogger;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class MuteSubCommand extends BaseSubCommand{
    @Override
    public LiteralArgumentBuilder<ServerCommandSource> build() {
        LiteralArgumentBuilder<ServerCommandSource> muteCmd = CommandManager.literal(this.getName());
        muteCmd.then(CommandManager.literal("once").executes(context -> {
            ModLogger.info("静音一次");
            return 1;
        }));
        muteCmd.then(CommandManager.literal("true").executes(context -> {
            ModLogger.info("静音");
            return 1;
        }));
        muteCmd.then(CommandManager.literal("false").executes(context -> {
            ModLogger.info("取消静音");
            return 1;
        }));

        return muteCmd;
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
