package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.utils.ModLogger;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class LeaveSubCommand extends BaseSubCommand {
    @Override
    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal(getName()).executes(context -> {
            ModLogger.info("离开");
            return 1;
        });
    }

    @Override
    public String setDescription() {
        return "离开";
    }

    @Override
    public String setName() {
        return "leave";
    }
}
