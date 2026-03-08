package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicPlaybackReportPayload(String action, String trackId, String message) implements CustomPacketPayload {
    public static final Type<MusicPlaybackReportPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "music_playback_report"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicPlaybackReportPayload> CODEC =
            CustomPacketPayload.codec(MusicPlaybackReportPayload::write, MusicPlaybackReportPayload::new);

    public MusicPlaybackReportPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.action);
        buffer.writeUtf(this.trackId);
        buffer.writeUtf(this.message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static MusicPlaybackReportPayload ended(String trackId) {
        return new MusicPlaybackReportPayload("ended", trackId == null ? "" : trackId, "");
    }

    public static MusicPlaybackReportPayload failed(String trackId, String message) {
        return new MusicPlaybackReportPayload("failed", trackId == null ? "" : trackId, message == null ? "" : message);
    }
}
