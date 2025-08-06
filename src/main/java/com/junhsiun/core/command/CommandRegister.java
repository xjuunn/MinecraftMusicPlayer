package com.junhsiun.core.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.function.Function;

public class CommandRegister {
    public static void register(Function<ServerCommandSource, Integer> callback) {
        CommandRegistrationCallback.EVENT.register((commandDispatcher,
                                                    commandRegistryAccess,
                                                    registrationEnvironment) -> {
            commandDispatcher.register(CommandManager.literal("music").executes(
                    context -> callback.apply(context.getSource())));
        });
    }
}