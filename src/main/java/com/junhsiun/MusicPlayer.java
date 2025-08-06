package com.junhsiun;

import com.junhsiun.core.command.MusicCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 *  music
 *
 *      platform                    -- 选择音乐平台
 *          网易云
 *          QQ
 *          Spotify
 *              login               -- 登录平台
 *              logout              -- 登出平台
 *              set / get           -- 配置
 *                  baseurl         -- API Base URL
 *
 *      search                      -- 搜索
 *          网易云
 *          QQ
 *          Spotify
 *              song                -- 音乐
 *              playlist            -- 歌单
 *              user                -- 用户
 *
 *      play                        -- 播放
 *          网易云
 *          QQ
 *          Spotify
 *              [music_id]
 *      mute                        -- 本机静音
 *          always                  -- 一直静音
 *          once                    -- 静音当前这首歌
 *          cancel                  -- 取消静音
 *
 *      next                        -- 下一首
 *      stop                        -- 停止
 *      continue                    -- 继续
 *      UI                          -- 可视化
 *      join                        -- 加入
 *      leave                       -- 离开
 *      help                        -- 帮助
 *
 */