package com.junhsiun.musicplayer.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.platform.ArtistTopSongSource;
import com.junhsiun.musicplayer.platform.HotPlaylistRandomSource;
import com.junhsiun.musicplayer.platform.RandomSongSource;
import com.junhsiun.musicplayer.util.FileAtomicWrite;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class LootMusicDiscService {
    /**
     * TrackInfo 是 record（字段均为 final），而部分 Minecraft 版本自带的 Gson
     * （如 1.18.2 的 2.8.9）不支持 record，会改用反射回填 final 字段并抛出
     * AssertionError。这里显式注册适配器，按字段手动构造，避免依赖反射。
     */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(TrackInfo.class, new TrackInfoAdapter())
            .create();
    private static final java.lang.reflect.Type STRING_SET = new TypeToken<Set<String>>(){}.getType();
    private static final java.lang.reflect.Type TRACK_LIST = new TypeToken<List<TrackInfo>>(){}.getType();
    private static final int POOL_CAPACITY = 20;
    private static final int REFILL_BATCH = 5;
    private static final int REFILL_THRESHOLD = POOL_CAPACITY - REFILL_BATCH;
    private static final long PER_SOURCE_TIMEOUT_SECONDS = 60;

    private final Set<String> processedContainers = new HashSet<>();
    private final Queue<TrackInfo> trackPool = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean poolRefilling = new AtomicBoolean(false);
    private final Set<UUID> pendingPlayers = new HashSet<>();
    private final List<RandomSongSource> sources = new ArrayList<>();
    private final AtomicInteger sourceIndex = new AtomicInteger(0);
    private boolean loaded;
    private volatile MinecraftServer server;

    public LootMusicDiscService() {
    }

    public void start() {
        sources.clear();
        sources.add(new HotPlaylistRandomSource(MusicPlayerMod.netease()));
        sources.add(new ArtistTopSongSource(MusicPlayerMod.netease()));
        loaded = false;
        processedContainers.clear();
        if (server != null) {
            loadCache();
        }
        refillPool();
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
        loaded = false;
        processedContainers.clear();
        if (isStarted()) {
            loadCache();
        }
    }

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
                p -> randomizable.unpackLootTable(p),
                key(world, pos)
        );
    }

    public InteractionResult tryInjectOnOpen(ServerPlayer player, Entity entity) {
        if (entity.level.isClientSide) {
            return InteractionResult.PASS;
        }
        if (!(entity instanceof AbstractMinecartContainer containerEntity)) {
            return InteractionResult.PASS;
        }
        return tryInjectOnOpen(
                player,
                containerEntity,
                p -> containerEntity.unpackLootTable(p),
                key(entity)
        );
    }

    public void useRandomDisc(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!MusicDiscHelper.isPendingDisc(held)) {
            return;
        }
        if (!pendingPlayers.add(player.getUUID())) {
            player.displayClientMessage(new TranslatableComponent("musicplayer.loot.already_generating").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        player.displayClientMessage(new TranslatableComponent("musicplayer.loot.generating").withStyle(ChatFormatting.GRAY), false);

        ItemStack baseDisc = held.copy();
        baseDisc.setCount(1);
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

        consumeTracks(1).whenComplete((tracks, throwable) -> {
            MinecraftServer srv = player.level.getServer();
            if (srv == null) {
                // 服务器已卸载：直接退回道具，避免玩家损失
                pendingPlayers.remove(player.getUUID());
                ItemStack returned = baseDisc.copy();
                returned.setCount(1);
                if (!player.getInventory().add(returned)) {
                    player.drop(returned, false, true);
                }
                return;
            }
            srv.execute(() -> {
                pendingPlayers.remove(player.getUUID());
                if (throwable != null || tracks == null || tracks.isEmpty()) {
                    MusicPlayerMod.LOGGER.warn("Failed to generate random music disc for player {}", player.getScoreboardName(), throwable);
                    ItemStack returned = baseDisc.copy();
                    returned.setCount(1);
                    if (!player.getInventory().add(returned)) {
                        player.drop(returned, false, true);
                    }
                    player.displayClientMessage(new TranslatableComponent("musicplayer.loot.failed_returned").withStyle(ChatFormatting.RED), false);
                    return;
                }
                giveBurnedDisc(player, baseDisc, tracks.get(0));
                refillPool();
            });
        });
    }

    public CompletableFuture<List<TrackInfo>> consumeTracks(int count) {
        List<TrackInfo> cached = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TrackInfo track = trackPool.poll();
            if (track == null) break;
            cached.add(track);
        }
        if (cached.size() >= count) {
            saveCache();
            refillPool();
            return CompletableFuture.completedFuture(cached);
        }
        int remaining = count - cached.size();
        return fetchTracks(remaining).thenApply(fetched -> {
            cached.addAll(fetched);
            saveCache();
            refillPool();
            return cached;
        });
    }

    private CompletableFuture<List<TrackInfo>> fetchTracks(int count) {
        if (sources.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        CompletableFuture<List<TrackInfo>> fallback = CompletableFuture.completedFuture(List.of());
        for (int i = 0; i < sources.size(); i++) {
            int index = sourceIndex.getAndIncrement() % sources.size();
            RandomSongSource source = sources.get(index);
            fallback = fallback.thenCompose(prev -> {
                if (!prev.isEmpty()) {
                    return CompletableFuture.completedFuture(prev);
                }
                return source.fetchRandomTracks(count)
                        .orTimeout(PER_SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            MusicPlayerMod.LOGGER.warn("Random source {} failed, trying next", source.getClass().getSimpleName(), ex);
                            return List.of();
                        });
            });
        }
        return fallback;
    }

    private boolean isStarted() {
        return !sources.isEmpty();
    }

    private void giveBurnedDisc(ServerPlayer player, ItemStack baseDisc, TrackInfo track) {
        ItemStack burnedDisc = MusicDiscHelper.burn(baseDisc, track);
        if (!player.getInventory().add(burnedDisc)) {
            player.drop(burnedDisc, false, true);
        }
        player.displayClientMessage(new TranslatableComponent("musicplayer.loot.generated").withStyle(ChatFormatting.GREEN)
                .append(new TextComponent(track.title()).withStyle(ChatFormatting.AQUA))
                .append(new TextComponent(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(new TextComponent(track.artist()).withStyle(ChatFormatting.GRAY)), false);
    }

    private void refillPool() {
        if (trackPool.size() >= REFILL_THRESHOLD || !poolRefilling.compareAndSet(false, true)) {
            return;
        }
        int deficit = POOL_CAPACITY - trackPool.size();
        int batchSize = Math.max(REFILL_BATCH, deficit);
        CompletableFuture<List<TrackInfo>> combined = CompletableFuture.completedFuture(List.of());
        for (int i = 0; i < sources.size(); i++) {
            int index = sourceIndex.getAndIncrement() % sources.size();
            RandomSongSource source = sources.get(index);
            combined = combined.thenCompose(prev -> {
                if (!prev.isEmpty()) {
                    return CompletableFuture.completedFuture(prev);
                }
                return source.fetchRandomTracks(batchSize)
                        .orTimeout(PER_SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            MusicPlayerMod.LOGGER.warn("Refill source {} failed, trying next", source.getClass().getSimpleName(), ex);
                            return List.of();
                        });
            });
        }
        combined.whenComplete((tracks, throwable) -> {
            poolRefilling.set(false);
            if (throwable != null || tracks == null || tracks.isEmpty()) {
                return;
            }
            trackPool.addAll(tracks);
            while (trackPool.size() > POOL_CAPACITY) {
                trackPool.poll();
            }
            saveCache();
            MusicPlayerMod.LOGGER.info("Refilled random track pool with {} tracks (pool size: {})", tracks.size(), trackPool.size());
        });
    }

    private InteractionResult tryInjectOnOpen(
            ServerPlayer player,
            Container container,
            Consumer<ServerPlayer> unpackLoot,
            String containerKey
    ) {
        MinecraftServer srv = player.level.getServer();
        if (srv == null) {
            return InteractionResult.PASS;
        }
        load(srv);
        if (!processedContainers.add(containerKey)) {
            return InteractionResult.PASS;
        }
        save(srv);

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

        unpackLoot.accept(player);
        int inserted = insertPendingDiscs(container, player.level.random, count);
        if (inserted > 0) {
            MusicPlayerMod.LOGGER.info("Injected {} random music disc(s) into loot container: {}", inserted, containerKey);
            container.setChanged();
        }
        return InteractionResult.PASS;
    }

    private int insertPendingDiscs(Container container, java.util.Random random, int maxCount) {
        List<Integer> emptySlots = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                emptySlots.add(slot);
            }
        }
        int toInsert = Math.min(maxCount, emptySlots.size());
        if (toInsert == 0) {
            return 0;
        }
        for (int i = 0; i < toInsert; i++) {
            int pick = random.nextInt(emptySlots.size());
            int slot = emptySlots.remove(pick);
            String token = UUID.randomUUID().toString();
            ItemStack pendingDisc = MusicDiscHelper.createPendingDisc(
                    MusicDiscHelper.randomBaseDiscStack(random),
                    token
            );
            container.setItem(slot, pendingDisc);
        }
        return toInsert;
    }

    private void load(MinecraftServer srv) {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = storePath(srv);
        if (Files.notExists(path)) {
            return;
        }
        try {
            processedContainers.clear();
            Set<String> loadedSet = GSON.fromJson(Files.newBufferedReader(path), STRING_SET);
            if (loadedSet != null) {
                processedContainers.addAll(loadedSet);
            }
        } catch (Exception exception) {
            MusicPlayerMod.LOGGER.warn("Failed to read loot music disc injection state.", exception);
        }
    }

    private synchronized void save(MinecraftServer srv) {
        Path path = storePath(srv);
        try {
            FileAtomicWrite.writeString(path, GSON.toJson(processedContainers));
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.warn("Failed to save loot music disc injection state.", exception);
        }
    }

    private void loadCache() {
        if (server == null) return;
        Path path = cachePath(server);
        if (Files.notExists(path)) return;
        try {
            List<TrackInfo> loadedTracks = GSON.fromJson(Files.newBufferedReader(path), TRACK_LIST);
            if (loadedTracks != null) {
                trackPool.clear();
                for (TrackInfo track : loadedTracks) {
                    if (track != null) {
                        trackPool.add(track);
                    }
                }
            }
            while (trackPool.size() > POOL_CAPACITY) {
                trackPool.poll();
            }
            MusicPlayerMod.LOGGER.info("Loaded {} cached random tracks", trackPool.size());
        } catch (Throwable throwable) {
            // 缓存只是加速数据，读失败直接丢弃重建即可；Gson 反射失败抛的是
            // AssertionError（Error 而非 Exception），故这里必须覆盖到 Throwable。
            MusicPlayerMod.LOGGER.warn("Failed to read random track cache.", throwable);
            trackPool.clear();
        }
    }

    private synchronized void saveCache() {
        if (server == null) return;
        Path path = cachePath(server);
        try {
            List<TrackInfo> snapshot = new ArrayList<>(trackPool);
            FileAtomicWrite.writeString(path, GSON.toJson(snapshot));
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.warn("Failed to save random track cache.", exception);
        }
    }

    private static Path storePath(MinecraftServer srv) {
        return srv.getWorldPath(LevelResource.ROOT)
                .resolve("musicplayer")
                .resolve("loot-music-disc-containers.json");
    }

    private static Path cachePath(MinecraftServer srv) {
        return srv.getWorldPath(LevelResource.ROOT)
                .resolve("musicplayer")
                .resolve("random-track-cache.json");
    }

    private static String key(Level world, BlockPos pos) {
        return world.dimension().toString() + ":" + pos.asLong();
    }

    private static String key(Entity entity) {
        return entity.level.dimension().toString() + ":entity:" + entity.getUUID();
    }

    /** 按字段手动构造 TrackInfo，绕开 Gson 对 record 的反射写入。 */
    private static final class TrackInfoAdapter implements JsonDeserializer<TrackInfo> {
        @Override
        public TrackInfo deserialize(JsonElement json, java.lang.reflect.Type type,
                                     JsonDeserializationContext context) {
            if (json == null || !json.isJsonObject()) {
                return null;
            }
            JsonObject object = json.getAsJsonObject();
            List<String> urls = new ArrayList<>();
            JsonElement urlElement = object.get("sourceUrls");
            if (urlElement != null && urlElement.isJsonArray()) {
                for (JsonElement element : urlElement.getAsJsonArray()) {
                    if (element != null && !element.isJsonNull()) {
                        urls.add(element.getAsString());
                    }
                }
            }
            return new TrackInfo(
                    text(object, "id"),
                    text(object, "title"),
                    text(object, "artist"),
                    text(object, "artistId"),
                    text(object, "coverUrl"),
                    urls,
                    number(object, "durationMillis"));
        }

        private static String text(JsonObject object, String key) {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? "" : element.getAsString();
        }

        private static long number(JsonObject object, String key) {
            JsonElement element = object.get(key);
            if (element == null || element.isJsonNull()) {
                return 0L;
            }
            try {
                return element.getAsLong();
            } catch (RuntimeException ignored) {
                return 0L;
            }
        }
    }
}
