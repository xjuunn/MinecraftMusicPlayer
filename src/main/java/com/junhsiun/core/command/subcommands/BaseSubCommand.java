package com.junhsiun.core.command.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;

public abstract class BaseSubCommand implements ISubCommand {
    private String name;
    private String description;
    public BaseSubCommand() {
        this.description = this.setDescription();
        this.name = this.setName();
    }

    public String getDescription() {
        return description;
    }

    public abstract String setDescription();

    public String getName() {
        return name;
    }

    public abstract String setName();
}
