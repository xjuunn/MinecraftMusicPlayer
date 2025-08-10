package com.junhsiun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junhsiun.core.channel.MusicChannelReceiver;
import com.junhsiun.core.command.subcommands.vo.SongVO;
import com.junhsiun.core.musicPlayer.ModMusicPlayer;
import com.junhsiun.core.utils.ModClientLogger;
import javazoom.jl.decoder.JavaLayerException;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.util.Objects;

public class MusicPlayerClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("客户端:ModMusicPlayer");

    @Override
    public void onInitializeClient() {
        ModMusicPlayer musicPlayer = ModMusicPlayer.getInstance();
        MusicChannelReceiver.onReceive((txt, minecraftClient, packetSender) -> {
            String[] cmd = txt.split(" ");
            if (Objects.equals(cmd[1], "play")) {
                playSong(musicPlayer, cmd[2]);
            }
        });
    }

    public void playSong(ModMusicPlayer musicPlayer, String url) throws IOException, JavaLayerException {
        musicPlayer.close();
        musicPlayer.loadNetworkMusic(url);
        musicPlayer.play();
    }
}