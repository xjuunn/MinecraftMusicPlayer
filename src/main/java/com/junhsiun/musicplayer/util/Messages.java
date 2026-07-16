package com.junhsiun.musicplayer.util;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

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

    public static void info(CommandSourceStack source, String text) {
        info(source, text, false);
    }

    public static void info(CommandSourceStack source, String text, boolean broadcastToOps) {
        source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), broadcastToOps);
    }

    public static void success(CommandSourceStack source, String text) {
        success(source, text, false);
    }

    public static void success(CommandSourceStack source, String text, boolean broadcastToOps) {
        source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GREEN), broadcastToOps);
    }

    public static void warning(CommandSourceStack source, String text) {
        source.sendFailure(Component.literal(text));
    }

    public static void loading(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.YELLOW), false);
    }

    public static MutableComponent clickableCommand(String label, String hover, String command, ChatFormatting color) {
        return Component.literal(label).setStyle(
                Style.EMPTY.withColor(color)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))
        );
    }

    public static MutableComponent clickableUrl(String label, String hover, String url, ChatFormatting color) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return Component.literal(label).withStyle(color);
        }
        try {
            return Component.literal(label).setStyle(
                    Style.EMPTY.withColor(color)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))
            );
        } catch (Exception ignored) {
            return Component.literal(label).withStyle(color);
        }
    }

    public static MutableComponent suggestable(String label, String hover, String command, ChatFormatting color) {
        return Component.literal(label).setStyle(
                Style.EMPTY.withColor(color)
                        .withClickEvent(new ClickEvent.SuggestCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))
        );
    }
}
