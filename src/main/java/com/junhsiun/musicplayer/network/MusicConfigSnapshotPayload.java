package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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
public record MusicConfigSnapshotPayload(boolean canEdit, Map<String, String> values)
        implements CustomPacketPayload {

    public static final Type<MusicConfigSnapshotPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "music_config_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicConfigSnapshotPayload> CODEC =
            CustomPacketPayload.codec(MusicConfigSnapshotPayload::write, MusicConfigSnapshotPayload::new);

    public MusicConfigSnapshotPayload {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public MusicConfigSnapshotPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), readValues(buffer));
    }

    private static Map<String, String> readValues(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) {
            throw new IllegalArgumentException("Invalid config entry count: " + size);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return values;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.canEdit);
        List<Map.Entry<String, String>> entries = new ArrayList<>(this.values.entrySet());
        buffer.writeVarInt(entries.size());
        for (Map.Entry<String, String> entry : entries) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue() == null ? "" : entry.getValue());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
