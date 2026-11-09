package com.junhsiun.musicplayer;

import com.junhsiun.musicplayer.client.ClientBgmController;
import com.junhsiun.musicplayer.client.ClientMusicUiState;
import com.junhsiun.musicplayer.client.CoverArtTextureCache;
import com.junhsiun.musicplayer.client.ClientJukeboxController;
import com.junhsiun.musicplayer.client.ClientMusicController;
import com.junhsiun.musicplayer.client.JukeboxCoverRenderer;
import com.junhsiun.musicplayer.client.config.MusicPlayerClientConfigManager;
import com.junhsiun.musicplayer.client.gui.DiscMasterScreen;
import com.junhsiun.musicplayer.client.gui.browser.MusicBrowserScreen;
import com.junhsiun.musicplayer.client.gui.glass.GlassBackdrop;
import com.junhsiun.musicplayer.client.gui.glass.GlassPanel;
import com.junhsiun.musicplayer.client.gui.media.MediaThumbnails;
import com.junhsiun.musicplayer.client.gui.window.GlassWindowScreen;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.network.BgmConfigPayload;
import com.junhsiun.musicplayer.network.DiscMasterBurnResultPayload;
import com.junhsiun.musicplayer.network.JukeboxMusicPayload;
import com.junhsiun.musicplayer.network.MusicConfigSnapshotPayload;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import com.junhsiun.musicplayer.network.MusicQueuePayload;
import com.junhsiun.musicplayer.network.MusicRecentPayload;
import com.junhsiun.musicplayer.network.MusicUiStatePayload;
import com.junhsiun.musicplayer.network.OpenUrlPayload;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;

import java.net.URI;

public final class MusicPlayerClientMod implements ClientModInitializer {

    /** 打开音乐浏览界面的快捷键，默认 M。 */
    private static final KeyMapping OPEN_BROWSER = new KeyMapping(
            "key.musicplayer.open_browser",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_M,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "music")));

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
        ClientPlayNetworking.registerGlobalReceiver(MusicUiStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientMusicUiState.getInstance().update(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(MusicRecentPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientMusicUiState.getInstance().updateRecent(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(MusicQueuePayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientMusicUiState.getInstance().updateQueue(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(MusicConfigSnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientMusicUiState.getInstance().updateConfig(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(DiscMasterBurnResultPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof DiscMasterScreen screen) {
                        screen.handleBurnResult(payload);
                    }
                })
        );
        WorldRenderEvents.AFTER_ENTITIES.register(JukeboxCoverRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(JukeboxCoverRenderer::render);
        // 快捷键默认 M，玩家可在「选项 → 控制」里改键（KeyMapping 就是原版的改键机制）
        KeyMappingHelper.registerKeyMapping(OPEN_BROWSER);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MusicPlayerClientConfigManager.tick();
            ClientJukeboxController.getInstance().tick(client);
            ClientMusicController.getInstance().tick();
            prefetchHeldCover(client);
            // consumeClick 会把一次按下消费成一次打开，长按不会连续弹窗
            while (OPEN_BROWSER.consumeClick()) {
                if (client.gui.screen() == null && client.player != null) {
                    // 打开玻璃界面的唯一正确入口（预编译 + 不强制渲染一帧），见 GlassWindowScreen#open
                    GlassWindowScreen.open(new MusicBrowserScreen());
                }
            }
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

        // 手持唱片母版右键空气 → 打开液态玻璃界面
        //
        // 只监听 UseItemCallback。刻意不监听 UseBlockCallback / UseEntityCallback：
        // 那两个回调返回 SUCCESS 会吞掉方块与实体的右键交互，表现为手持母版时
        // 打不开箱子、用不了工作台、也没法和村民交易。
        //
        // 这样也足够：对着可交互方块右键时，原版 interactBlock 会先返回成功，
        // 后续的 interactItem 根本不会被调用，界面不会和容器界面抢；
        // 对着无交互方块或空气右键时 interactItem 才触发，界面照常打开。
        UseItemCallback.EVENT.register((player, world, hand) -> tryOpenDiscMaster(player, world, hand));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ClientBgmController.getInstance().onEnteredWorld());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientMusicController.getInstance().stop("Disconnected.");
            // 退回主菜单：恢复使用客户端本地歌单
            ClientBgmController.getInstance().configureLocal(MusicPlayerConfigManager.get().bgmPlaylistId);
            ClientJukeboxController.getInstance().stopAll("Disconnected.");
            CoverArtTextureCache.getInstance().clear();
            // 服务端同步下来的播放状态 / 最近播放 / 配置快照只属于本次连接
            ClientMusicUiState.getInstance().clear();
            // 浏览界面的缩略图纹理同理：退出世界就放掉，别把上一局的封面带进下一局
            MediaThumbnails.getInstance().clear();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MusicPlayerClientConfigManager.flushDirty();
            ClientMusicController.getInstance().stop("Client stopping.");
            ClientBgmController.getInstance().reset();
            ClientJukeboxController.getInstance().stopAll("Client stopping.");
            CoverArtTextureCache.getInstance().shutdown();
            GlassBackdrop.getInstance().reset();
        });

        // 主菜单（未连接存档）也使用客户端本地歌单替换原版背景音乐
        ClientBgmController.getInstance().configureLocal(MusicPlayerConfigManager.get().bgmPlaylistId);
    }

    /**
     * 上一个已预取过的封面地址，避免每 tick 都去解析手持物品的 NBT。
     */
    private static String lastPrefetchedCover = "";

    /**
     * 手持已刻录的唱片时提前把封面拉下来。
     *
     * <p>封面下载是在收到播放载荷之后才发起的，而唱片机一放上去就开始转，
     * 弱网下就会先看到几秒的黑色盘面。从刻录完成到插进唱片机之间，唱片一定在手上，
     * 在这里预取能把这段等待提前吃掉，插进去的时候纹理通常已经就绪。</p>
     */
    private static void prefetchHeldCover(Minecraft client) {
        Player player = client.player;
        if (player == null) {
            return;
        }
        prefetchCover(player.getMainHandItem());
        prefetchCover(player.getOffhandItem());
    }

    private static void prefetchCover(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String coverUrl = MusicDiscHelper.read(stack)
                .map(MusicDiscHelper.DiscTrackData::coverUrl)
                .orElse("");
        if (coverUrl.isBlank() || coverUrl.equals(lastPrefetchedCover)) {
            return;
        }
        lastPrefetchedCover = coverUrl;
        CoverArtTextureCache.getInstance().request(coverUrl);
    }

    /**
     * 手持唱片母版右键时打开界面。只在客户端本地玩家身上触发，服务端逻辑不受影响。
     */
    private static InteractionResult tryOpenDiscMaster(Player player, Level world, InteractionHand hand) {
        if (!world.isClientSide() || !(player instanceof LocalPlayer)) {
            return InteractionResult.PASS;
        }
        if (!MusicDiscHelper.isDiscMaster(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        // 管线编译和离屏贴图创建都要几百微秒到几毫秒。放在这里（输入回调，位于渲染循环
        // 之外）做完，界面出现的那一帧就是正常的完整帧；若拖到界面首帧的提取/绘制阶段，
        // 会打断正在进行的帧，驱动先送出一帧黑屏再显示界面。
        DiscMasterScreen screen = new DiscMasterScreen();
        // 统一走框架入口：预编译管线 + Gui.setScreen（不强制渲染，避免闪一帧黑）
        GlassWindowScreen.open(screen);
        return InteractionResult.SUCCESS;
    }
}
