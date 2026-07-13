package com.junhsiun.musicplayer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.model.TrackInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LootMusicDiscService {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {
    };
    private static final int POOL_TARGET = 5;
    private static final int POOL_REFILL_THRESHOLD = 3;

    private final Set<String> processedContainers = new HashSet<>();
    private final Queue<TrackInfo> trackPool = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean poolRefilling = new AtomicBoolean(false);
    private final Set<UUID> pendingPlayers = new HashSet<>();
    private boolean loaded;

    public InteractionResult tryInjectOnOpen(ServerPlayer player, Level world, BlockPos pos) {
        if (world.isClientSide()) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity randomizable)) {
            return InteractionResult.PASS;
        }
        return tryInjectOnOpen(
                player,
                randomizable,
                randomizable,
                key(world, pos)
        );
    }

    public InteractionResult tryInjectOnOpen(ServerPlayer player, Entity entity) {
        if (entity.level().isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!(entity instanceof Container container) || !(entity instanceof RandomizableContainer randomizable)) {
            return InteractionResult.PASS;
        }
        return tryInjectOnOpen(
                player,
                container,
                randomizable,
                key(entity)
        );
    }

    public void start() {
        refillPool();
    }

    public void useRandomDisc(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!MusicDiscHelper.isPendingDisc(held)) {
            return;
        }
        if (!pendingPlayers.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("已有正在进行的随机生成，请稍候...").withStyle(ChatFormatting.YELLOW));
            return;
        }

        player.sendSystemMessage(Component.literal("正在生成随机音乐...").withStyle(ChatFormatting.GRAY));

        ItemStack baseDisc = held.copyWithCount(1);
        if (held.getCount() == 1) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
            held.shrink(1);
        }

        TrackInfo cached = trackPool.poll();
        if (cached != null) {
            pendingPlayers.remove(player.getUUID());
            giveBurnedDisc(player, baseDisc, cached);
            refillPool();
            return;
        }

        fetchOneTrack().whenComplete((track, throwable) -> {
            MinecraftServer server = player.level().getServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                pendingPlayers.remove(player.getUUID());
                if (throwable != null || track == null) {
                    MusicPlayerMod.LOGGER.warn("Failed to generate random music disc for player {}", player.getScoreboardName(), throwable);
                    ItemStack returned = baseDisc.copyWithCount(1);
                    if (!player.getInventory().add(returned)) {
                        player.drop(returned, false, true);
                    }
                    player.sendSystemMessage(Component.literal("生成失败，唱片已归还").withStyle(ChatFormatting.RED));
                    return;
                }
                giveBurnedDisc(player, baseDisc, track);
                refillPool();
            });
        });
    }

    private CompletableFuture<TrackInfo> fetchOneTrack() {
        return MusicPlayerMod.netease().randomHotTracks(1)
                .orTimeout(30, TimeUnit.SECONDS)
                .thenApply(tracks -> tracks == null || tracks.isEmpty() ? null : tracks.getFirst());
    }

    private void giveBurnedDisc(ServerPlayer player, ItemStack baseDisc, TrackInfo track) {
        ItemStack burnedDisc = MusicDiscHelper.burn(baseDisc, track);
        if (!player.getInventory().add(burnedDisc)) {
            player.drop(burnedDisc, false, true);
        }
        player.sendSystemMessage(Component.literal("已生成: ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(track.title()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(track.artist()).withStyle(ChatFormatting.GRAY)));
    }

    private void refillPool() {
        if (trackPool.size() >= POOL_REFILL_THRESHOLD || !poolRefilling.compareAndSet(false, true)) {
            return;
        }
        MusicPlayerMod.netease().randomHotTracks(POOL_TARGET)
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((tracks, throwable) -> {
                    poolRefilling.set(false);
                    if (throwable != null || tracks == null) {
                        return;
                    }
                    trackPool.addAll(tracks);
                    MusicPlayerMod.LOGGER.info("Refilled random track pool with {} tracks (pool size: {})", tracks.size(), trackPool.size());
                });
    }

    private InteractionResult tryInjectOnOpen(
            ServerPlayer player,
            Container container,
            RandomizableContainer randomizable,
            String containerKey
    ) {
        if (randomizable.getLootTable() == null) {
            return InteractionResult.PASS;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return InteractionResult.PASS;
        }
        load(server);
        if (!processedContainers.add(containerKey)) {
            return InteractionResult.PASS;
        }
        save(server);

        if (!MusicPlayerConfigManager.get().enableLootMusicDiscs) {
            return InteractionResult.PASS;
        }
        int count = MusicPlayerConfigManager.get().lootMusicDiscCount;
        if (count <= 0) {
            return InteractionResult.PASS;
        }
        if (player.getRandom().nextDouble() > MusicPlayerConfigManager.get().lootMusicDiscChance) {
            return InteractionResult.PASS;
        }

        randomizable.unpackLootTable(player);
        int inserted = insertPendingDiscs(container, player.level().getRandom());
        if (inserted > 0) {
            MusicPlayerMod.LOGGER.info("Injected {} random music disc(s) into loot container: {}", inserted, containerKey);
            container.setChanged();
        }
        return InteractionResult.PASS;
    }

    private int insertPendingDiscs(Container container, net.minecraft.util.RandomSource random) {
        int inserted = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.getItem(slot).isEmpty()) {
                continue;
            }
            String token = UUID.randomUUID().toString();
            ItemStack pendingDisc = MusicDiscHelper.createPendingDisc(
                    MusicDiscHelper.randomBaseDiscStack(random),
                    token
            );
            container.setItem(slot, pendingDisc);
            inserted++;
        }
        return inserted;
    }

    private void load(MinecraftServer server) {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = storePath(server);
        if (Files.notExists(path)) {
            return;
        }
        try {
            processedContainers.clear();
            processedContainers.addAll(MAPPER.readValue(path.toFile(), STRING_SET));
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.warn("Failed to read loot music disc injection state.", exception);
        }
    }

    private void save(MinecraftServer server) {
        Path path = storePath(server);
        try {
            Files.createDirectories(path.getParent());
            MAPPER.writeValue(path.toFile(), processedContainers);
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.warn("Failed to save loot music disc injection state.", exception);
        }
    }

    private static Path storePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("musicplayer")
                .resolve("loot-music-disc-containers.json");
    }

    private static String key(Level world, BlockPos pos) {
        return world.dimension().toString() + ":" + pos.asLong();
    }

    private static String key(Entity entity) {
        return entity.level().dimension().toString() + ":entity:" + entity.getUUID();
    }
}
