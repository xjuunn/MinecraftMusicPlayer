package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：底部播放栏需要的实时状态。
 *
 * <p>只发"状态快照"，不发进度心跳：客户端拿到 {@code elapsedMillis} 后自己按真实时间推进，
 * 服务端每两秒左右再校准一次即可，避免每秒多轮广播。</p>
 */
public record MusicUiStatePayload(boolean playing, boolean paused, String trackId, String title,
                                  String artist, String coverUrl, long elapsedMillis,
                                  long durationMillis, String requester, String playOrder,
                                  int queueSize, int playlistRemaining) implements CustomPacketPayload {

    public static final Type<MusicUiStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "music_ui_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicUiStatePayload> CODEC =
            CustomPacketPayload.codec(MusicUiStatePayload::write, MusicUiStatePayload::new);

    public MusicUiStatePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(), buffer.readUtf(),
                buffer.readUtf(), buffer.readUtf(), buffer.readLong(), buffer.readLong(),
                buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.playing);
        buffer.writeBoolean(this.paused);
        buffer.writeUtf(this.trackId == null ? "" : this.trackId);
        buffer.writeUtf(this.title == null ? "" : this.title);
        buffer.writeUtf(this.artist == null ? "" : this.artist);
        buffer.writeUtf(this.coverUrl == null ? "" : this.coverUrl);
        buffer.writeLong(this.elapsedMillis);
        buffer.writeLong(this.durationMillis);
        buffer.writeUtf(this.requester == null ? "" : this.requester);
        buffer.writeUtf(this.playOrder == null ? "" : this.playOrder);
        buffer.writeVarInt(this.queueSize);
        buffer.writeVarInt(this.playlistRemaining);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
