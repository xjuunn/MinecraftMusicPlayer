package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端 → 服务端：修改一个服务端配置项。仅管理员生效。 */
public record MusicConfigUpdatePayload(String key, String value) implements CustomPacketPayload {

    public static final Type<MusicConfigUpdatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "music_config_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicConfigUpdatePayload> CODEC =
            CustomPacketPayload.codec(MusicConfigUpdatePayload::write, MusicConfigUpdatePayload::new);

    public MusicConfigUpdatePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.key == null ? "" : this.key);
        buffer.writeUtf(this.value == null ? "" : this.value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
