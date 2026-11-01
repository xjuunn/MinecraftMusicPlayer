package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.platform.NeteaseApiClient;
import com.junhsiun.musicplayer.platform.api.DirectMusicApi;
import com.junhsiun.musicplayer.platform.api.MusicApi;
import com.junhsiun.musicplayer.platform.api.MusicApiMode;
import com.junhsiun.musicplayer.platform.api.MusicApiRouter;
import com.junhsiun.musicplayer.platform.api.ProxyMusicApi;

import java.util.concurrent.CompletableFuture;

/**
 * 唱片母版界面的歌曲搜索：<b>客户端直连</b>，不经过服务端。
 *
 * <p>界面上敲关键词就能搜，多一次服务端往返没有意义；服务端只在真正刻录时按 ID
 * 解析曲目（见 {@code DiscMasterService}）。取数链路与命令侧一致：直连优先、
 * 失败回退到 {@link ClientBgmController} 解析出的后端地址。</p>
 *
 * <p>直连实现延迟创建：进存档前根本用不到，提前构造只会白白初始化音源探测链。</p>
 */
public final class DiscMasterSearchService {

    private static final DiscMasterSearchService INSTANCE = new DiscMasterSearchService();

    private volatile MusicApi api;

    private DiscMasterSearchService() {
    }

    public static DiscMasterSearchService getInstance() {
        return INSTANCE;
    }

    /** 每页条数，与服务端搜索保持一致。 */
    public static int pageSize() {
        int limit = MusicPlayerConfigManager.get().searchLimit;
        return limit < 1 ? 8 : limit;
    }

    /**
     * 搜索歌曲，{@code page} 从 1 开始。
     *
     * <p>返回 {@link MusicApi.SearchPage}：界面底部的"共 N 首"要用<b>总命中数</b>，
     * 而不是本页条数。数据来源给不出总数时 {@code total} 为 -1，界面就不显示这一段。</p>
     */
    public CompletableFuture<MusicApi.SearchPage> searchSongPage(String keyword, int page) {
        return api().searchSongPage(keyword, page);
    }

    private MusicApi api() {
        MusicApi current = api;
        if (current == null) {
            synchronized (this) {
                current = api;
                if (current == null) {
                    current = createApi();
                    api = current;
                }
            }
        }
        return current;
    }

    private static MusicApi createApi() {
        DirectMusicApi direct = new DirectMusicApi();
        ProxyMusicApi proxy = new ProxyMusicApi(
                new NeteaseApiClient(() -> ClientBgmController.getInstance().currentApiBaseUrl()));
        return new MusicApiRouter(() -> direct, proxy, MusicApiMode::current);
    }
}
