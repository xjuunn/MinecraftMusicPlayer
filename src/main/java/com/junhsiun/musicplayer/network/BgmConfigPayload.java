package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class BgmConfigPayload {
    private final String playlistId;
    private final String baseUrl;

    public BgmConfigPayload(String playlistId, String baseUrl) {
        this.playlistId = playlistId;
        this.baseUrl = baseUrl;
    }

    public String playlistId() {
        return playlistId;
    }

    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BgmConfigPayload)) {
            return false;
        }
        BgmConfigPayload that = (BgmConfigPayload) o;
        return Objects.equals(playlistId, that.playlistId)
                && Objects.equals(baseUrl, that.baseUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playlistId, baseUrl);
    }

    @Override
    public String toString() {
        return "BgmConfigPayload{playlistId='" + playlistId + "', baseUrl='" + baseUrl + "'}";
    }

    public static final ResourceLocation ID = new ResourceLocation(MusicPlayerMod.MOD_ID, "bgm_config");

    public static BgmConfigPayload decode(FriendlyByteBuf buffer) {
        return new BgmConfigPayload(buffer.readUtf(), buffer.readUtf());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.playlistId == null ? "" : this.playlistId);
        buffer.writeUtf(this.baseUrl == null ? "" : this.baseUrl);
    }

    public static void send(ServerPlayer player, BgmConfigPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.encode(buf);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
