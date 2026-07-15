package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record MusicControlPayload(String action, String trackId, List<String> urls, String title, String subtitle, String message, long offsetMillis) implements CustomPacketPayload {
    public static final Type<MusicControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "music_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicControlPayload> CODEC =
            CustomPacketPayload.codec(MusicControlPayload::write, MusicControlPayload::new);

    public MusicControlPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUtf(), readUrls(buffer), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readLong());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
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

    private static List<String> readUrls(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> urls = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            urls.add(buffer.readUtf());
        }
        return urls;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static MusicControlPayload play(String trackId, List<String> urls, String title, String subtitle, long offsetMillis) {
        List<String> valid = urls.stream()
                .filter(url -> url != null && url.startsWith("http") && url.length() < 30000)
                .toList();
        if (valid.isEmpty() && !urls.isEmpty()) {
            MusicPlayerMod.LOGGER.warn("所有播放源 URL 均无效，trackId={}, urls={}", trackId, urls.size());
        }
        return new MusicControlPayload("play", trackId == null ? "" : trackId, valid, title, subtitle, "", offsetMillis);
    }

    public static MusicControlPayload stop(String message) {
        return new MusicControlPayload("stop", "", List.of(), "", "", message == null ? "" : message, 0L);
    }
}
