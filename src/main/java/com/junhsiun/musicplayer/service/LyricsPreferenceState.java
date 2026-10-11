package com.junhsiun.musicplayer.service;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LyricsPreferenceState extends SavedData {
    private static final String ID = "musicplayer/lyrics_preferences";

    private final Map<UUID, Boolean> preferences = new HashMap<>();

    public LyricsPreferenceState() {
        super(ID);
    }

    @Override
    public void load(CompoundTag tag) {
        ListTag list = tag.getList("players", 10);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            try {
                UUID playerId = UUID.fromString(entry.getString("uuid"));
                preferences.put(playerId, entry.getBoolean("enabled"));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        preferences.forEach((playerId, enabled) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("uuid", playerId.toString());
            entry.putBoolean("enabled", enabled);
            list.add(entry);
        });
        tag.put("players", list);
        return tag;
    }

    public boolean isEnabled(UUID playerId) {
        return preferences.getOrDefault(playerId, false);
    }

    public void setEnabled(UUID playerId, boolean enabled) {
        preferences.put(playerId, enabled);
        setDirty();
    }

    public static String storageId() {
        return ID;
    }
}
