package com.junhsiun.musicplayer.disc;

import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.util.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
            Items.MUSIC_DISC_CREATOR,
            Items.MUSIC_DISC_CREATOR_MUSIC_BOX,
            Items.MUSIC_DISC_FAR,
            Items.MUSIC_DISC_LAVA_CHICKEN,
            Items.MUSIC_DISC_MALL,
            Items.MUSIC_DISC_MELLOHI,
            Items.MUSIC_DISC_STAL,
            Items.MUSIC_DISC_STRAD,
            Items.MUSIC_DISC_WARD,
            Items.MUSIC_DISC_11,
            Items.MUSIC_DISC_WAIT,
            Items.MUSIC_DISC_OTHERSIDE,
            Items.MUSIC_DISC_RELIC,
            Items.MUSIC_DISC_5,
            Items.MUSIC_DISC_PIGSTEP,
            Items.MUSIC_DISC_PRECIPICE,
            Items.MUSIC_DISC_TEARS,
            Items.MUSIC_DISC_BOUNCE
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
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return false;
        }
        CompoundTag wrapper = customData.copyTag();
        return wrapper.getBooleanOr(PENDING_KEY, false);
    }

    public static String getPendingToken(ItemStack stack) {
        if (!isPendingDisc(stack)) {
            return "";
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return "";
        }
        return customData.copyTag().getStringOr(PENDING_TOKEN_KEY, "");
    }

    public static ItemStack randomBaseDiscStack(RandomSource random) {
        List<Item> discs = new ArrayList<>(SUPPORTED_DISCS);
        Item item = discs.get(random.nextInt(discs.size()));
        return new ItemStack(item);
    }

    public static ItemStack createPendingDisc(ItemStack baseDisc, String token) {
        ItemStack pendingDisc = baseDisc.copyWithCount(1);
        CompoundTag wrapper = new CompoundTag();
        wrapper.putBoolean(PENDING_KEY, true);
        wrapper.putString(PENDING_TOKEN_KEY, safe(token));
        CustomData.set(DataComponents.CUSTOM_DATA, pendingDisc, wrapper);
        pendingDisc.set(DataComponents.CUSTOM_NAME, Component.literal("随机音乐唱片").withStyle(ChatFormatting.LIGHT_PURPLE));
        pendingDisc.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("右键使用以生成随机音乐").withStyle(ChatFormatting.DARK_GRAY)
        )));
        return pendingDisc;
    }

    public static ItemStack burn(ItemStack baseDisc, TrackInfo track) {
        ItemStack burnedDisc = baseDisc.copyWithCount(1);
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

        CompoundTag wrapper = new CompoundTag();
        wrapper.put(ROOT_KEY, root);
        CustomData.set(DataComponents.CUSTOM_DATA, burnedDisc, wrapper);
        burnedDisc.set(DataComponents.CUSTOM_NAME, buildDisplayName(track));
        burnedDisc.set(DataComponents.LORE, buildLore(track, urls.size()));
        return burnedDisc;
    }

    public static Optional<DiscTrackData> read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag wrapper = customData.copyTag();
        if (!wrapper.contains(ROOT_KEY)) {
            return Optional.empty();
        }
        CompoundTag root = wrapper.getCompoundOrEmpty(ROOT_KEY);
        String title = root.getStringOr(TITLE_KEY, "");
        String artist = root.getStringOr(ARTIST_KEY, "");
        if (title.isBlank() || artist.isBlank()) {
            return Optional.empty();
        }
        List<String> urls = new ArrayList<>();
        if (root.contains(URLS_KEY)) {
            ListTag listTag = root.getListOrEmpty(URLS_KEY);
            for (int index = 0; index < listTag.size(); index++) {
                String value = listTag.getStringOr(index, "");
                if (!value.isBlank()) {
                    urls.add(value);
                }
            }
        }
        return Optional.of(new DiscTrackData(
                root.getStringOr(TRACK_ID_KEY, ""),
                title,
                artist,
                root.getStringOr(ARTIST_ID_KEY, ""),
                root.getStringOr(COVER_URL_KEY, ""),
                urls,
                root.getLongOr(DURATION_KEY, 0L)
        ));
    }

    private static Component buildDisplayName(TrackInfo track) {
        return Component.literal(track.title() + " - " + track.artist())
                .withStyle(ChatFormatting.AQUA);
    }

    private static ItemLore buildLore(TrackInfo track, int sourceCount) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Minecraft Music Player").withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("歌曲: " + track.title()).withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal("作者: " + track.artist()).withStyle(ChatFormatting.GRAY));
        if (track.artistId() != null && !track.artistId().isBlank()) {
            lines.add(Component.literal("作者 ID: " + track.artistId()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (track.id() != null && !track.id().isBlank()) {
            lines.add(Component.literal("歌曲 ID: " + track.id()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (track.durationMillis() > 0L) {
            lines.add(Component.literal("时长: " + Messages.formatDuration(track.durationMillis())).withStyle(ChatFormatting.DARK_AQUA));
        }
        lines.add(Component.literal("音源数量: " + sourceCount).withStyle(ChatFormatting.DARK_AQUA));
        return new ItemLore(lines);
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
