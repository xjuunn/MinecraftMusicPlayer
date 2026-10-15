package com.junhsiun.musicplayer.client.gui;

import com.junhsiun.musicplayer.client.config.MusicPlayerClientConfig;
import com.junhsiun.musicplayer.client.config.MusicPlayerClientConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundSource;

public class BgmVolumeSlider extends AbstractSliderButton {

    public BgmVolumeSlider(int x, int y) {
        super(x, y, 150, 20, musicplayer$clampVolume(MusicPlayerClientConfigManager.get().bgmVolume));
    }

    @Override
    protected void updateMessage() {
        setMessage(musicplayer$label(this.value));
    }

    @Override
    protected void applyValue() {
        MusicPlayerClientConfig config = MusicPlayerClientConfigManager.get();
        config.bgmVolume = musicplayer$clampVolume(this.value);
        MusicPlayerClientConfigManager.markDirty();
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
