package com.junhsiun.musicplayer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.model.TrackInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LootMusicDiscService {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {
    };

    private final Set<String> processedContainers = new HashSet<>();
    private final Map<String, TrackInfo> resolvedPendingDiscs = new ConcurrentHashMap<>();
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
                world,
                randomizable,
                randomizable,
                key(world, pos),
                pos.toShortString()
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
                entity.level(),
                container,
                randomizable,
                key(entity),
                entity.getType() + "/" + entity.getUUID()
        );
    }

    public void tick(MinecraftServer server) {
        if (resolvedPendingDiscs.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            replaceResolvedInPlayerInventory(player);
            replaceResolvedInMenu(player.containerMenu);
        }
    }

    private InteractionResult tryInjectOnOpen(
            ServerPlayer player,
            Level world,
            Container container,
            RandomizableContainer randomizable,
            String containerKey,
            String locationDescription
    ) {
        if (randomizable.getLootTable() == null) {
            return InteractionResult.PASS;
        }

        MinecraftServer server = world.getServer();
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
        int requestedCount = Math.max(0, MusicPlayerConfigManager.get().lootMusicDiscCount);
        if (requestedCount <= 0) {
            return InteractionResult.PASS;
        }
        if (player.getRandom().nextDouble() > MusicPlayerConfigManager.get().lootMusicDiscChance) {
            return InteractionResult.PASS;
        }

        randomizable.unpackLootTable(player);
        List<PendingDiscPlacement> placements = insertPendingDiscs(container, world, requestedCount);
        if (placements.isEmpty()) {
            return InteractionResult.PASS;
        }

        MusicPlayerMod.LOGGER.info("Injecting random music disc placeholders into loot container: {}", locationDescription);
        MusicPlayerMod.netease().randomHotTracks(placements.size()).whenComplete((tracks, throwable) ->
                server.execute(() -> resolvePendingDiscs(container, placements, tracks, throwable, locationDescription)));
        return InteractionResult.PASS;
    }

    private List<PendingDiscPlacement> insertPendingDiscs(Container container, Level world, int requestedCount) {
        List<PendingDiscPlacement> placements = new ArrayList<>();
        for (int count = 0; count < requestedCount; count++) {
            int slot = firstEmptySlot(container);
            if (slot < 0) {
                break;
            }
            String token = UUID.randomUUID().toString();
            ItemStack pendingDisc = MusicDiscHelper.createPendingDisc(
                    MusicDiscHelper.randomBaseDiscStack(world.getRandom()),
                    token
            );
            container.setItem(slot, pendingDisc);
            placements.add(new PendingDiscPlacement(slot, token));
        }
        if (!placements.isEmpty()) {
            container.setChanged();
        }
        return placements;
    }

    private void resolvePendingDiscs(
            Container container,
            List<PendingDiscPlacement> placements,
            List<TrackInfo> tracks,
            Throwable throwable,
            String locationDescription
    ) {
        if (throwable != null) {
            MusicPlayerMod.LOGGER.warn("Failed to generate random loot music discs: {}", locationDescription, throwable);
            clearPendingDiscs(container, placements);
            return;
        }
        if (tracks == null || tracks.isEmpty()) {
            clearPendingDiscs(container, placements);
            return;
        }

        int resolveCount = Math.min(placements.size(), tracks.size());
        for (int index = 0; index < resolveCount; index++) {
            resolvedPendingDiscs.put(placements.get(index).token(), tracks.get(index));
        }

        replaceResolvedInContainer(container, placements);
        clearUnresolvedPlacements(container, placements.subList(resolveCount, placements.size()));
    }

    private void replaceResolvedInContainer(Container container, List<PendingDiscPlacement> placements) {
        boolean changed = false;
        for (PendingDiscPlacement placement : placements) {
            if (placement.slot() < 0 || placement.slot() >= container.getContainerSize()) {
                continue;
            }
            ItemStack current = container.getItem(placement.slot());
            if (!placement.matches(current)) {
                continue;
            }
            TrackInfo track = resolvedPendingDiscs.remove(placement.token());
            if (track == null) {
                continue;
            }
            container.setItem(placement.slot(), MusicDiscHelper.burn(current.copyWithCount(1), track));
            changed = true;
        }
        if (changed) {
            container.setChanged();
        }
    }

    private void clearPendingDiscs(Container container, List<PendingDiscPlacement> placements) {
        boolean changed = false;
        for (PendingDiscPlacement placement : placements) {
            resolvedPendingDiscs.remove(placement.token());
            if (placement.slot() < 0 || placement.slot() >= container.getContainerSize()) {
                continue;
            }
            ItemStack current = container.getItem(placement.slot());
            if (!placement.matches(current)) {
                continue;
            }
            container.setItem(placement.slot(), ItemStack.EMPTY);
            changed = true;
        }
        if (changed) {
            container.setChanged();
        }
    }

    private void clearUnresolvedPlacements(Container container, List<PendingDiscPlacement> placements) {
        if (placements.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (PendingDiscPlacement placement : placements) {
            if (placement.slot() < 0 || placement.slot() >= container.getContainerSize()) {
                continue;
            }
            ItemStack current = container.getItem(placement.slot());
            if (!placement.matches(current)) {
                continue;
            }
            container.setItem(placement.slot(), ItemStack.EMPTY);
            changed = true;
        }
        if (changed) {
            container.setChanged();
        }
    }

    private void replaceResolvedInPlayerInventory(ServerPlayer player) {
        Container inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            ItemStack replacement = resolveStack(current);
            if (replacement != null) {
                inventory.setItem(slot, replacement);
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setChanged();
        }
    }

    private void replaceResolvedInMenu(AbstractContainerMenu menu) {
        if (menu == null) {
            return;
        }
        for (Slot slot : menu.slots) {
            ItemStack replacement = resolveStack(slot.getItem());
            if (replacement != null) {
                slot.set(replacement);
                slot.setChanged();
            }
        }
    }

    private ItemStack resolveStack(ItemStack stack) {
        if (!MusicDiscHelper.isPendingDisc(stack)) {
            return null;
        }
        String token = MusicDiscHelper.getPendingToken(stack);
        if (token.isBlank()) {
            return null;
        }
        TrackInfo track = resolvedPendingDiscs.remove(token);
        if (track == null) {
            return null;
        }
        return MusicDiscHelper.burn(stack.copyWithCount(1), track);
    }

    private static int firstEmptySlot(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
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

    private record PendingDiscPlacement(int slot, String token) {
        private boolean matches(ItemStack stack) {
            return MusicDiscHelper.isPendingDisc(stack) && token.equals(MusicDiscHelper.getPendingToken(stack));
        }
    }
}