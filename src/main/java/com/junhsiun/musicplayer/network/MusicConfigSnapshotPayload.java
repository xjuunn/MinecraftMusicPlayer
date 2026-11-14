package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端 → 客户端：服务端配置快照。
 *
 * <p>用「键 / 值字符串」而不是给每个字段单独写一个 record：配置项会持续增加，
 * 键值表让设置页和网络层不必每加一个字段就改一遍协议。服务端只接受白名单里的键。</p>
 */
public record MusicConfigSnapshotPayload(boolean canEdit, Map<String, String> values) {

    public static final ResourceLocation ID =
            new ResourceLocation(MusicPlayerMod.MOD_ID, "music_config_snapshot");

    public MusicConfigSnapshotPayload {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static MusicConfigSnapshotPayload decode(FriendlyByteBuf buffer) {
        boolean canEdit = buffer.readBoolean();
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) {
            throw new IllegalArgumentException("Invalid config entry count: " + size);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new MusicConfigSnapshotPayload(canEdit, values);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.canEdit);
        List<Map.Entry<String, String>> entries = new ArrayList<>(this.values.entrySet());
        buffer.writeVarInt(entries.size());
        for (Map.Entry<String, String> entry : entries) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue() == null ? "" : entry.getValue());
        }
    }
}
