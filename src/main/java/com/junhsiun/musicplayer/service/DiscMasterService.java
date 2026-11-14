package com.junhsiun.musicplayer.service;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.disc.MusicDiscHelper;
import com.junhsiun.musicplayer.model.TrackInfo;
import com.junhsiun.musicplayer.network.DiscMasterBurnResultPayload;
import com.junhsiun.musicplayer.platform.url.UrlUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 唱片母版刻录：界面上点"刻录"按钮走这里。
 *
 * <p>与 {@code /music burn song} 的区别只有两点：</p>
 * <ul>
 *   <li>只接受<b>唱片母版</b>，命令接受任意可刻录唱片；</li>
 *   <li>母版可能在副手（右键打开界面的判定覆盖双手），所以两只手都要看。</li>
 * </ul>
 *
 * <p>曲目由服务端按 ID 解析，客户端只传 ID，不传曲目内容。</p>
 */
public final class DiscMasterService {

    private DiscMasterService() {
    }

    /** 处理一次刻录请求；所有结果都会回一个 {@link DiscMasterBurnResultPayload} 给发起者。 */
    public static void burn(MinecraftServer server, ServerPlayer player, String songId) {
        if (songId == null || songId.isBlank()) {
            reply(player, false, "musicplayer.discmaster.burn_invalid");
            return;
        }
        if (masterHand(player) == null) {
            reply(player, false, "musicplayer.discmaster.need_disc");
            return;
        }
        MusicPlayerMod.netease().resolveSong(songId).whenComplete((track, throwable) -> server.execute(() -> {
            if (throwable != null) {
                MusicPlayerMod.LOGGER.warn("唱片母版刻录失败: songId={}, {}", songId, UrlUtil.rootMessage(throwable));
                reply(player, false, "musicplayer.discmaster.burn_error");
                return;
            }
            if (track == null || track.title() == null || track.title().isBlank()) {
                reply(player, false, "musicplayer.discmaster.burn_error");
                return;
            }
            // 解析期间玩家可能已经换手、丢掉或刻录过母版，落笔前再确认一次
            InteractionHand hand = masterHand(player);
            if (hand == null) {
                reply(player, false, "musicplayer.discmaster.disc_changed");
                return;
            }
            applyBurn(player, hand, track);
        }));
    }

    private static void applyBurn(ServerPlayer player, InteractionHand hand, TrackInfo track) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack burnedDisc = MusicDiscHelper.burn(held.copyWithCount(1), track);
        if (held.getCount() == 1) {
            player.setItemInHand(hand, burnedDisc);
        } else {
            held.shrink(1);
            if (!player.getInventory().add(burnedDisc)) {
                player.drop(burnedDisc, false, true);
            }
        }

        String name = track.title() + " - " + track.artist();
        player.sendSystemMessage(Component.translatable("musicplayer.burn.done").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(track.title()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(track.artist() == null ? "" : track.artist()).withStyle(ChatFormatting.GRAY)));
        reply(player, true, name);
    }

    /** 主手优先，其次副手；都没有母版时返回 {@code null}。 */
    private static InteractionHand masterHand(ServerPlayer player) {
        if (MusicDiscHelper.isDiscMaster(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (MusicDiscHelper.isDiscMaster(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static void reply(ServerPlayer player, boolean success, String message) {
        if (!ServerPlayNetworking.canSend(player, DiscMasterBurnResultPayload.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(player, new DiscMasterBurnResultPayload(success, message));
    }
}
