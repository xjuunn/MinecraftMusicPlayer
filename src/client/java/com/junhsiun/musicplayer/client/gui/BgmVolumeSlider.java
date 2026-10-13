package com.junhsiun.musicplayer.client.gui;

import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundSource;

public class BgmVolumeSlider extends AbstractSliderButton {

    public BgmVolumeSlider(int x, int y) {
        super(x, y, 150, 20, musicplayer$clampVolume(MusicPlayerConfigManager.get().bgmVolume));
    }

    @Override
    protected void updateMessage() {
        setMessage(musicplayer$label(this.value));
    }

    @Override
    protected void applyValue() {
        MusicPlayerConfigManager.get().bgmVolume = musicplayer$clampVolume(this.value);
        MusicPlayerConfigManager.save();
        Minecraft minecraft = Minecraft.getInstance();
        float volume = minecraft.options.getSoundSourceVolume(SoundSource.MUSIC);
        minecraft.getSoundManager().updateSourceVolume(SoundSource.MUSIC, volume);
    }

    private static String musicplayer$label(double value) {
        int percent = (int) Math.round(value * 100.0D);
        return I18n.get("musicplayer.options.bgm_volume") + ": " + percent + "%";
    }

    private static double musicplayer$clampVolume(double value) {
        if (Double.isNaN(value)) {
            return 0.7D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
