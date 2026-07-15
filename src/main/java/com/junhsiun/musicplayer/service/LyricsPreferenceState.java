package com.junhsiun.musicplayer.service;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LyricsPreferenceState extends SavedData {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("musicplayer", "lyrics_preferences");
    private static final Codec<LyricsPreferenceState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.BOOL)
                            .fieldOf("players")
                            .forGetter(state -> state.preferences)
            ).apply(instance, LyricsPreferenceState::new)
    );
    public static final SavedDataType<LyricsPreferenceState> TYPE =
            new SavedDataType<>(ID, LyricsPreferenceState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<UUID, Boolean> preferences = new HashMap<>();

    private LyricsPreferenceState() {
    }

    private LyricsPreferenceState(Map<UUID, Boolean> preferences) {
        this.preferences.putAll(preferences);
    }

    public boolean isEnabled(UUID playerId) {
        return preferences.getOrDefault(playerId, false);
    }

    public void setEnabled(UUID playerId, boolean enabled) {
        if (enabled) {
            preferences.put(playerId, true);
        } else {
            preferences.remove(playerId);
        }
        setDirty();
    }
}
