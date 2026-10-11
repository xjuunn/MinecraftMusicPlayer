package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.util.Java8;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JukeboxMusicPayload {
    private final String action;
    private final long jukeboxPos;
    private final List<String> urls;
    private final String title;
    private final String subtitle;
    private final String coverUrl;
    private final long offsetMillis;
    private final String trackId;

    public JukeboxMusicPayload(String action, long jukeboxPos, List<String> urls, String title, String subtitle, String coverUrl, long offsetMillis, String trackId) {
        this.action = action;
        this.jukeboxPos = jukeboxPos;
        this.urls = urls;
        this.title = title;
        this.subtitle = subtitle;
        this.coverUrl = coverUrl;
        this.offsetMillis = offsetMillis;
        this.trackId = trackId;
    }

    public String action() {
        return action;
    }

    public long jukeboxPos() {
        return jukeboxPos;
    }

    public List<String> urls() {
        return urls;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public String coverUrl() {
        return coverUrl;
    }

    public long offsetMillis() {
        return offsetMillis;
    }

    public String trackId() {
        return trackId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JukeboxMusicPayload)) {
            return false;
        }
        JukeboxMusicPayload that = (JukeboxMusicPayload) o;
        return jukeboxPos == that.jukeboxPos
                && offsetMillis == that.offsetMillis
                && Objects.equals(action, that.action)
                && Objects.equals(urls, that.urls)
                && Objects.equals(title, that.title)
                && Objects.equals(subtitle, that.subtitle)
                && Objects.equals(coverUrl, that.coverUrl)
                && Objects.equals(trackId, that.trackId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, jukeboxPos, urls, title, subtitle, coverUrl, offsetMillis, trackId);
    }

    @Override
    public String toString() {
        return "JukeboxMusicPayload{action='" + action + "', jukeboxPos=" + jukeboxPos + ", urls=" + urls
                + ", title='" + title + "', subtitle='" + subtitle + "', coverUrl='" + coverUrl
                + "', offsetMillis=" + offsetMillis + ", trackId='" + trackId + "'}";
    }

    public static final ResourceLocation ID = new ResourceLocation(MusicPlayerMod.MOD_ID, "jukebox_music");

    public static JukeboxMusicPayload decode(FriendlyByteBuf buffer) {
        return new JukeboxMusicPayload(buffer.readUtf(), buffer.readLong(), readUrls(buffer), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readLong(), buffer.readUtf());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.action);
        buffer.writeLong(this.jukeboxPos);
        buffer.writeVarInt(this.urls.size());
        for (String url : this.urls) {
            buffer.writeUtf(url);
        }
        buffer.writeUtf(this.title);
        buffer.writeUtf(this.subtitle);
        buffer.writeUtf(this.coverUrl);
        buffer.writeLong(this.offsetMillis);
        buffer.writeUtf(this.trackId);
    }

    private static List<String> readUrls(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> urls = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            urls.add(buffer.readUtf());
        }
        return urls;
    }

    public static JukeboxMusicPayload play(long jukeboxPos, List<String> urls, String title, String subtitle, String coverUrl, long offsetMillis, String trackId) {
        return new JukeboxMusicPayload("play", jukeboxPos, Java8.copyOf(urls), title == null ? "" : title, subtitle == null ? "" : subtitle, coverUrl == null ? "" : coverUrl, offsetMillis, trackId == null ? "" : trackId);
    }

    public static JukeboxMusicPayload update(long jukeboxPos, String title, String subtitle, String coverUrl, String trackId) {
        return new JukeboxMusicPayload("update", jukeboxPos, Java8.listOf(), title == null ? "" : title, subtitle == null ? "" : subtitle, coverUrl == null ? "" : coverUrl, 0L, trackId == null ? "" : trackId);
    }

    public static JukeboxMusicPayload refresh(long jukeboxPos, List<String> urls, String title, String subtitle, String coverUrl, long offsetMillis, String trackId) {
        return new JukeboxMusicPayload("refresh", jukeboxPos, Java8.copyOf(urls), title == null ? "" : title, subtitle == null ? "" : subtitle, coverUrl == null ? "" : coverUrl, offsetMillis, trackId == null ? "" : trackId);
    }

    public static JukeboxMusicPayload stop(long jukeboxPos) {
        return new JukeboxMusicPayload("stop", jukeboxPos, Java8.listOf(), "", "", "", 0L, "");
    }

    public static void send(ServerPlayer player, JukeboxMusicPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.encode(buf);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
