package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.ClientBgmController;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.SoundPreviewHandler;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {

    @Redirect(
            method = "addOptions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/OptionsList;addSmall([Lnet/minecraft/client/OptionInstance;)V",
                    ordinal = 0
            )
    )
    private void musicplayer$addBgmEntries(OptionsList list, OptionInstance<?>[] original) {
        OptionInstance<?>[] entries = new OptionInstance<?>[original.length + 2];
        entries[0] = musicplayer$createNetworkBgmToggle();
        entries[1] = musicplayer$createBgmVolumeOption();
        System.arraycopy(original, 0, entries, 2, original.length);
        list.addSmall(entries);
    }

    /** 是否使用网络背景音乐的全局开关（客户端本地设置）。 */
    private OptionInstance<Boolean> musicplayer$createNetworkBgmToggle() {
        boolean initial = MusicPlayerConfigManager.get().enableNetworkBgm;
        return OptionInstance.createBoolean(
                "musicplayer.options.enable_network_bgm",
                OptionInstance.noTooltip(),
                initial,
                value -> {
                    MusicPlayerConfigManager.get().enableNetworkBgm = value;
                    MusicPlayerConfigManager.save();
                    ClientBgmController.getInstance().onGlobalToggleChanged();
                });
    }

    private OptionInstance<Double> musicplayer$createBgmVolumeOption() {
        double initial = musicplayer$clampVolume(MusicPlayerConfigManager.get().bgmVolume);
        return new OptionInstance<>(
                "musicplayer.options.bgm_volume",
                OptionInstance.noTooltip(),
                SoundOptionsScreenMixin::musicplayer$volumeLabel,
                OptionInstance.UnitDouble.INSTANCE,
                initial,
                value -> {
                    MusicPlayerConfigManager.get().bgmVolume = musicplayer$clampVolume(value);
                    MusicPlayerConfigManager.save();
                    Minecraft minecraft = Minecraft.getInstance();
                    SoundManager soundManager = minecraft.getSoundManager();
                    if (minecraft.options.getFinalSoundSourceVolume(SoundSource.MUSIC) > 0.0F) {
                        minecraft.getMusicManager().showNowPlayingToastIfNeeded();
                    }
                    soundManager.refreshCategoryVolume(SoundSource.MUSIC);
                    if (minecraft.level == null) {
                        SoundPreviewHandler.preview(soundManager, SoundSource.MUSIC, value.floatValue());
                    }
                });
    }

    private static double musicplayer$clampVolume(double value) {
        if (Double.isNaN(value)) {
            return 0.7D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static Component musicplayer$volumeLabel(Component caption, double value) {
        if (value == 0.0D) {
            return Options.genericValueLabel(caption, CommonComponents.OPTION_OFF);
        }
        return Component.translatable("options.percent_value", caption, (int) (value * 100.0D));
    }
}
