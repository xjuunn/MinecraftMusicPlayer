package com.junhsiun.musicplayer;

import com.junhsiun.musicplayer.command.MusicCommands;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.network.BgmConfigPayload;
import com.junhsiun.musicplayer.network.JukeboxMusicPayload;
import com.junhsiun.musicplayer.network.MusicControlPayload;
import com.junhsiun.musicplayer.network.MusicPlaybackReportPayload;
import com.junhsiun.musicplayer.network.OpenUrlPayload;
import com.junhsiun.musicplayer.platform.NeteaseApiClient;
import com.junhsiun.musicplayer.platform.api.DirectMusicApi;
import com.junhsiun.musicplayer.platform.api.MusicApi;
import com.junhsiun.musicplayer.platform.api.MusicApiMode;
import com.junhsiun.musicplayer.platform.api.MusicApiRouter;
import com.junhsiun.musicplayer.platform.api.ProxyMusicApi;
import com.junhsiun.musicplayer.service.JukeboxPlaybackService;
import com.junhsiun.musicplayer.service.LootMusicDiscService;
import com.junhsiun.musicplayer.service.MusicQueueService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MusicPlayerMod implements ModInitializer {
    public static final String MOD_ID = "musicplayer";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");

    private static final MusicQueueService MUSIC_QUEUE_SERVICE = new MusicQueueService();
    private static final JukeboxPlaybackService JUKEBOX_PLAYBACK_SERVICE = new JukeboxPlaybackService();
    private static final LootMusicDiscService LOOT_MUSIC_DISC_SERVICE = new LootMusicDiscService();
    private static final NeteaseApiClient NETEASE_API_CLIENT = new NeteaseApiClient();
    /** 直连实现延迟创建：只有真正用到直连时才初始化音源探测链。 */
    private static volatile MusicApi DIRECT_MUSIC_API;
    private static final MusicApiRouter MUSIC_API = new MusicApiRouter(
            MusicPlayerMod::directMusicApi,
            new ProxyMusicApi(NETEASE_API_CLIENT),
            MusicApiMode::current);

    private static MusicApi directMusicApi() {
        MusicApi direct = DIRECT_MUSIC_API;
        if (direct == null) {
            synchronized (MusicPlayerMod.class) {
                direct = DIRECT_MUSIC_API;
                if (direct == null) {
                    direct = new DirectMusicApi();
                    DIRECT_MUSIC_API = direct;
                }
            }
        }
        return direct;
    }

    @Override
    public void onInitialize() {
        MusicPlayerConfigManager.load();

        CommandRegistrationCallback.EVENT.register(MusicCommands::register);
        ServerTickEvents.END_SERVER_TICK.register(JUKEBOX_PLAYBACK_SERVICE::tick);
        ServerTickEvents.END_SERVER_TICK.register(MUSIC_QUEUE_SERVICE::tick);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hitResult == null) return InteractionResult.PASS;
            ItemStack heldStack = player.getItemInHand(hand);

            if (MusicDiscHelper.isPendingDisc(heldStack)) {
                if (player instanceof net.minecraft.server.level.ServerPlayer) {
                    net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;
                    LOOT_MUSIC_DISC_SERVICE.useRandomDisc(serverPlayer, hand);
                }
                return InteractionResult.SUCCESS;
            }

            BlockState state = world.getBlockState(hitResult.getBlockPos());
            boolean isJukeboxNoRecord = state.getBlock() instanceof JukeboxBlock && !state.getValue(JukeboxBlock.HAS_RECORD);
            boolean isMusicDisc = MusicDiscHelper.isMusicPlayerDisc(heldStack);
            LOGGER.info("[DEBUG] UseBlockCallback: block={}, isJukebox={}, hasRecord={}, isMusicDisc={}, isClient={}",
                    state.getBlock().getClass().getSimpleName(),
                    state.getBlock() instanceof JukeboxBlock,
                    state.getBlock() instanceof JukeboxBlock ? state.getValue(JukeboxBlock.HAS_RECORD) : "N/A",
                    isMusicDisc,
                    world.isClientSide());
            if (isJukeboxNoRecord) {
                if (isMusicDisc) {
                    if (world.isClientSide()) {
                        return InteractionResult.SUCCESS;
                    }
                    if (player instanceof net.minecraft.server.level.ServerPlayer) {
                        net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;
                        return JUKEBOX_PLAYBACK_SERVICE.tryInsertCustomDisc(serverPlayer, world, hand, hitResult);
                    }
                    return InteractionResult.FAIL;
                }
            }

            if (!(player instanceof net.minecraft.server.level.ServerPlayer)) {
                return InteractionResult.PASS;
            }
            net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;
            return LOOT_MUSIC_DISC_SERVICE.tryInjectOnOpen(serverPlayer, world, hitResult.getBlockPos());
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer)) {
                return InteractionResult.PASS;
            }
            net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;
            if (MusicDiscHelper.isPendingDisc(player.getItemInHand(hand))) {
                LOOT_MUSIC_DISC_SERVICE.useRandomDisc(serverPlayer, hand);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer)) {
                return InteractionResult.PASS;
            }
            net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;
            return LOOT_MUSIC_DISC_SERVICE.tryInjectOnOpen(serverPlayer, entity);
        });
        ServerPlayNetworking.registerGlobalReceiver(MusicPlaybackReportPayload.ID, (server, player, handler, buf, sender) -> {
            MusicPlaybackReportPayload payload = MusicPlaybackReportPayload.decode(buf);
            server.execute(() -> MUSIC_QUEUE_SERVICE.handlePlaybackReport(server, player, payload));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            MUSIC_QUEUE_SERVICE.handleJoin(handler.player);
            sendBgmConfig(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MUSIC_QUEUE_SERVICE.handleDisconnect(handler.player));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            MUSIC_QUEUE_SERVICE.initLyricsState(server);
            LOOT_MUSIC_DISC_SERVICE.setServer(server);
            LOOT_MUSIC_DISC_SERVICE.start();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            MUSIC_QUEUE_SERVICE.shutdown(server);
            JUKEBOX_PLAYBACK_SERVICE.shutdown(server);
            NETEASE_API_CLIENT.shutdownExecutor();
        });
    }

    public static void sendBgmConfig(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, BgmConfigPayload.ID)) {
            return;
        }
        String playlistId = MusicPlayerConfigManager.get().bgmPlaylistId;
        String baseUrl = MusicPlayerConfigManager.get().neteaseBaseUrl;
        BgmConfigPayload.send(player, new BgmConfigPayload(
                playlistId == null ? "" : playlistId.trim(),
                baseUrl == null ? "" : baseUrl.trim()));
    }

    public static void syncBgmConfig(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendBgmConfig(player);
        }
    }

    public static MusicQueueService queueService() {
        return MUSIC_QUEUE_SERVICE;
    }

    public static MusicApi netease() {
        return MUSIC_API;
    }

    /** 路由器本体，供配置命令读写模式与健康状态。 */
    public static MusicApiRouter musicApiRouter() {
        return MUSIC_API;
    }

    public static JukeboxPlaybackService jukeboxService() {
        return JUKEBOX_PLAYBACK_SERVICE;
    }
}
