package com.junhsiun.musicplayer.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.platform.ArtistTopSongSource;
import com.junhsiun.musicplayer.platform.HotPlaylistRandomSource;
import com.junhsiun.musicplayer.platform.RandomSongSource;
import com.junhsiun.musicplayer.util.Java8;
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
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class LootMusicDiscService {
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
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "loot-disc-timeout");
        thread.setDaemon(true);
        return thread;
    });

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
        sources.add(new HotPlaylistRandomSource(MusicPlayerMod.netease()));
        sources.add(new ArtistTopSongSource(MusicPlayerMod.netease()));
        if (server != null) {
            loadCache();
        }
        refillPool();
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
        if (isStarted()) {
            loadCache();
        }
    }

    public InteractionResult tryInjectOnOpen(ServerPlayer player, Level world, BlockPos pos) {
        if (world.isClientSide()) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity)) {
            return InteractionResult.PASS;
        }
        RandomizableContainerBlockEntity randomizable = (RandomizableContainerBlockEntity) blockEntity;
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
        if (!(entity instanceof AbstractMinecartContainer)) {
            return InteractionResult.PASS;
        }
        AbstractMinecartContainer containerEntity = (AbstractMinecartContainer) entity;
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
            if (srv == null) return;
            srv.execute(() -> {
                pendingPlayers.remove(player.getUUID());
                if (throwable != null || tracks == null || tracks.isEmpty()) {
                    MusicPlayerMod.LOGGER.warn("Failed to generate random music disc for player {}", player.getScoreboardName(), throwable);
                    ItemStack returned = baseDisc.copy();
                    returned.setCount(1);
                    if (!player.inventory.add(returned)) {
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
            return CompletableFuture.completedFuture(Java8.listOf());
        }
        CompletableFuture<List<TrackInfo>> fallback = CompletableFuture.completedFuture(Java8.listOf());
        for (int i = 0; i < sources.size(); i++) {
            int index = sourceIndex.getAndIncrement() % sources.size();
            RandomSongSource source = sources.get(index);
            fallback = fallback.thenCompose(prev -> {
                if (!prev.isEmpty()) {
                    return CompletableFuture.completedFuture(prev);
                }
                // Java 9+ 的 orTimeout 不可用（目标 Java 8），用 ScheduledExecutorService 等价实现。
                return withTimeout(source.fetchRandomTracks(count))
                        .exceptionally(ex -> {
                            MusicPlayerMod.LOGGER.warn("Random source {} failed, trying next", source.getClass().getSimpleName(), ex);
                            return Java8.listOf();
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
        if (!player.inventory.add(burnedDisc)) {
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
        CompletableFuture<List<TrackInfo>> combined = CompletableFuture.completedFuture(Java8.listOf());
        for (int i = 0; i < sources.size(); i++) {
            int index = sourceIndex.getAndIncrement() % sources.size();
            RandomSongSource source = sources.get(index);
            combined = combined.thenCompose(prev -> {
                if (!prev.isEmpty()) {
                    return CompletableFuture.completedFuture(prev);
                }
                // Java 9+ 的 orTimeout 不可用（目标 Java 8），用 ScheduledExecutorService 等价实现。
                return withTimeout(source.fetchRandomTracks(batchSize))
                        .exceptionally(ex -> {
                            MusicPlayerMod.LOGGER.warn("Refill source {} failed, trying next", source.getClass().getSimpleName(), ex);
                            return Java8.listOf();
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

    private static CompletableFuture<List<TrackInfo>> withTimeout(CompletableFuture<List<TrackInfo>> future) {
        final CompletableFuture<List<TrackInfo>> result = new CompletableFuture<>();
        ScheduledFuture<?> timeoutTask = TIMEOUT_EXECUTOR.schedule(new Runnable() {
            @Override
            public void run() {
                result.completeExceptionally(new TimeoutException("随机曲目获取超时"));
            }
        }, PER_SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        future.whenComplete((tracks, ex) -> {
            timeoutTask.cancel(false);
            if (ex != null) {
                result.completeExceptionally(ex);
            } else {
                result.complete(tracks);
            }
        });
        return result;
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
            Set<String> loaded = GSON.fromJson(Files.newBufferedReader(path), STRING_SET);
            if (loaded != null) {
                processedContainers.addAll(loaded);
            }
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.warn("Failed to read loot music disc injection state.", exception);
        }
    }

    private void save(MinecraftServer srv) {
        Path path = storePath(srv);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, GSON.toJson(processedContainers).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.warn("Failed to save loot music disc injection state.", exception);
        }
    }

    private void loadCache() {
        if (server == null) return;
        Path path = cachePath(server);
        if (Files.notExists(path)) return;
        try {
            List<TrackInfo> loaded = GSON.fromJson(Files.newBufferedReader(path), TRACK_LIST);
            if (loaded != null) {
                trackPool.clear();
                trackPool.addAll(loaded);
            }
            while (trackPool.size() > POOL_CAPACITY) {
                trackPool.poll();
            }
            MusicPlayerMod.LOGGER.info("Loaded {} cached random tracks", trackPool.size());
        } catch (IOException exception) {
            MusicPlayerMod.LOGGER.warn("Failed to read random track cache.", exception);
        }
    }

    private void saveCache() {
        if (server == null) return;
        Path path = cachePath(server);
        try {
            Files.createDirectories(path.getParent());
            List<TrackInfo> snapshot = new ArrayList<>(trackPool);
            Files.write(path, GSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
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

    private static final class TrackInfoAdapter implements JsonSerializer<TrackInfo>, JsonDeserializer<TrackInfo> {
        @Override
        public JsonElement serialize(TrackInfo src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject root = new JsonObject();
            root.addProperty("id", src.id());
            root.addProperty("title", src.title());
            root.addProperty("artist", src.artist());
            root.addProperty("artistId", src.artistId());
            root.addProperty("coverUrl", src.coverUrl());
            JsonArray urls = new JsonArray();
            for (String url : src.sourceUrls()) {
                urls.add(new JsonPrimitive(url));
            }
            root.add("sourceUrls", urls);
            root.addProperty("durationMillis", src.durationMillis());
            return root;
        }

        @Override
        public TrackInfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject root = json.getAsJsonObject();
            JsonArray urlsElement = root.getAsJsonArray("sourceUrls");
            List<String> urls = new ArrayList<>();
            if (urlsElement != null) {
                for (JsonElement element : urlsElement) {
                    urls.add(element.getAsString());
                }
            }
            return new TrackInfo(
                    getString(root, "id"),
                    getString(root, "title"),
                    getString(root, "artist"),
                    getString(root, "artistId"),
                    getString(root, "coverUrl"),
                    urls,
                    root.has("durationMillis") && root.get("durationMillis").isJsonPrimitive()
                            ? root.get("durationMillis").getAsLong()
                            : 0L
            );
        }

        private static String getString(JsonObject root, String key) {
            return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : "";
        }
    }
}
