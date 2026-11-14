package com.junhsiun.musicplayer.service;

import com.junhsiun.musicplayer.model.RecentTrack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

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
 *
 * <p>1.21.1 还没有 {@code SavedDataType} + Codec 体系，走旧式 {@code SavedData.Factory} + 手写 NBT。</p>
 */
public final class RecentPlaybackState extends SavedData {

    /** 每位玩家保留的最大条数。 */
    public static final int MAX_ENTRIES = 50;

    public static final SavedData.Factory<RecentPlaybackState> FACTORY = new SavedData.Factory<>(
            RecentPlaybackState::new,
            RecentPlaybackState::fromTag,
            DataFixTypes.LEVEL
    );

    private final Map<UUID, List<RecentTrack>> players = new HashMap<>();

    public RecentPlaybackState() {
    }

    public static RecentPlaybackState fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        RecentPlaybackState state = new RecentPlaybackState();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            UUID playerId;
            try {
                playerId = UUID.fromString(entry.getString("uuid"));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            List<RecentTrack> tracks = new ArrayList<>();
            ListTag trackList = entry.getList("tracks", Tag.TAG_COMPOUND);
            for (int trackIndex = 0; trackIndex < trackList.size(); trackIndex++) {
                RecentTrack.decode(trackList.getCompound(trackIndex)).ifPresent(tracks::add);
            }
            state.players.put(playerId, tracks);
        }
        return state;
    }

    public static String storageId() {
        return "musicplayer_recent_playback";
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, List<RecentTrack>> entry : players.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag playerTag = new CompoundTag();
            playerTag.putString("uuid", entry.getKey().toString());
            ListTag trackList = new ListTag();
            for (RecentTrack track : entry.getValue()) {
                trackList.add(track.encode());
            }
            playerTag.put("tracks", trackList);
            list.add(playerTag);
        }
        tag.put("players", list);
        return tag;
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
