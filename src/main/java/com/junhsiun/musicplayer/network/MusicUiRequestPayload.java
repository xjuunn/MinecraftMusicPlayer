package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：请求刷新底部播放状态 / 最近播放 / 配置快照。 */
public record MusicUiRequestPayload(String action) {

    public static final ResourceLocation ID =
            new ResourceLocation(MusicPlayerMod.MOD_ID, "music_ui_request");

    public static MusicUiRequestPayload decode(FriendlyByteBuf buffer) {
        return new MusicUiRequestPayload(buffer.readUtf());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.action == null ? "" : this.action);
    }

    public static MusicUiRequestPayload all() {
        return new MusicUiRequestPayload("all");
    }

    public static MusicUiRequestPayload recent() {
        return new MusicUiRequestPayload("recent");
    }

    public static MusicUiRequestPayload queue() {
        return new MusicUiRequestPayload("queue");
    }
}
