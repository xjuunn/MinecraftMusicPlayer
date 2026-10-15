package com.junhsiun.musicplayer.client.gui;

import com.junhsiun.musicplayer.client.config.MusicPlayerClientConfig;
import com.junhsiun.musicplayer.client.config.MusicPlayerClientConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundSource;

public class BgmVolumeSlider extends AbstractSliderButton {
    private static final Component LABEL = new TranslatableComponent("musicplayer.options.bgm_volume");

    public BgmVolumeSlider(int x, int y) {
        super(x, y, 150, 20, LABEL, musicplayer$clampVolume(MusicPlayerClientConfigManager.get().bgmVolume));
    }

    @Override
    public void updateMessage() {
        setMessage(musicplayer$label(this.value));
    }

    @Override
    public void applyValue() {
        MusicPlayerClientConfig config = MusicPlayerClientConfigManager.get();
        config.bgmVolume = musicplayer$clampVolume(this.value);
        MusicPlayerClientConfigManager.markDirty();
        Minecraft minecraft = Minecraft.getInstance();
        float volume = minecraft.options.getSoundSourceVolume(SoundSource.MUSIC);
        minecraft.getSoundManager().updateSourceVolume(SoundSource.MUSIC, volume);
    }

    private static Component musicplayer$label(double value) {
        if (value <= 0.0D) {
            return new TranslatableComponent("options.percent_value", LABEL, 0);
        }
        return new TranslatableComponent("options.percent_value", LABEL, (int) Math.round(value * 100.0D));
    }

    private static double musicplayer$clampVolume(double value) {
        if (Double.isNaN(value)) {
            return 0.7D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
