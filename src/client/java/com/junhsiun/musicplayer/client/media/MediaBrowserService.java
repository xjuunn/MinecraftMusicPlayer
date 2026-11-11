package com.junhsiun.musicplayer.client.media;

import com.junhsiun.musicplayer.model.MediaDetail;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import com.junhsiun.musicplayer.model.MediaPage;
import com.junhsiun.musicplayer.model.LyricLine;
import com.junhsiun.musicplayer.platform.LyricService;
import com.junhsiun.musicplayer.platform.NeteaseApiClient;
import com.junhsiun.musicplayer.platform.api.DirectMusicApi;
import com.junhsiun.musicplayer.platform.api.MusicApi;
import com.junhsiun.musicplayer.platform.api.MusicApiMode;
import com.junhsiun.musicplayer.platform.api.MusicApiRouter;
import com.junhsiun.musicplayer.platform.api.ProxyMusicApi;
import com.junhsiun.musicplayer.client.ClientBgmController;

import java.util.List;
import java.util.concurrent.CancellationException;
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
    /** 直连实现：浏览请求取消要靠它作废旧代号的在途请求。 */
    private volatile DirectMusicApi directApi;
    private final LyricService lyricService = new LyricService();

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

    /** 单曲歌词（LRC 时间轴）。取不到时以空列表完成，界面显示"暂无歌词"。 */
    public CompletableFuture<List<LyricLine>> lyrics(String songId) {
        if (songId == null || songId.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return lyricService.fetchLyrics(songId);
    }

    /**
     * 通知取数层「页面已经切走」：取消上一页还在排队/执行的浏览请求。
     *
     * <p>详见 {@link DirectMusicApi#beginNavigation()}。界面在压栈、返回、切换分区时调用。</p>
     */
    public void beginNavigation() {
        DirectMusicApi direct = directApi;
        if (direct != null) {
            direct.beginNavigation();
        }
    }

    /** 判断失败原因是不是「用户切页、请求被主动取消」——这种结果应该安静地忽略。 */
    public static boolean isCancelled(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof CancellationException) {
                return true;
            }
            Throwable next = cause.getCause();
            if (next == null || next == cause) {
                break;
            }
            cause = next;
        }
        return false;
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

    private MusicApi createApi() {
        DirectMusicApi direct = new DirectMusicApi();
        directApi = direct;
        ProxyMusicApi proxy = new ProxyMusicApi(
                new NeteaseApiClient(() -> ClientBgmController.getInstance().currentApiBaseUrl()));
        return new MusicApiRouter(() -> direct, proxy, MusicApiMode::current);
    }
}
