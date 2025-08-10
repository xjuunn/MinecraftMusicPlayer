package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.channel.MusicChannel;
import com.junhsiun.core.utils.ModLogger;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class StopSubCommand extends BaseSubCommand {

    @Override
    LiteralArgumentBuilder<ServerCommandSource> config(LiteralArgumentBuilder<ServerCommandSource> cmd) {
        cmd.requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(2))
                .executes(context -> {
                    ModLogger.info("停止播放");
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    String playerId = "-1";
                    if (player != null) {
                        playerId = String.valueOf(player.getId());
                    }
                    MusicChannel.broadcast(context.getSource().getServer(), playerId + " stop");
                    context.getSource().getServer().getPlayerManager().getPlayerList().forEach(player1 -> {
                        if (player != null) {
                            player1.sendMessage(Text.literal("").append(player.getName()).append(Text.literal(" 停止了播放")));
                        }
                    });
                    return 1;
                });
        return cmd;
    }

    @Override
    public String setDescription() {
        return "停止播放";
    }

    @Override
    public String setName() {
        return "stop";
    }
}
