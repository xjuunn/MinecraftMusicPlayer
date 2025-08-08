package com.junhsiun.core.channel;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class MusicChannel {
    public static final Identifier MUSIC_CHANNEL = new Identifier("musicplayer", "play_music");

    public static void send(ServerPlayerEntity player, PacketByteBuf buf) {
        ServerPlayNetworking.send(player, MUSIC_CHANNEL, buf);
    }

    public static void send(ServerPlayerEntity player, String txt) {
        PacketByteBuf packetByteBuf = PacketByteBufs.create();
        packetByteBuf.writeString(txt);
        MusicChannel.send(player, packetByteBuf);
    }

    public static void broadcast(MinecraftServer server, String txt) {
        server.getPlayerManager().getPlayerList().forEach(player -> {
            send(player, txt);
        });
    }
}
