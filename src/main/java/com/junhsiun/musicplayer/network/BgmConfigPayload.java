package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record BgmConfigPayload(String playlistId, String baseUrl) {
    public static final ResourceLocation ID = new ResourceLocation(MusicPlayerMod.MOD_ID, "bgm_config");

    public static BgmConfigPayload decode(FriendlyByteBuf buffer) {
        return new BgmConfigPayload(buffer.readUtf(), buffer.readUtf());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.playlistId == null ? "" : this.playlistId);
        buffer.writeUtf(this.baseUrl == null ? "" : this.baseUrl);
    }

    public static void send(ServerPlayer player, String playlistId, String baseUrl) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new BgmConfigPayload(playlistId == null ? "" : playlistId, baseUrl == null ? "" : baseUrl).encode(buf);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
