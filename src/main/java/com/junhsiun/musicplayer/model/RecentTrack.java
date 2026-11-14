package com.junhsiun.musicplayer.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

/**
 * 一条「最近播放」记录。
 *
 * <p>只保留列表展示需要的字段，不保存音源直链：直链会过期，而且体积远大于展示所需。
 * 重新播放时仍然走正常的 {@code /music play song <id>} 解析链路。</p>
 */
public record RecentTrack(String id, String title, String artist, String coverUrl,
                          long playedAtMillis, long durationMillis) {

    public static final Codec<RecentTrack> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("id").forGetter(RecentTrack::id),
                    Codec.STRING.optionalFieldOf("title", "").forGetter(RecentTrack::title),
                    Codec.STRING.optionalFieldOf("artist", "").forGetter(RecentTrack::artist),
                    Codec.STRING.optionalFieldOf("cover", "").forGetter(RecentTrack::coverUrl),
                    Codec.LONG.optionalFieldOf("played_at", 0L).forGetter(RecentTrack::playedAtMillis),
                    Codec.LONG.optionalFieldOf("duration", 0L).forGetter(RecentTrack::durationMillis)
            ).apply(instance, RecentTrack::new)
    );

    public RecentTrack {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        coverUrl = coverUrl == null ? "" : coverUrl;
        playedAtMillis = Math.max(0L, playedAtMillis);
        durationMillis = Math.max(0L, durationMillis);
    }

    /** 1.21.1 没有 Codec 存储，最近播放走手写 NBT。 */
    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("title", title);
        tag.putString("artist", artist);
        tag.putString("cover", coverUrl);
        tag.putLong("played_at", playedAtMillis);
        tag.putLong("duration", durationMillis);
        return tag;
    }

    public static Optional<RecentTrack> decode(CompoundTag tag) {
        String trackId = tag.getString("id");
        if (trackId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RecentTrack(
                trackId,
                tag.getString("title"),
                tag.getString("artist"),
                tag.getString("cover"),
                tag.getLong("played_at"),
                tag.getLong("duration")
        ));
    }
}
