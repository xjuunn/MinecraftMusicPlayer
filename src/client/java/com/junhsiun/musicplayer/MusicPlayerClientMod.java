package com.junhsiun.musicplayer;

import com.junhsiun.musicplayer.client.ClientBgmController;
import com.junhsiun.musicplayer.client.CoverArtTextureCache;
import com.junhsiun.musicplayer.client.ClientJukeboxController;
import com.junhsiun.musicplayer.client.ClientMusicController;
import com.junhsiun.musicplayer.client.JukeboxCoverRenderer;
import com.junhsiun.musicplayer.client.config.MusicPlayerClientConfigManager;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.network.BgmConfigPayload;
import com.junhsiun.musicplayer.network.JukeboxMusicPayload;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import com.junhsiun.musicplayer.network.OpenUrlPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.net.URI;

public final class MusicPlayerClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 客户端本地配置（BGM 音量/开关）独立于服务端共享配置
        MusicPlayerClientConfigManager.load();
        ClientPlayNetworking.registerGlobalReceiver(MusicControlPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientMusicController.getInstance().handle(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(JukeboxMusicPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientJukeboxController.getInstance().handle(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(OpenUrlPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    String url = payload.url();
                    if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
                        return;
                    }
                    Minecraft client = context.client();
                    if (!client.options.chatLinks().get()) {
                        return;
                    }
                    try {
                        URI uri = URI.create(url);
                        if (client.options.chatLinksPrompt().get()) {
                            ConfirmLinkScreen.confirmLinkNow(client.screen, uri, false);
                        } else {
                            Util.getPlatform().openUri(uri);
                        }
                    } catch (Exception exception) {
                        MusicPlayerMod.LOGGER.warn("打开链接失败: {}", url, exception);
                    }
                })
        );
        ClientPlayNetworking.registerGlobalReceiver(BgmConfigPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientBgmController.getInstance()
                        .configureServer(payload.playlistId(), payload.baseUrl()))
        );
        LevelRenderEvents.COLLECT_SUBMITS.register(JukeboxCoverRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MusicPlayerClientConfigManager.tick();
            ClientJukeboxController.getInstance().tick(client);
            ClientMusicController.getInstance().tick();
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClientSide() || hitResult == null) {
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

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ClientBgmController.getInstance().onEnteredWorld());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientMusicController.getInstance().stop("Disconnected.");
            // 退回主菜单：恢复使用客户端本地歌单
            ClientBgmController.getInstance().configureLocal(MusicPlayerConfigManager.get().bgmPlaylistId);
            ClientJukeboxController.getInstance().stopAll("Disconnected.");
            CoverArtTextureCache.getInstance().clear();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MusicPlayerClientConfigManager.flushDirty();
            ClientMusicController.getInstance().stop("Client stopping.");
            ClientBgmController.getInstance().reset();
            ClientJukeboxController.getInstance().stopAll("Client stopping.");
            CoverArtTextureCache.getInstance().shutdown();
        });

        // 主菜单（未连接存档）也使用客户端本地歌单替换原版背景音乐
        ClientBgmController.getInstance().configureLocal(MusicPlayerConfigManager.get().bgmPlaylistId);
    }
}
