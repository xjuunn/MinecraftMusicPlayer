package com.junhsiun;

import com.junhsiun.core.command.MusicCommand;
import com.junhsiun.core.utils.OkHttpUtil;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MusicPlayer implements ModInitializer {
    public static final String MOD_ID = "musicplayer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        initCommand();
    }

    private void initCommand() {
        new MusicCommand().register();
    }
}