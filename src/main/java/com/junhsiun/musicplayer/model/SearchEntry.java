package com.junhsiun.musicplayer.model;

import com.junhsiun.musicplayer.util.Java8;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

import java.util.Objects;

public final class SearchEntry {
    private final String id;
    private final String title;
    private final Component subtitle;
    private final String titleCommand;
    private final String subtitleCommand;

    public SearchEntry(String id, String title, Component subtitle, String titleCommand, String subtitleCommand) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.titleCommand = titleCommand;
        this.subtitleCommand = subtitleCommand;
    }

    public SearchEntry(String id, String title, Component subtitle) {
        this(id, title, subtitle, "", "");
    }

    public SearchEntry(String id, String title, String subtitle, String titleCommand, String subtitleCommand) {
        this(id, title, Java8.isBlank(subtitle) ? null : new TextComponent(subtitle), titleCommand, subtitleCommand);
    }

    public SearchEntry(String id, String title, String subtitle) {
        this(id, title, subtitle, "", "");
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Component subtitle() {
        return subtitle;
    }

    public String titleCommand() {
        return titleCommand;
    }

    public String subtitleCommand() {
        return subtitleCommand;
    }

    public boolean hasSubtitle() {
        return subtitle != null && Java8.isNotBlank(subtitle.getString());
    }

    public boolean hasTitleCommand() {
        return Java8.isNotBlank(titleCommand);
    }

    public boolean hasSubtitleCommand() {
        return Java8.isNotBlank(subtitleCommand);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchEntry)) {
            return false;
        }
        SearchEntry that = (SearchEntry) o;
        return Objects.equals(id, that.id)
                && Objects.equals(title, that.title)
                && Objects.equals(subtitle, that.subtitle)
                && Objects.equals(titleCommand, that.titleCommand)
                && Objects.equals(subtitleCommand, that.subtitleCommand);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, subtitle, titleCommand, subtitleCommand);
    }

    @Override
    public String toString() {
        return "SearchEntry{id='" + id + "', title='" + title + "', subtitle=" + subtitle
                + ", titleCommand='" + titleCommand + "', subtitleCommand='" + subtitleCommand + "'}";
    }
}
