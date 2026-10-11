package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class MusicPlaybackReportPayload {
    private final String action;
    private final String trackId;
    private final String message;

    public MusicPlaybackReportPayload(String action, String trackId, String message) {
        this.action = action;
        this.trackId = trackId;
        this.message = message;
    }

    public String action() {
        return action;
    }

    public String trackId() {
        return trackId;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MusicPlaybackReportPayload)) {
            return false;
        }
        MusicPlaybackReportPayload that = (MusicPlaybackReportPayload) o;
        return Objects.equals(action, that.action)
                && Objects.equals(trackId, that.trackId)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, trackId, message);
    }

    @Override
    public String toString() {
        return "MusicPlaybackReportPayload{action='" + action + "', trackId='" + trackId + "', message='" + message + "'}";
    }

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
