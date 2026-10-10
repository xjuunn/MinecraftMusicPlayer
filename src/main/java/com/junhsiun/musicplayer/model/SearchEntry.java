package com.junhsiun.musicplayer.model;

import net.minecraft.network.chat.Component;

public record SearchEntry(String id, String title, Component subtitle, String titleCommand, String subtitleCommand) {
    public SearchEntry(String id, String title, Component subtitle) {
        this(id, title, subtitle, "", "");
    }

    public SearchEntry(String id, String title, String subtitle, String titleCommand, String subtitleCommand) {
        this(id, title, subtitle == null || subtitle.isBlank() ? null : Component.literal(subtitle), titleCommand, subtitleCommand);
    }

    public SearchEntry(String id, String title, String subtitle) {
        this(id, title, subtitle, "", "");
    }

    public boolean hasSubtitle() {
        return subtitle != null && !subtitle.getString().isBlank();
    }

    public boolean hasTitleCommand() {
        return titleCommand != null && !titleCommand.isBlank();
    }

    public boolean hasSubtitleCommand() {
        return subtitleCommand != null && !subtitleCommand.isBlank();
    }
}
