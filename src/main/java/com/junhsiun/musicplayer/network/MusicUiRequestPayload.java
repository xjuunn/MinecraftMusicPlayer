package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端 → 服务端：请求刷新底部播放状态 / 最近播放 / 配置快照。 */
public record MusicUiRequestPayload(String action) implements CustomPacketPayload {

    public static final Type<MusicUiRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "music_ui_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicUiRequestPayload> CODEC =
            CustomPacketPayload.codec(MusicUiRequestPayload::write, MusicUiRequestPayload::new);

    public MusicUiRequestPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.action == null ? "" : this.action);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static MusicUiRequestPayload all() {
        return new MusicUiRequestPayload("all");
    }

    public static MusicUiRequestPayload recent() {
        return new MusicUiRequestPayload("recent");
    }

    public static MusicUiRequestPayload queue() {
        return new MusicUiRequestPayload("queue");
    }
}
