package com.junhsiun.musicplayer.service;

import com.junhsiun.musicplayer.model.RecentTrack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端保存的「最近播放」历史，按玩家分开。
 *
 * <p>数据挂在世界存档里（{@link SavedData}），所以换客户端、换设备仍然能看到同一份历史；
 * 客户端只在本次连接期间缓存服务端同步下来的列表，不落盘。</p>
 */
public final class RecentPlaybackState extends SavedData {

    /** 每位玩家保留的最大条数。 */
    public static final int MAX_ENTRIES = 50;

    private static final String ID = "musicplayer_recent_playback";
    private static final Codec<RecentPlaybackState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, RecentTrack.CODEC.listOf())
                            .fieldOf("players")
                            .forGetter(state -> state.players)
            ).apply(instance, RecentPlaybackState::new)
    );
    public static final SavedDataType<RecentPlaybackState> TYPE =
            new SavedDataType<>(ID, RecentPlaybackState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<UUID, List<RecentTrack>> players = new HashMap<>();

    private RecentPlaybackState() {
    }

    private RecentPlaybackState(Map<UUID, List<RecentTrack>> players) {
        players.forEach((uuid, entries) -> this.players.put(uuid, new ArrayList<>(entries)));
    }

    /** 某位玩家的历史，最新在前。 */
    public List<RecentTrack> entries(UUID playerId) {
        List<RecentTrack> entries = players.get(playerId);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    /**
     * 记录一次播放。同一首歌再次播放时移到最前，不产生重复条目。
     */
    public void record(UUID playerId, RecentTrack track) {
        if (playerId == null || track == null || track.id().isBlank()) {
            return;
        }
        List<RecentTrack> entries = players.computeIfAbsent(playerId, key -> new ArrayList<>());
        entries.removeIf(existing -> existing.id().equals(track.id()));
        entries.addFirst(track);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
        setDirty();
    }
}
