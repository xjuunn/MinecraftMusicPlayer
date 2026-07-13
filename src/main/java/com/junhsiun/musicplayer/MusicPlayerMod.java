package com.junhsiun.musicplayer;

import com.junhsiun.musicplayer.command.MusicCommands;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.network.JukeboxMusicPayload;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import com.junhsiun.musicplayer.network.MusicPlaybackReportPayload;
import com.junhsiun.musicplayer.platform.NeteaseApiClient;
import com.junhsiun.musicplayer.service.JukeboxPlaybackService;
import com.junhsiun.musicplayer.service.LootMusicDiscService;
import com.junhsiun.musicplayer.service.MusicQueueService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MusicPlayerMod implements ModInitializer {
    public static final String MOD_ID = "musicplayer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final MusicQueueService MUSIC_QUEUE_SERVICE = new MusicQueueService();
    private static final JukeboxPlaybackService JUKEBOX_PLAYBACK_SERVICE = new JukeboxPlaybackService();
    private static final LootMusicDiscService LOOT_MUSIC_DISC_SERVICE = new LootMusicDiscService();
    private static final NeteaseApiClient NETEASE_API_CLIENT = new NeteaseApiClient();

    @Override
    public void onInitialize() {
        MusicPlayerConfigManager.load();
        PayloadTypeRegistry.clientboundPlay().register(MusicControlPayload.TYPE, MusicControlPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(JukeboxMusicPayload.TYPE, JukeboxMusicPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MusicPlaybackReportPayload.TYPE, MusicPlaybackReportPayload.CODEC);

        CommandRegistrationCallback.EVENT.register(MusicCommands::register);
        ServerTickEvents.END_SERVER_TICK.register(MUSIC_QUEUE_SERVICE::tick);
        ServerTickEvents.END_SERVER_TICK.register(JUKEBOX_PLAYBACK_SERVICE::tick);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack heldStack = player.getItemInHand(hand);

            if (MusicDiscHelper.isPendingDisc(heldStack)) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    LOOT_MUSIC_DISC_SERVICE.useRandomDisc(serverPlayer, hand);
                }
                return InteractionResult.SUCCESS;
            }

            BlockState state = world.getBlockState(hitResult.getBlockPos());
            if (state.getBlock() instanceof JukeboxBlock && !state.getValue(JukeboxBlock.HAS_RECORD)) {
                if (MusicDiscHelper.isMusicPlayerDisc(heldStack)) {
                    if (world.isClientSide()) {
                        return InteractionResult.SUCCESS;
                    }
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        return JUKEBOX_PLAYBACK_SERVICE.tryInsertCustomDisc(serverPlayer, world, hand, hitResult);
                    }
                    return InteractionResult.FAIL;
                }
            }

            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            return LOOT_MUSIC_DISC_SERVICE.tryInjectOnOpen(serverPlayer, world, hitResult.getBlockPos());
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (MusicDiscHelper.isPendingDisc(player.getItemInHand(hand))) {
                LOOT_MUSIC_DISC_SERVICE.useRandomDisc(serverPlayer, hand);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            return LOOT_MUSIC_DISC_SERVICE.tryInjectOnOpen(serverPlayer, entity);
        });
        ServerPlayNetworking.registerGlobalReceiver(MusicPlaybackReportPayload.TYPE, (payload, context) ->
                context.server().execute(() ->
                        MUSIC_QUEUE_SERVICE.handlePlaybackReport(context.server(), context.player(), payload))
        );
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MUSIC_QUEUE_SERVICE.handleJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MUSIC_QUEUE_SERVICE.handleDisconnect(handler.player));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOOT_MUSIC_DISC_SERVICE.setServer(server);
            LOOT_MUSIC_DISC_SERVICE.start();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            MUSIC_QUEUE_SERVICE.shutdown(server);
            JUKEBOX_PLAYBACK_SERVICE.shutdown(server);
        });
    }

    public static MusicQueueService queueService() {
        return MUSIC_QUEUE_SERVICE;
    }

    public static NeteaseApiClient netease() {
        return NETEASE_API_CLIENT;
    }

    public static JukeboxPlaybackService jukeboxService() {
        return JUKEBOX_PLAYBACK_SERVICE;
    }
}
