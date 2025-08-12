package com.junhsiun.core.command;

import com.junhsiun.MusicPlayer;
import com.junhsiun.core.command.platform.MusicPlatformRegistry;
import com.junhsiun.core.command.platform.NeteasePlatform;
import com.junhsiun.core.command.subcommands.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;

public class MusicCommand {

    public void register() {
        MusicPlatformRegistry.register(new NeteasePlatform());

        CommandRegistrationCallback.EVENT.register((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
            ArrayList<BaseSubCommand> commandArrayList = new ArrayList<>();
            // 注册命令
            commandArrayList.add(new PlaySubCommand());
            commandArrayList.add(new SearchSubCommand());
            commandArrayList.add(new PlatformSubCommand());
            commandArrayList.add(new MuteSubCommand());
            commandArrayList.add(new ContinueSubCommand());
            commandArrayList.add(new StopSubCommand());
            commandArrayList.add(new JoinSubCommand());
            commandArrayList.add(new LeaveSubCommand());
            commandArrayList.add(new NextSubCommand());
            commandArrayList.add(new UISubCommand());
            commandArrayList.add(new ConfigSubCommand());
//            commandArrayList.add(new ViewSubCommand());
//            commandArrayList.add(new TestSubCommand());
//            commandArrayList.add(new HelpSubCommand());
            LiteralArgumentBuilder<ServerCommandSource> musicCmd = CommandManager.literal("music");
            commandArrayList.forEach(cmd -> {
                musicCmd.then(cmd.build());
            });
            musicCmd.then(CommandManager.literal("help").executes(context -> {
                ServerCommandSource source = context.getSource();

                source.sendFeedback(() -> Text.literal("🎵 Music 帮助菜单").formatted(Formatting.AQUA, Formatting.BOLD), false);
                source.sendFeedback(() -> Text.literal("-----------------------------").formatted(Formatting.GRAY), false);

                // 计算最长命令长度，用于对齐
                int maxLength = commandArrayList.stream()
                        .mapToInt(cmd -> cmd.getName().length())
                        .max().orElse(0);
                commandArrayList.forEach(cmd -> {
                    String paddedName = String.format("%-" + (maxLength + 2) + "s", cmd.getName());
                    Text line = Text.literal("  ")
                            .append(Text.literal(paddedName).formatted(Formatting.GOLD))
                            .append(Text.literal(cmd.getDescription()).formatted(Formatting.GRAY));

                    source.sendFeedback(() -> line, false);
                });

                source.sendFeedback(() -> Text.literal("-----------------------------").formatted(Formatting.DARK_GRAY), false);

                return 1;
            }));
            commandDispatcher.register(musicCmd);
        });
    }

}
