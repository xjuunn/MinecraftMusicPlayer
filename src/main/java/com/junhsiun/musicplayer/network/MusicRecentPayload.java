package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.model.RecentTrack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** 服务端 → 客户端：某位玩家的「最近播放」列表（最新在前）。 */
public record MusicRecentPayload(List<RecentTrack> entries) implements CustomPacketPayload {

    public static final Type<MusicRecentPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "music_recent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicRecentPayload> CODEC =
            CustomPacketPayload.codec(MusicRecentPayload::write, MusicRecentPayload::new);

    public MusicRecentPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public MusicRecentPayload(RegistryFriendlyByteBuf buffer) {
        this(readEntries(buffer));
    }

    private static List<RecentTrack> readEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 100) {
            throw new IllegalArgumentException("Invalid recent entry count: " + size);
        }
        List<RecentTrack> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new RecentTrack(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readUtf(), buffer.readLong(), buffer.readLong()));
        }
        return entries;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
