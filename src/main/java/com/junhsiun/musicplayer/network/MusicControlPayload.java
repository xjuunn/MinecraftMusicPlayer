package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicControlPayload(String action, String url, String title, String subtitle, String message) implements CustomPacketPayload {
    public static final Type<MusicControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "music_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicControlPayload> CODEC =
            CustomPacketPayload.codec(MusicControlPayload::write, MusicControlPayload::new);

    public MusicControlPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.action);
        buffer.writeUtf(this.url);
        buffer.writeUtf(this.title);
        buffer.writeUtf(this.subtitle);
        buffer.writeUtf(this.message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static MusicControlPayload play(String url, String title, String subtitle) {
        return new MusicControlPayload("play", url, title, subtitle, "");
    }

    public static MusicControlPayload stop(String message) {
        return new MusicControlPayload("stop", "", "", "", message == null ? "" : message);
    }
}
