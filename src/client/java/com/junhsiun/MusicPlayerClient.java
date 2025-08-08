package com.junhsiun;

import com.junhsiun.core.channel.MusicChannelReceiver;
import com.junhsiun.core.musicPlayer.ModMusicPlayer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicPlayerClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("客户端:ModMusicPlayer");

    @Override
    public void onInitializeClient() {
        ModMusicPlayer musicPlayer = ModMusicPlayer.getInstance();
        MusicChannelReceiver.onReceive((txt, minecraftClient, packetSender) -> {


//            musicPlayer.loadLoaclMusic("D:\\xjuun\\Downloads\\music.mp3");
//            musicPlayer.loadNetworkMusic("http://m8.music.126.net/20250807194300/2357e8352913c1b51abef552c26b51ae/ymusic/565e/0e5d/0f5a/a43b41a857976299c79f8023c5db5f99.mp3?vuutv=jpUzoWBjpRuEmIGwJdbKa8rX20g2RTJyeJFWDNC+ZG1a6iFVxKgsRms5E0WRSPhOQGbK+LuFlU6JBjQ1qyrYFZ3Obey1yrIRJWwDiUGQSBA=");
//            musicPlayer.play();


            if (minecraftClient.player != null) {
                minecraftClient.player.sendMessage(Text.literal("客户端收到" + txt + "指令"), false);
            }
        });
    }
}