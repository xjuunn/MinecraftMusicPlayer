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

    public static MusicPlaybackReportPayload position(long positionMillis, String trackId) {
        return new MusicPlaybackReportPayload("position", trackId == null ? "" : trackId, Long.toString(positionMillis));
    }

    public static MusicPlaybackReportPayload jukeboxEnded(String trackId) {
        return new MusicPlaybackReportPayload("jukebox_ended", trackId == null ? "" : trackId, "");
    }

    /**
     * 客户端<b>真正开始出声</b>的时刻。
     *
     * <p>服务端一发出 {@code play} 就开始计时，但客户端还要把整首 MP3 下载完才开始播；
     * 下载期间歌词会提前跑。这条报文让服务端知道"现在才真正从第 0 毫秒开始"，
     * 在此之前不推进歌词。</p>
     */
    public static MusicPlaybackReportPayload started(String trackId) {
        return new MusicPlaybackReportPayload("started", trackId == null ? "" : trackId, "");
    }
}
