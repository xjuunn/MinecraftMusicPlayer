package com.junhsiun.core.command.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;

public interface ISubCommand {
    LiteralArgumentBuilder<ServerCommandSource> build();
}
