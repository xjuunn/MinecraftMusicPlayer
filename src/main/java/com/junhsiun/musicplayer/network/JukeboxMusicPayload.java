package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public record JukeboxMusicPayload(String action, long jukeboxPos, List<String> urls, String title, String subtitle, String coverUrl, long offsetMillis, String trackId) {
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
        if (size < 0 || size > 16) {
            throw new IllegalArgumentException("Invalid url list size: " + size);
        }
        List<String> urls = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            urls.add(buffer.readUtf());
        }
        return urls;
    }

    private static List<String> validUrls(List<String> urls) {
        if (urls == null) {
            return List.of();
        }
        return urls.stream()
                .filter(url -> url != null && url.startsWith("http") && url.length() < 30000)
                .toList();
    }

    public static JukeboxMusicPayload play(long jukeboxPos, List<String> urls, String title, String subtitle, String coverUrl, long offsetMillis, String trackId) {
        return new JukeboxMusicPayload("play", jukeboxPos, validUrls(urls), title == null ? "" : title, subtitle == null ? "" : subtitle, coverUrl == null ? "" : coverUrl, offsetMillis, trackId == null ? "" : trackId);
    }

    public static JukeboxMusicPayload update(long jukeboxPos, String title, String subtitle, String coverUrl, String trackId) {
        return new JukeboxMusicPayload("update", jukeboxPos, List.of(), title == null ? "" : title, subtitle == null ? "" : subtitle, coverUrl == null ? "" : coverUrl, 0L, trackId == null ? "" : trackId);
    }

    public static JukeboxMusicPayload refresh(long jukeboxPos, List<String> urls, String title, String subtitle, String coverUrl, long offsetMillis, String trackId) {
        return new JukeboxMusicPayload("refresh", jukeboxPos, validUrls(urls), title == null ? "" : title, subtitle == null ? "" : subtitle, coverUrl == null ? "" : coverUrl, offsetMillis, trackId == null ? "" : trackId);
    }

    public static JukeboxMusicPayload stop(long jukeboxPos) {
        return new JukeboxMusicPayload("stop", jukeboxPos, List.of(), "", "", "", 0L, "");
    }

    public static void send(ServerPlayer player, JukeboxMusicPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.encode(buf);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
