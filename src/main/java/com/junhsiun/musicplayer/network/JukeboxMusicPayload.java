package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record JukeboxMusicPayload(String action, long jukeboxPos, List<String> urls, String title, String subtitle, String coverUrl) implements CustomPacketPayload {
    public static final Type<JukeboxMusicPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "jukebox_music"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxMusicPayload> CODEC =
            CustomPacketPayload.codec(JukeboxMusicPayload::write, JukeboxMusicPayload::new);

    public JukeboxMusicPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readLong(), readUrls(buffer), buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.action);
        buffer.writeLong(this.jukeboxPos);
        buffer.writeVarInt(this.urls.size());
        for (String url : this.urls) {
            buffer.writeUtf(url);
        }
        buffer.writeUtf(this.title);
        buffer.writeUtf(this.subtitle);
        buffer.writeUtf(this.coverUrl);
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

    public static JukeboxMusicPayload play(long jukeboxPos, List<String> urls, String title, String subtitle, String coverUrl) {
        return new JukeboxMusicPayload("play", jukeboxPos, List.copyOf(urls), title == null ? "" : title, subtitle == null ? "" : subtitle, coverUrl == null ? "" : coverUrl);
    }

    public static JukeboxMusicPayload update(long jukeboxPos, String title, String subtitle, String coverUrl) {
        return new JukeboxMusicPayload("update", jukeboxPos, List.of(), title == null ? "" : title, subtitle == null ? "" : subtitle, coverUrl == null ? "" : coverUrl);
    }

    public static JukeboxMusicPayload refresh(long jukeboxPos, List<String> urls, String title, String subtitle, String coverUrl) {
        return new JukeboxMusicPayload("refresh", jukeboxPos, List.copyOf(urls), title == null ? "" : title, subtitle == null ? "" : subtitle, coverUrl == null ? "" : coverUrl);
    }

    public static JukeboxMusicPayload stop(long jukeboxPos) {
        return new JukeboxMusicPayload("stop", jukeboxPos, List.of(), "", "", "");
    }
}
