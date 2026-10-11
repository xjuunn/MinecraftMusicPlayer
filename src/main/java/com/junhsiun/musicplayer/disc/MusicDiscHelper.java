package com.junhsiun.musicplayer.disc;

import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.util.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class MusicDiscHelper {
    private static final String ROOT_KEY = "musicplayer_disc";
    private static final String PENDING_KEY = "musicplayer_pending_disc";
    private static final String PENDING_TOKEN_KEY = "musicplayer_pending_token";
    private static final String TRACK_ID_KEY = "track_id";
    private static final String TITLE_KEY = "title";
    private static final String ARTIST_KEY = "artist";
    private static final String ARTIST_ID_KEY = "artist_id";
    private static final String COVER_URL_KEY = "cover_url";
    private static final String DURATION_KEY = "duration";
    private static final String URLS_KEY = "urls";
    private static final Set<Item> SUPPORTED_DISCS = Set.of(
            Items.MUSIC_DISC_13,
            Items.MUSIC_DISC_CAT,
            Items.MUSIC_DISC_BLOCKS,
            Items.MUSIC_DISC_CHIRP,
            Items.MUSIC_DISC_FAR,
            Items.MUSIC_DISC_MALL,
            Items.MUSIC_DISC_MELLOHI,
            Items.MUSIC_DISC_STAL,
            Items.MUSIC_DISC_STRAD,
            Items.MUSIC_DISC_WARD,
            Items.MUSIC_DISC_11,
            Items.MUSIC_DISC_WAIT,
            Items.MUSIC_DISC_PIGSTEP
    );

    private MusicDiscHelper() {
    }

    public static boolean isBurnableDisc(ItemStack stack) {
        return stack != null && !stack.isEmpty() && SUPPORTED_DISCS.contains(stack.getItem());
    }

    public static boolean isMusicPlayerDisc(ItemStack stack) {
        return read(stack).isPresent();
    }

    public static boolean isPendingDisc(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return false;
        }
        return stack.getTag().getBoolean(PENDING_KEY);
    }

    public static String getPendingToken(ItemStack stack) {
        if (!isPendingDisc(stack)) {
            return "";
        }
        return stack.getTag().getString(PENDING_TOKEN_KEY);
    }

    public static ItemStack randomBaseDiscStack(Random random) {
        List<Item> discs = new ArrayList<>(SUPPORTED_DISCS);
        Item item = discs.get(random.nextInt(discs.size()));
        return new ItemStack(item);
    }

    public static ItemStack createPendingDisc(ItemStack baseDisc, String token) {
        ItemStack pendingDisc = baseDisc.copy();
        pendingDisc.setCount(1);
        CompoundTag tag = pendingDisc.getOrCreateTag();
        tag.putBoolean(PENDING_KEY, true);
        tag.putString(PENDING_TOKEN_KEY, safe(token));
        pendingDisc.setHoverName(new TranslatableComponent("musicplayer.disc.random").withStyle(ChatFormatting.LIGHT_PURPLE));
        return pendingDisc;
    }

    public static ItemStack burn(ItemStack baseDisc, TrackInfo track) {
        ItemStack burnedDisc = baseDisc.copy();
        burnedDisc.setCount(1);
        CompoundTag root = new CompoundTag();
        root.putString(TRACK_ID_KEY, safe(track.id()));
        root.putString(TITLE_KEY, safe(track.title()));
        root.putString(ARTIST_KEY, safe(track.artist()));
        root.putString(ARTIST_ID_KEY, safe(track.artistId()));
        root.putString(COVER_URL_KEY, safe(track.coverUrl()));
        root.putLong(DURATION_KEY, Math.max(0L, track.durationMillis()));
        ListTag urls = new ListTag();
        for (String url : track.sourceUrls()) {
            if (url != null && !url.isBlank()) {
                urls.add(StringTag.valueOf(url));
            }
        }
        root.put(URLS_KEY, urls);

        burnedDisc.getOrCreateTag().put(ROOT_KEY, root);
        burnedDisc.setHoverName(buildDisplayName(track));
        ListTag lore = buildLore(track, urls.size());
        if (!lore.isEmpty()) {
            burnedDisc.getOrCreateTagElement("display").put("Lore", lore);
        }
        return burnedDisc;
    }

    public static Optional<DiscTrackData> read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return Optional.empty();
        }
        CompoundTag wrapper = stack.getTag();
        if (!wrapper.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag root = wrapper.getCompound(ROOT_KEY);
        String title = root.getString(TITLE_KEY);
        String artist = root.getString(ARTIST_KEY);
        if (title.isBlank() || artist.isBlank()) {
            return Optional.empty();
        }
        List<String> urls = new ArrayList<>();
        if (root.contains(URLS_KEY, Tag.TAG_LIST)) {
            ListTag listTag = root.getList(URLS_KEY, Tag.TAG_STRING);
            for (int index = 0; index < listTag.size(); index++) {
                String value = listTag.getString(index);
                if (!value.isBlank()) {
                    urls.add(value);
                }
            }
        }
        return Optional.of(new DiscTrackData(
                root.getString(TRACK_ID_KEY),
                title,
                artist,
                root.getString(ARTIST_ID_KEY),
                root.getString(COVER_URL_KEY),
                urls,
                root.getLong(DURATION_KEY)
        ));
    }

    private static Component buildDisplayName(TrackInfo track) {
        return new TextComponent(track.title() + " - " + track.artist())
                .withStyle(ChatFormatting.AQUA);
    }

    private static ListTag buildLore(TrackInfo track, int sourceCount) {
        ListTag lore = new ListTag();
        addLoreLine(lore, new TranslatableComponent("musicplayer.disc.brand").withStyle(ChatFormatting.GOLD));
        addLoreLine(lore, new TranslatableComponent("musicplayer.disc.song", track.title()).withStyle(ChatFormatting.WHITE));
        addLoreLine(lore, new TranslatableComponent("musicplayer.disc.artist", track.artist()).withStyle(ChatFormatting.GRAY));
        if (track.artistId() != null && !track.artistId().isBlank()) {
            addLoreLine(lore, new TranslatableComponent("musicplayer.disc.artist_id", track.artistId()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (track.id() != null && !track.id().isBlank()) {
            addLoreLine(lore, new TranslatableComponent("musicplayer.disc.song_id", track.id()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (track.durationMillis() > 0L) {
            addLoreLine(lore, new TranslatableComponent("musicplayer.disc.duration", Messages.formatDuration(track.durationMillis())).withStyle(ChatFormatting.DARK_AQUA));
        }
        addLoreLine(lore, new TranslatableComponent("musicplayer.disc.source_count", sourceCount).withStyle(ChatFormatting.DARK_AQUA));
        return lore;
    }

    private static void addLoreLine(ListTag lore, Component line) {
        lore.add(StringTag.valueOf(Component.Serializer.toJson(line)));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record DiscTrackData(
            String trackId,
            String title,
            String artist,
            String artistId,
            String coverUrl,
            List<String> urls,
            long durationMillis
    ) {
    }
}
