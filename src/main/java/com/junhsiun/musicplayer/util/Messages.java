package com.junhsiun.musicplayer.util;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;

public final class Messages {
    private Messages() {
    }

    public static final String NETEASE_SONG_URL = "https://music.163.com/#/song?id=";

    public static String sanitizeForLog(String input) {
        if (input == null) return "null";
        if (input.length() > 256) {
            input = input.substring(0, 253) + "...";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 32 && c != 127) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    public static String formatDuration(long durationMillis) {
        long totalSeconds = Math.max(0L, durationMillis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%d:%02d", minutes, seconds);
    }

    public static void sendChat(CommandSourceStack source, Component component) {
        if (source.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(component);
            return;
        }
        source.sendSuccess(() -> component, false);
    }

    public static void sendChatLines(CommandSourceStack source, Component component) {
        sendChat(source, component);
    }

    /**
     * 将字符串渲染为组件：若字符串形如翻译键（{@code musicplayer.xxx.yyy}）则按翻译解析，否则按普通文本解析。
     * 用于网络传输的失败原因、异常消息等既可能是键也可能是原始文本的场景。
     */
    public static Component textOrTranslatable(String text) {
        if (text != null && text.matches("musicplayer\\.[a-z0-9_]+(?:\\.[a-z0-9_]+)+")) {
            return Component.translatable(text);
        }
        return Component.literal(text == null ? "" : text);
    }

    public static void info(CommandSourceStack source, String text) {
        info(source, text, false);
    }

    public static void info(CommandSourceStack source, String text, boolean broadcastToOps) {
        info(source, Component.literal(text), broadcastToOps);
    }

    public static void info(CommandSourceStack source, Component component) {
        info(source, component, false);
    }

    public static void info(CommandSourceStack source, Component component, boolean broadcastToOps) {
        sendChat(source, component.copy().withStyle(ChatFormatting.GRAY));
    }

    public static void success(CommandSourceStack source, String text) {
        success(source, text, false);
    }

    public static void success(CommandSourceStack source, String text, boolean broadcastToOps) {
        success(source, Component.literal(text), broadcastToOps);
    }

    public static void success(CommandSourceStack source, Component component) {
        success(source, component, false);
    }

    public static void success(CommandSourceStack source, Component component, boolean broadcastToOps) {
        sendChat(source, component.copy().withStyle(ChatFormatting.GREEN));
    }

    public static void warning(CommandSourceStack source, String text) {
        warning(source, Component.literal(text));
    }

    public static void warning(CommandSourceStack source, Component component) {
        Component styled = component.copy().withStyle(ChatFormatting.RED);
        if (source.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(styled);
            return;
        }
        source.sendFailure(styled);
    }

    public static void loading(CommandSourceStack source, String text) {
        loading(source, Component.literal(text));
    }

    public static void loading(CommandSourceStack source, Component component) {
        sendChat(source, component.copy().withStyle(ChatFormatting.YELLOW));
    }

    public static void sendLine(CommandSourceStack source, Component component) {
        sendChat(source, component);
    }

    public static void sendFailurePlayer(CommandSourceStack source, Component component) {
        if (source.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(component);
            return;
        }
        source.sendFailure(component);
    }

    public static void sendFailure(CommandSourceStack source, Component component) {
        sendFailurePlayer(source, component);
    }

    public static void sendSuccess(CommandSourceStack source, Component component) {
        sendChat(source, component);
    }

    public static void sendSuccess(CommandSourceStack source, Component component, boolean broadcastToOps) {
        sendChat(source, component);
    }

    public static MutableComponent clickableCommand(String label, String hover, String command, ChatFormatting color) {
        return clickableCommand(Component.literal(label), Component.literal(hover), command, color);
    }

    public static MutableComponent clickableCommand(Component label, Component hover, String command, ChatFormatting color) {
        return label.copy().setStyle(
                Style.EMPTY.withColor(color)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(hover))
        );
    }

    public static MutableComponent clickableUrl(String label, String hover, String url, ChatFormatting color) {
        return clickableUrl(Component.literal(label), Component.literal(hover), url, color);
    }

    public static MutableComponent clickableUrl(Component label, Component hover, String url, ChatFormatting color) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return label.copy().withStyle(color);
        }
        try {
            return label.copy().setStyle(
                    Style.EMPTY.withColor(color)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                            .withHoverEvent(new HoverEvent.ShowText(hover))
            );
        } catch (Exception ignored) {
            return label.copy().withStyle(color);
        }
    }

    public static MutableComponent suggestable(String label, String hover, String command, ChatFormatting color) {
        return suggestable(Component.literal(label), Component.literal(hover), command, color);
    }

    public static MutableComponent suggestable(Component label, Component hover, String command, ChatFormatting color) {
        return label.copy().setStyle(
                Style.EMPTY.withColor(color)
                        .withClickEvent(new ClickEvent.SuggestCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(hover))
        );
    }
}
