package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.model.QueueEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：当前播放队列快照。
 *
 * <p>播放状态（当前曲目、进度）仍然由 {@link MusicUiStatePayload} 负责，这里只补上
 * 队列里接下来要播的歌曲。队列页打开后按需请求并在可见期间低频刷新，不跟着两秒一次的
 * 状态广播一起发，避免长队列反复占用带宽。</p>
 *
 * @param entries           接下来要播放的歌曲（单点队列在前，歌单预载队列在后）
 * @param playlistRemaining 歌单模式剩余曲目数；不在歌单模式时为 0
 * @param playlistMode      当前是否处于歌单模式
 */
public record MusicQueuePayload(List<QueueEntry> entries, int playlistRemaining,
                                boolean playlistMode) implements CustomPacketPayload {

    /** 客户端最多接收这么多条，防止异常服务端塞爆列表。 */
    private static final int MAX_ENTRIES = 512;

    public static final Type<MusicQueuePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "music_queue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicQueuePayload> CODEC =
            CustomPacketPayload.codec(MusicQueuePayload::write, MusicQueuePayload::new);

    public MusicQueuePayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public MusicQueuePayload(RegistryFriendlyByteBuf buffer) {
        this(readEntries(buffer), buffer.readVarInt(), buffer.readBoolean());
    }

    private static List<QueueEntry> readEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid queue entry count: " + size);
        }
        List<QueueEntry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new QueueEntry(buffer.readUtf(), buffer.readUtf(),
                    buffer.readUtf(), buffer.readUtf(), buffer.readUtf()));
        }
        return entries;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entries.size());
        for (QueueEntry entry : this.entries) {
            buffer.writeUtf(entry.id());
            buffer.writeUtf(entry.title());
            buffer.writeUtf(entry.artist());
            buffer.writeUtf(entry.requester());
            buffer.writeUtf(entry.coverUrl());
        }
        buffer.writeVarInt(this.playlistRemaining);
        buffer.writeBoolean(this.playlistMode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}