package com.junhsiun.musicplayer;

import com.junhsiun.musicplayer.command.MusicCommands;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import com.junhsiun.musicplayer.network.MusicPlaybackReportPayload;
import com.junhsiun.musicplayer.platform.NeteaseApiClient;
import com.junhsiun.musicplayer.service.MusicQueueService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MusicPlayerMod implements ModInitializer {
    public static final String MOD_ID = "musicplayer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final MusicQueueService MUSIC_QUEUE_SERVICE = new MusicQueueService();
    private static final NeteaseApiClient NETEASE_API_CLIENT = new NeteaseApiClient();

    @Override
    public void onInitialize() {
        MusicPlayerConfigManager.load();
        PayloadTypeRegistry.playS2C().register(MusicControlPayload.TYPE, MusicControlPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MusicPlaybackReportPayload.TYPE, MusicPlaybackReportPayload.CODEC);

        CommandRegistrationCallback.EVENT.register(MusicCommands::register);
        ServerTickEvents.END_SERVER_TICK.register(MUSIC_QUEUE_SERVICE::tick);
        ServerPlayNetworking.registerGlobalReceiver(MusicPlaybackReportPayload.TYPE, (payload, context) ->
                context.server().execute(() ->
                        MUSIC_QUEUE_SERVICE.handlePlaybackReport(context.server(), context.player(), payload))
        );
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MUSIC_QUEUE_SERVICE.handleJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MUSIC_QUEUE_SERVICE.handleDisconnect(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(MUSIC_QUEUE_SERVICE::shutdown);
    }

    public static MusicQueueService queueService() {
        return MUSIC_QUEUE_SERVICE;
    }

    public static NeteaseApiClient netease() {
        return NETEASE_API_CLIENT;
    }
}
