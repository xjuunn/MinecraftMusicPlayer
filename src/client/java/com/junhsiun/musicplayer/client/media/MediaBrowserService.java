package com.junhsiun.musicplayer.client.media;

import com.junhsiun.musicplayer.model.MediaDetail;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.junhsiun.musicplayer.model.MediaPage;
import com.junhsiun.musicplayer.platform.NeteaseApiClient;
import com.junhsiun.musicplayer.platform.api.DirectMusicApi;
import com.junhsiun.musicplayer.platform.api.MusicApi;
import com.junhsiun.musicplayer.platform.api.MusicApiMode;
import com.junhsiun.musicplayer.platform.api.MusicApiRouter;
import com.junhsiun.musicplayer.platform.api.ProxyMusicApi;
import com.junhsiun.musicplayer.client.ClientBgmController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 浏览界面的取数入口：<b>客户端直连</b>，不经过服务端。
 *
 * <p>搜索、翻页、看详情都是纯浏览行为，走服务端往返既慢又多一层要同步的状态；真正需要服务端
 * 参与的只有"播放"这类会改变世界状态的操作。取数链路与命令侧保持一致：直连优先、失败回退到
 * 配置里的后端地址（见 {@link MusicApiRouter}）。</p>
 *
 * <p>直连实现延迟创建：不进界面就完全不构造，避免提前初始化音源探测链。</p>
 */
public final class MediaBrowserService {

    private static final MediaBrowserService INSTANCE = new MediaBrowserService();

    private volatile MusicApi api;

    private MediaBrowserService() {
    }

    public static MediaBrowserService getInstance() {
        return INSTANCE;
    }

    /** 每页条数。浏览界面固定每页 30 条，与配置里的 {@code searchLimit} 无关。 */
    public static final int PAGE_SIZE = 30;

    /** 按类型搜索一页。 */
    public CompletableFuture<MediaPage> search(MediaKind kind, String keyword, int page, int limit) {
        return api().searchMedia(kind, keyword, page, limit);
    }

    /** 详情页元数据。 */
    public CompletableFuture<MediaDetail> detail(MediaKind kind, String id) {
        return api().mediaDetail(kind, id);
    }

    /** 详情页内的条目分页。 */
    public CompletableFuture<List<MediaItem>> entries(MediaKind kind, String id, int offset, int limit) {
        return api().mediaEntries(kind, id, offset, limit);
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
