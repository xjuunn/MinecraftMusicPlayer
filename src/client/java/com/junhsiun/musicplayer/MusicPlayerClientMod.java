package com.junhsiun.musicplayer;

import com.junhsiun.musicplayer.client.CoverArtTextureCache;
import com.junhsiun.musicplayer.client.ClientJukeboxController;
import com.junhsiun.musicplayer.client.ClientMusicController;
import com.junhsiun.musicplayer.client.JukeboxCoverRenderer;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.network.JukeboxMusicPayload;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class MusicPlayerClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MusicControlPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientMusicController.getInstance().handle(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(JukeboxMusicPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientJukeboxController.getInstance().handle(payload))
        );
        LevelRenderEvents.COLLECT_SUBMITS.register(JukeboxCoverRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientJukeboxController.getInstance().tick(client));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClientSide()) {
                return InteractionResult.PASS;
            }
            BlockState state = world.getBlockState(hitResult.getBlockPos());
            if (!(state.getBlock() instanceof JukeboxBlock) || state.getValue(JukeboxBlock.HAS_RECORD)) {
                return InteractionResult.PASS;
            }
            if (!MusicDiscHelper.isMusicPlayerDisc(player.getItemInHand(hand))) {
                return InteractionResult.PASS;
            }
            ClientJukeboxController.getInstance().markPendingInsertion(hitResult.getBlockPos());
            return InteractionResult.PASS;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientMusicController.getInstance().stop("Disconnected.");
            ClientJukeboxController.getInstance().stopAll("Disconnected.");
            CoverArtTextureCache.getInstance().clear();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ClientMusicController.getInstance().stop("Client stopping.");
            ClientJukeboxController.getInstance().stopAll("Client stopping.");
            CoverArtTextureCache.getInstance().clear();
        });
    }
}
