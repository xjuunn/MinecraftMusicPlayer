package com.junhsiun.musicplayer;

import com.junhsiun.musicplayer.client.ClientJukeboxController;
import com.junhsiun.musicplayer.client.ClientMusicController;
import com.junhsiun.musicplayer.network.JukeboxMusicPayload;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MusicPlayerClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MusicControlPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientMusicController.getInstance().handle(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(JukeboxMusicPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientJukeboxController.getInstance().handle(payload))
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientMusicController.getInstance().stop("Disconnected.");
            ClientJukeboxController.getInstance().stopAll("Disconnected.");
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ClientMusicController.getInstance().stop("Client stopping.");
            ClientJukeboxController.getInstance().stopAll("Client stopping.");
        });
    }
}
