package com.junhsiun.core.channel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public class MusicChannelReceiver {
    public static final Identifier MUSIC_CHANNEL = new Identifier("musicplayer", "play_music");

    public static void onReceive(ReceiveCallBack callback) {
        ClientPlayNetworking.registerGlobalReceiver(MUSIC_CHANNEL, (minecraftClient,
                                                                    clientPlayNetworkHandler,
                                                                    packetByteBuf,
                                                                    packetSender) -> {
            String txt = packetByteBuf.readString();
            minecraftClient.execute(() -> {
                callback.onReceive(txt, minecraftClient, packetSender);
            });
        });
    }

    public interface ReceiveCallBack {
        void onReceive(String txt, MinecraftClient minecraftClient, PacketSender packetSender);
    }


}

