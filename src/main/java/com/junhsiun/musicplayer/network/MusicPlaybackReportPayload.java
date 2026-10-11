package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record MusicPlaybackReportPayload(String action, String trackId, String message) {
    public static final ResourceLocation ID = new ResourceLocation(MusicPlayerMod.MOD_ID, "music_playback_report");

    public static MusicPlaybackReportPayload decode(FriendlyByteBuf buffer) {
        return new MusicPlaybackReportPayload(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.action);
        buffer.writeUtf(this.trackId);
        buffer.writeUtf(this.message);
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
}
