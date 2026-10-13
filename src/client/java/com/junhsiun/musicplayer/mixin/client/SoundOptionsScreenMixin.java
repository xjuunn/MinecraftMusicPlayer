package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.ClientBgmController;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.client.gui.BgmVolumeSlider;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin extends Screen {

    protected SoundOptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void musicplayer$addBgmEntries(CallbackInfo ci) {
        int y = (this.height / 6 - 12) + 144;
        this.addButton(new Button(this.width / 2 - 155, y, 150, 20,
                musicplayer$toggleLabel(), button -> {
                    MusicPlayerConfigManager.get().enableNetworkBgm = !MusicPlayerConfigManager.get().enableNetworkBgm;
                    MusicPlayerConfigManager.save();
                    ClientBgmController.getInstance().onGlobalToggleChanged();
                    button.setMessage(musicplayer$toggleLabel());
                }));
        this.addButton(new BgmVolumeSlider(this.width / 2 + 5, y));
    }

    private static Component musicplayer$toggleLabel() {
        boolean enabled = MusicPlayerConfigManager.get().enableNetworkBgm;
        return new TranslatableComponent("musicplayer.options.enable_network_bgm").append(": ")
                .append(enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
}
