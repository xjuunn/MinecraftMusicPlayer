package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.utils.ModLogger;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class NextSubCommand extends BaseSubCommand {
    @Override
    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal(this.getName()).executes(context -> {
            ModLogger.info("下一首");
            return 1;
        });
    }

    @Override
    public String setDescription() {
        return "下一首";
    }

    @Override
    public String setName() {
        return "next";
    }
}
