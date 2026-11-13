package com.junhsiun.musicplayer.mixin.client;

import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读写 {@code Hud.isHidden}，供纯享模式临时隐藏 HUD 并原样恢复。 */
@Mixin(Hud.class)
public interface HudAccessor {

    @Accessor("isHidden")
    boolean musicplayer$isHidden();

    @Accessor("isHidden")
    void musicplayer$setHidden(boolean hidden);
}
