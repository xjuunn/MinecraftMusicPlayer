package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenUrlPayload(String url) implements CustomPacketPayload {
    public static final Type<OpenUrlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "open_url"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenUrlPayload> CODEC =
            CustomPacketPayload.codec(OpenUrlPayload::write, OpenUrlPayload::new);

    public OpenUrlPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.url);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
