package com.junhsiun.musicplayer.model;

public record SearchEntry(String id, String title, String subtitle, String titleCommand, String subtitleCommand) {
    public SearchEntry(String id, String title, String subtitle) {
        this(id, title, subtitle, "", "");
    }

    public boolean hasTitleCommand() {
        return titleCommand != null && !titleCommand.isBlank();
    }

    public boolean hasSubtitleCommand() {
        return subtitleCommand != null && !subtitleCommand.isBlank();
    }
}
