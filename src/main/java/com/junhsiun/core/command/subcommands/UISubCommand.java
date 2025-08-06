package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.utils.ModLogger;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class UISubCommand extends BaseSubCommand{
    @Override
    public LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal(this.getName()).executes(context -> {
            ModLogger.info("显示UI");
            return 1;
        });
    }

    @Override
    public String setDescription() {
        return "显示UI";
    }

    @Override
    public String setName() {
        return "ui";
    }
}
