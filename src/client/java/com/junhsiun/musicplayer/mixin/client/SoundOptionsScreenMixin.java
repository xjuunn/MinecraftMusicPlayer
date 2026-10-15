package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.ClientBgmController;
import com.junhsiun.musicplayer.client.config.MusicPlayerClientConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {

    @Redirect(
            method = "init",
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
        boolean initial = MusicPlayerClientConfigManager.get().enableNetworkBgm;
        return OptionInstance.createBoolean(
                "musicplayer.options.enable_network_bgm",
                OptionInstance.noTooltip(),
                initial,
                value -> {
                    MusicPlayerClientConfigManager.get().enableNetworkBgm = value;
                    MusicPlayerClientConfigManager.save();
                    ClientBgmController.getInstance().onGlobalToggleChanged();
                });
    }

    private OptionInstance<Double> musicplayer$createBgmVolumeOption() {
        double initial = musicplayer$clampVolume(MusicPlayerClientConfigManager.get().bgmVolume);
        return new OptionInstance<>(
                "musicplayer.options.bgm_volume",
                OptionInstance.noTooltip(),
                SoundOptionsScreenMixin::musicplayer$volumeLabel,
                OptionInstance.UnitDouble.INSTANCE,
                initial,
                value -> {
                    MusicPlayerClientConfigManager.get().bgmVolume = musicplayer$clampVolume(value);
                    MusicPlayerClientConfigManager.markDirty();
                    Minecraft minecraft = Minecraft.getInstance();
                    float volume = minecraft.options.getSoundSourceVolume(SoundSource.MUSIC);
                    minecraft.getSoundManager().updateSourceVolume(SoundSource.MUSIC, volume);
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
