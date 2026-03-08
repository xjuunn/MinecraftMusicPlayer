package com.junhsiun.musicplayer;

import com.junhsiun.musicplayer.client.ClientMusicController;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class MusicPlayerClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MusicControlPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientMusicController.getInstance().handle(payload))
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientMusicController.getInstance().stop("已断开连接，停止播放。"));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
                ClientMusicController.getInstance().stop("客户端关闭，停止播放。"));
    }
}
