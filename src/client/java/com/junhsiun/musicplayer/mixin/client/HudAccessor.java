package com.junhsiun.musicplayer.mixin.client;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读写 {@code Options.hideGui}，供纯享模式临时隐藏 HUD 并原样恢复（1.21.1：开关在 Options 上）。 */
@Mixin(Options.class)
public interface HudAccessor {

    @Accessor("hideGui")
    boolean musicplayer$isHidden();

    @Accessor("hideGui")
    void musicplayer$setHidden(boolean hidden);
}
