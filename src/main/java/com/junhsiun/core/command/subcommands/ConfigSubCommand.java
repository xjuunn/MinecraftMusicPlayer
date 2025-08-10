package com.junhsiun.core.command.subcommands;

import com.junhsiun.core.config.ServerConfigManager;
import com.junhsiun.core.utils.OkHttpUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class ConfigSubCommand extends BaseSubCommand {
    @Override
    LiteralArgumentBuilder<ServerCommandSource> config(LiteralArgumentBuilder<ServerCommandSource> cmd) {
        LiteralArgumentBuilder<ServerCommandSource> setCmd = CommandManager.literal("set");
        setProxyConfig(setCmd);
        cmd.then(setCmd);
        LiteralArgumentBuilder<ServerCommandSource> getCmd = CommandManager.literal("get");
        getProxyConfig(getCmd);
        cmd.then(getCmd);
        return cmd;
    }

    private void getProxyConfig(LiteralArgumentBuilder<ServerCommandSource> cmdGet) {
        cmdGet.then(CommandManager.literal("proxy").executes(context -> {
            context.getSource().sendFeedback(() -> Text.literal("Proxy:" + OkHttpUtil.getProxy()), false);
            return 1;
        }));
    }

    private void setProxyConfig(LiteralArgumentBuilder<ServerCommandSource> cmdSet) {
        cmdSet.then(CommandManager.literal("proxy").then(CommandManager.argument("IP", StringArgumentType.string()).then(CommandManager.argument("port", StringArgumentType.string()).executes(context -> {
            String ip = StringArgumentType.getString(context, "IP");
            int port = Integer.parseInt(StringArgumentType.getString(context, "port"));
            ServerConfigManager.getConfig().proxy = ip + ":" + port;
            ServerConfigManager.saveConfig();
            OkHttpUtil.setProxy(ip, port);
            context.getSource().sendFeedback(() -> Text.literal("Proxy:" + ServerConfigManager.getConfig().proxy), false);
            return 1;
        }))));
    }

    @Override
    public String setDescription() {
        return "配置";
    }

    @Override
    public String setName() {
        return "config";
    }
}
