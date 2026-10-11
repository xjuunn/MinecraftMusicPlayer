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
import java.util.stream.Collectors;

public final class MusicControlPayload {
    private final String action;
    private final String trackId;
    private final List<String> urls;
    private final String title;
    private final String subtitle;
    private final String message;
    private final long offsetMillis;

    public MusicControlPayload(String action, String trackId, List<String> urls, String title, String subtitle, String message, long offsetMillis) {
        this.action = action;
        this.trackId = trackId;
        this.urls = urls;
        this.title = title;
        this.subtitle = subtitle;
        this.message = message;
        this.offsetMillis = offsetMillis;
    }

    public String action() {
        return action;
    }

    public String trackId() {
        return trackId;
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

    public String message() {
        return message;
    }

    public long offsetMillis() {
        return offsetMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MusicControlPayload)) {
            return false;
        }
        MusicControlPayload that = (MusicControlPayload) o;
        return offsetMillis == that.offsetMillis
                && Objects.equals(action, that.action)
                && Objects.equals(trackId, that.trackId)
                && Objects.equals(urls, that.urls)
                && Objects.equals(title, that.title)
                && Objects.equals(subtitle, that.subtitle)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, trackId, urls, title, subtitle, message, offsetMillis);
    }

    @Override
    public String toString() {
        return "MusicControlPayload{action='" + action + "', trackId='" + trackId + "', urls=" + urls
                + ", title='" + title + "', subtitle='" + subtitle + "', message='" + message
                + "', offsetMillis=" + offsetMillis + "}";
    }

    public static final ResourceLocation ID = new ResourceLocation(MusicPlayerMod.MOD_ID, "music_control");

    public static MusicControlPayload decode(FriendlyByteBuf buffer) {
        return new MusicControlPayload(buffer.readUtf(), buffer.readUtf(), readUrls(buffer), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readLong());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.action);
        buffer.writeUtf(this.trackId);
        buffer.writeVarInt(this.urls.size());
        for (String url : this.urls) {
            buffer.writeUtf(url);
        }
        buffer.writeUtf(this.title);
        buffer.writeUtf(this.subtitle);
        buffer.writeUtf(this.message);
        buffer.writeLong(this.offsetMillis);
    }

    private static List<String> readUrls(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> urls = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            urls.add(buffer.readUtf());
        }
        return urls;
    }

    public static MusicControlPayload play(String trackId, List<String> urls, String title, String subtitle, long offsetMillis) {
        List<String> valid = urls.stream()
                .filter(url -> url != null && url.startsWith("http") && url.length() < 30000)
                .collect(Collectors.toList());
        if (valid.isEmpty() && !urls.isEmpty()) {
            MusicPlayerMod.LOGGER.warn("所有播放源 URL 均无效，trackId={}, urls={}", trackId, urls.size());
        }
        return new MusicControlPayload("play", trackId == null ? "" : trackId, valid, title, subtitle, "", offsetMillis);
    }

    public static MusicControlPayload stop(String message) {
        return new MusicControlPayload("stop", "", Java8.listOf(), "", "", message == null ? "" : message, 0L);
    }

    public static void send(ServerPlayer player, MusicControlPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.encode(buf);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
