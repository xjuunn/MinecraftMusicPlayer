package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：修改一个服务端配置项。仅管理员生效。 */
public record MusicConfigUpdatePayload(String key, String value) {

    public static final ResourceLocation ID =
            new ResourceLocation(MusicPlayerMod.MOD_ID, "music_config_update");

    public static MusicConfigUpdatePayload decode(FriendlyByteBuf buffer) {
        return new MusicConfigUpdatePayload(buffer.readUtf(), buffer.readUtf());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.key == null ? "" : this.key);
        buffer.writeUtf(this.value == null ? "" : this.value);
    }
}
