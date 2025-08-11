package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.channel.MusicChannel;
import com.junhsiun.core.command.platform.MusicPlatformRegistry;
import com.junhsiun.core.utils.ModLogger;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.atomic.AtomicBoolean;

public class PlaySubCommand extends BaseSubCommand {

    @Override
    LiteralArgumentBuilder<ServerCommandSource> config(LiteralArgumentBuilder<ServerCommandSource> cmd) {
        MusicPlatformRegistry.all().forEach(platform -> {
            cmd.then(CommandManager.literal(platform.getName())
                    .then(CommandManager.argument("music_id", StringArgumentType.word()).executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        Text playerName;
                        if (player != null) {
                            playerName = player.getName();
                        } else {
                            playerName = Text.literal("未知玩家");
                        }

                        String musicId = StringArgumentType.getString(context, "music_id");
                        AtomicBoolean called = new AtomicBoolean(false);
                        platform.getMusicUrl(musicId, musicUrl -> {
                            if (called.compareAndSet(false, true)) {
                                if (musicUrl == null) {
                                    context.getSource().sendFeedback(() -> Text.literal("点歌失败，音乐没找到URL").setStyle(Style.EMPTY.withColor(Formatting.RED)), false);
                                    return;
                                }
                                platform.getMusicInfo(musicId, musicInfo -> {
                                    musicInfo.setUrl(musicUrl);
                                    String playerId = "-1";
                                    if (player != null) {
                                        playerId = String.valueOf(player.getId());
                                    }
                                    MusicChannel.broadcast(context.getSource().getServer(), playerId + " play " + musicUrl);
                                    context.getSource().getServer().getPlayerManager().getPlayerList().forEach(player1 -> {
                                        player1.sendMessage(Text.literal("").append(playerName)
                                                .append(" 播放了音乐").setStyle(Style.EMPTY.withBold(false).withColor(Formatting.WHITE))
                                                .append(Text.literal("[" + musicInfo.getName() + "]").setStyle(Style.EMPTY.withColor(Formatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, musicInfo.getUrl()))
                                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击下载"))))));
                                    });
                                });
                            }
                        });
                        return 1;

                    }))
            );
        });
        return cmd;
    }

    @Override
    public String setDescription() {
        return "播放";
    }

    @Override
    public String setName() {
        return "play";
    }
}
