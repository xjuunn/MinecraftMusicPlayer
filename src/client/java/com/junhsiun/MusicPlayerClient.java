package com.junhsiun;

import com.junhsiun.core.channel.MusicChannelReceiver;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicPlayerClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("客户端：MusicPlayer");

    @Override
    public void onInitializeClient() {
        MusicChannelReceiver.onReceive((txt, minecraftClient, packetSender) -> {
            if (minecraftClient.player != null) {
                minecraftClient.player.sendMessage(Text.literal("客户端收到消息"), false);
            }
        });
    }
}