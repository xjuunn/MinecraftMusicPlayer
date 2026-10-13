package com.junhsiun.musicplayer.client.config;

/** 客户端本地配置（独立于服务端共享配置，仅客户端读写）。 */
public class MusicPlayerClientConfig {
    /** 背景音乐音量（原版与网络背景音乐），0.0 - 1.0，默认 70%。 */
    public volatile double bgmVolume = 0.7D;
    /** 是否启用网络背景音乐（客户端本地全局开关，在设置界面中切换；false 时背景音乐使用原版）。 */
    public volatile boolean enableNetworkBgm = true;
}
