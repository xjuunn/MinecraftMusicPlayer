package com.junhsiun;

import com.junhsiun.core.channel.MusicChannel;
import com.junhsiun.core.command.CommandRegister;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicPlayer implements ModInitializer {
    public static final String MOD_ID = "musicplayer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegister.register(serverCommandSource -> {
            ServerPlayerEntity player = serverCommandSource.getPlayer();
            if (player != null) {
                MusicChannel.send(player, "你好客户端");
            }
            serverCommandSource.sendFeedback(() -> Text.literal("执行结束1"), true);
            return 1;
        });


    }
}