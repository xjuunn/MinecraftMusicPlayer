package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.utils.ModLogger;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class HelpSubCommand extends BaseSubCommand {

    @Override
    LiteralArgumentBuilder<ServerCommandSource> config(LiteralArgumentBuilder<ServerCommandSource> cmd) {
        cmd.executes(context -> {
            ModLogger.info("显示帮助");
            return 1;
        });
        return cmd;
    }

    @Override
    public String setDescription() {
        return "显示帮助";
    }

    @Override
    public String setName() {
        return "help";
    }
}
