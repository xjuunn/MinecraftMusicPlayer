package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：把指定歌曲刻录到手上的唱片母版。
 *
 * <p>只带歌曲 ID。曲目详情（标题、作者、音源直链）由服务端解析，和
 * {@code /music burn song} 走同一条链路，避免客户端伪造唱片内容。</p>
 */
public record DiscMasterBurnPayload(String songId) implements CustomPacketPayload {
    public static final Type<DiscMasterBurnPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "disc_master_burn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiscMasterBurnPayload> CODEC =
            CustomPacketPayload.codec(DiscMasterBurnPayload::write, DiscMasterBurnPayload::new);

    public DiscMasterBurnPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.songId == null ? "" : this.songId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
