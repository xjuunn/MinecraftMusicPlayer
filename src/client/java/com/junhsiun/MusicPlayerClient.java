package com.junhsiun;

import com.junhsiun.core.channel.MusicChannelReceiver;
import com.junhsiun.core.musicPlayer.ModMusicPlayer;
import com.junhsiun.core.musicPlayer.ModMusicPlayerStatus;
import com.junhsiun.core.utils.ModLogger;
import javazoom.jl.decoder.JavaLayerException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

public class MusicPlayerClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("客户端:ModMusicPlayer");

    @Override
    public void onInitializeClient() {
        initMusicEvent();
        initJoinEvent();
    }

    private void initJoinEvent() {
        ModMusicPlayer musicPlayer = ModMusicPlayer.getInstance();
        ClientPlayConnectionEvents.DISCONNECT.register((clientPlayNetworkHandler, minecraftClient) -> {
            stopSong(musicPlayer);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraftClient -> {
            ModLogger.info("退出单人世界");
            stopSong(musicPlayer);
        });
    }

    private void initMusicEvent() {
        ModMusicPlayer musicPlayer = ModMusicPlayer.getInstance();
        MusicChannelReceiver.onReceive((txt, minecraftClient, packetSender) -> {
            String[] cmd = txt.split(" ");
            if (Objects.equals(cmd[1], "play")) {
                playSong(musicPlayer, cmd[2]);
            }
            if (Objects.equals(cmd[1], "stop")) {
                stopSong(musicPlayer);
            }
            if (Objects.equals(cmd[1], "leave")) {
                leaveSong(musicPlayer);
            }

            if (Objects.equals(cmd[1], "join")) {
                joinSong(musicPlayer);
            }

            if (Objects.equals(cmd[1], "muteOnce")) {
                muteOnce(musicPlayer);
            }

        });
    }

    public void playSong(ModMusicPlayer musicPlayer, String url) throws IOException, JavaLayerException {
        musicPlayer.close();
        musicPlayer.loadNetworkMusic(url);
        musicPlayer.play();
    }

    public void stopSong(ModMusicPlayer musicPlayer) {
        musicPlayer.close();
    }

    public void leaveSong(ModMusicPlayer musicPlayer) {
        musicPlayer.setStatus(ModMusicPlayerStatus.Leave);
        this.stopSong(musicPlayer);
    }

    public void joinSong(ModMusicPlayer musicPlayer) {
        musicPlayer.setStatus(ModMusicPlayerStatus.Standby);
    }

    public void muteOnce(ModMusicPlayer musicPlayer) {
        musicPlayer.setStatus(ModMusicPlayerStatus.MuteOnce);
    }

}