package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.channel.MusicChannel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class LeaveSubCommand extends BaseSubCommand {

    @Override
    LiteralArgumentBuilder<ServerCommandSource> config(LiteralArgumentBuilder<ServerCommandSource> cmd) {
        cmd.executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player != null) {
                MusicChannel.send(player, player.getId() + " leave");
                player.sendMessage(Text.literal("已退出播放").setStyle(Style.EMPTY.withColor(Formatting.GREEN)), true);
            }
            return 1;
        });
        return cmd;
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
