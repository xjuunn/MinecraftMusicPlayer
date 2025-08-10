package com.junhsiun.core.channel;

import javazoom.jl.decoder.JavaLayerException;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.FileNotFoundException;
import java.io.IOException;

public class MusicChannelReceiver {
    public static final Identifier MUSIC_CHANNEL = new Identifier("musicplayer", "play_music");

    public static void onReceive(ReceiveCallBack callback) {
        ClientPlayNetworking.registerGlobalReceiver(MUSIC_CHANNEL, (minecraftClient,
                                                                    clientPlayNetworkHandler,
                                                                    packetByteBuf,
                                                                    packetSender) -> {
            String txt = packetByteBuf.readString();
            minecraftClient.execute(() -> {
                try {
                    callback.onReceive(txt, minecraftClient, packetSender);
                } catch (JavaLayerException | IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    public interface ReceiveCallBack {
        void onReceive(String txt, MinecraftClient minecraftClient, PacketSender packetSender) throws IOException, JavaLayerException;
    }


}

