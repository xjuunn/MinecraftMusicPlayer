package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.model.RecentTrack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** 服务端 → 客户端：某位玩家的「最近播放」列表（最新在前）。 */
public record MusicRecentPayload(List<RecentTrack> entries) {

    public static final ResourceLocation ID =
            new ResourceLocation(MusicPlayerMod.MOD_ID, "music_recent");

    public MusicRecentPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static MusicRecentPayload decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 100) {
            throw new IllegalArgumentException("Invalid recent entry count: " + size);
        }
        List<RecentTrack> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new RecentTrack(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readUtf(), buffer.readLong(), buffer.readLong()));
        }
        return new MusicRecentPayload(entries);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entries.size());
        for (RecentTrack entry : this.entries) {
            buffer.writeUtf(entry.id());
            buffer.writeUtf(entry.title());
            buffer.writeUtf(entry.artist());
            buffer.writeUtf(entry.coverUrl());
            buffer.writeLong(entry.playedAtMillis());
            buffer.writeLong(entry.durationMillis());
        }
    }
}
