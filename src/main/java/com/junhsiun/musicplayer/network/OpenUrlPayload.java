package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class OpenUrlPayload {
    private final String url;

    public OpenUrlPayload(String url) {
        this.url = url;
    }

    public String url() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OpenUrlPayload)) {
            return false;
        }
        OpenUrlPayload that = (OpenUrlPayload) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return "OpenUrlPayload{url='" + url + "'}";
    }

    public static final ResourceLocation ID = new ResourceLocation(MusicPlayerMod.MOD_ID, "open_url");

    public static OpenUrlPayload decode(FriendlyByteBuf buffer) {
        return new OpenUrlPayload(buffer.readUtf());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.url);
    }

    public static void send(ServerPlayer player, OpenUrlPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.encode(buf);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
