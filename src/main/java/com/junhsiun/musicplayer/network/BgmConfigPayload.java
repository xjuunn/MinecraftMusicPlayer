package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BgmConfigPayload(String playlistId) implements CustomPacketPayload {
    public static final Type<BgmConfigPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "bgm_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BgmConfigPayload> CODEC =
            CustomPacketPayload.codec(BgmConfigPayload::write, BgmConfigPayload::new);

    public BgmConfigPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.playlistId == null ? "" : this.playlistId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
