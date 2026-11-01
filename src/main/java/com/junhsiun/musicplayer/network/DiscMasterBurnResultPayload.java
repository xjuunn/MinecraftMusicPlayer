package com.junhsiun.musicplayer.network;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：刻录请求的处理结果。
 *
 * <p>失败原因、成功曲名都放在 {@code message} 里：既可能是翻译键
 * （{@code musicplayer.burn.need_disc} 之类），也可能是"歌名 - 作者"这样的纯文本，
 * 客户端用 {@code Messages.textOrTranslatable} 统一渲染。</p>
 *
 * <p>必须有这个回包：客户端点击刻录后会禁用按钮并显示"正在刻录"，没有结果回包就
 * 永远卡在那个状态。</p>
 */
public record DiscMasterBurnResultPayload(boolean success, String message) implements CustomPacketPayload {
    public static final Type<DiscMasterBurnResultPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "disc_master_burn_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiscMasterBurnResultPayload> CODEC =
            CustomPacketPayload.codec(DiscMasterBurnResultPayload::write, DiscMasterBurnResultPayload::new);

    public DiscMasterBurnResultPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.success);
        buffer.writeUtf(this.message == null ? "" : this.message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
