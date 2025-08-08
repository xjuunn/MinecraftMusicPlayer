package com.junhsiun;

import com.junhsiun.core.channel.MusicChannelReceiver;
import com.junhsiun.core.musicPlayer.ModMusicPlayer;
import com.junhsiun.core.utils.ModClientLogger;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Objects;

public class MusicPlayerClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("客户端:ModMusicPlayer");

    @Override
    public void onInitializeClient() {
        ModMusicPlayer musicPlayer = ModMusicPlayer.getInstance();
        MusicChannelReceiver.onReceive((txt, minecraftClient, packetSender) -> {;
            String[] cmd = txt.split(" ");
            if (Objects.equals(cmd[1], "play")) {
                musicPlayer.close();
                ModClientLogger.info("1播放音乐：" + cmd[2]);
                musicPlayer.loadNetworkMusic(cmd[2]);
                musicPlayer.play();
            }

            if (minecraftClient.player != null) {
                minecraftClient.player.sendMessage(Text.literal(txt), false);
            }
        });
    }
}