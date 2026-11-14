package com.junhsiun.musicplayer.service;

import com.junhsiun.musicplayer.model.RecentTrack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
 */
public final class RecentPlaybackState extends SavedData {

    /** 每位玩家保留的最大条数。 */
    public static final int MAX_ENTRIES = 50;

    public RecentPlaybackState() {
    }

    private final Map<UUID, List<RecentTrack>> players = new HashMap<>();


    /** 1.20.1 的 SavedData 加载入口：NBT → 状态。 */
    public static RecentPlaybackState fromTag(CompoundTag tag) {
        RecentPlaybackState state = new RecentPlaybackState();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            try {
                UUID playerId = UUID.fromString(entry.getString("uuid"));
                List<RecentTrack> entries = new ArrayList<>();
                ListTag tracks = entry.getList("tracks", Tag.TAG_COMPOUND);
                for (int trackIndex = 0; trackIndex < tracks.size(); trackIndex++) {
                    CompoundTag trackTag = tracks.getCompound(trackIndex);
                    entries.add(new RecentTrack(
                            trackTag.getString("id"),
                            trackTag.getString("title"),
                            trackTag.getString("artist"),
                            trackTag.getString("cover"),
                            trackTag.getLong("played_at"),
                            trackTag.getLong("duration")));
                }
                state.players.put(playerId, entries);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        players.forEach((playerId, entries) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("uuid", playerId.toString());
            ListTag tracks = new ListTag();
            for (RecentTrack track : entries) {
                CompoundTag trackTag = new CompoundTag();
                trackTag.putString("id", track.id());
                trackTag.putString("title", track.title());
                trackTag.putString("artist", track.artist());
                trackTag.putString("cover", track.coverUrl());
                trackTag.putLong("played_at", track.playedAtMillis());
                trackTag.putLong("duration", track.durationMillis());
                tracks.add(trackTag);
            }
            entry.put("tracks", tracks);
            list.add(entry);
        });
        tag.put("players", list);
        return tag;
    }

    public static String storageId() {
        return storageId();
    }

    public static RecentPlaybackState factory() {
        return new RecentPlaybackState();
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
        entries.add(0, track);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        setDirty();
    }
}
