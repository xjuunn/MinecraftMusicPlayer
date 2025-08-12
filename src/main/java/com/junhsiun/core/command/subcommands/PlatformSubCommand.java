package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.command.platform.BasePlatform;
import com.junhsiun.core.command.platform.ILoginable;
import com.junhsiun.core.command.platform.MusicPlatformRegistry;
import com.junhsiun.core.config.ServerConfigManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class PlatformSubCommand extends BaseSubCommand {

    @Override
    LiteralArgumentBuilder<ServerCommandSource> config(LiteralArgumentBuilder<ServerCommandSource> cmd) {
        cmd.requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(4));
        MusicPlatformRegistry.all().forEach(platform -> {
            LiteralArgumentBuilder<ServerCommandSource> platformCmd = CommandManager.literal(platform.getName());
            setSubCommand(platformCmd, platform);
            cmd.then(platformCmd);
        });
        return cmd;
    }

    private void setSubCommand(LiteralArgumentBuilder<ServerCommandSource> cmd, BasePlatform platform) {
        if (platform instanceof ILoginable) {
            cmd.then(CommandManager.literal("login").executes(context -> {
                ((ILoginable) platform).login();
                return 1;
            }));
            cmd.then(CommandManager.literal("logout").executes(context -> {
                ((ILoginable) platform).logout();
                return 1;
            }));
        }

        cmd.then(CommandManager.literal("set").then(CommandManager.argument("baseurl", StringArgumentType.string()).executes(context -> {
            String baseurl = StringArgumentType.getString(context, "baseurl");
            platform.setBaseUrl(baseurl);
            context.getSource().sendFeedback(() -> Text.literal("Baseurl:" + platform.getBaseUrl()), false);
            return 1;
        })));

        cmd.then(CommandManager.literal("get").then(CommandManager.literal("baseurl").executes(context -> {
            context.getSource().sendFeedback(() -> Text.literal("Baseurl:" + platform.getBaseUrl()), false);
            return 1;
        })));


    }

    @Override
    public String setDescription() {
        return "播放平台";
    }

    @Override
    public String setName() {
        return "platform";
    }
}
