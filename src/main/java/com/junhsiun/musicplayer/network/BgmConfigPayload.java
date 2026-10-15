package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record BgmConfigPayload(String playlistId) {
    public static final ResourceLocation ID = new ResourceLocation(MusicPlayerMod.MOD_ID, "bgm_config");

    public static BgmConfigPayload decode(FriendlyByteBuf buffer) {
        return new BgmConfigPayload(buffer.readUtf());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.playlistId == null ? "" : this.playlistId);
    }

    public static void send(ServerPlayer player, BgmConfigPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.encode(buf);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
