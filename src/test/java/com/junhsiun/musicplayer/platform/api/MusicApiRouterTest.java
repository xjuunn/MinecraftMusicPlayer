package com.junhsiun.musicplayer.platform.api;

import com.junhsiun.musicplayer.model.SearchEntry;
import com.junhsiun.musicplayer.model.TrackInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicApiRouterTest {

    /** 只实现被测方法，其余抛 {@link UnsupportedOperationException} 以暴露误用。 */
    private static class StubMusicApi implements MusicApi {
        @Override
        public CompletableFuture<TrackInfo> resolveSong(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<TrackInfo> resolveRadioSong(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> searchSongs(String keyword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> searchSongs(String keyword, int page) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> searchArtists(String keyword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> searchArtists(String keyword, int page) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> searchPlaylists(String keyword, int page) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> searchUsers(String keyword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> searchUsers(String keyword, int page) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> searchRadios(String keyword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> searchRadios(String keyword, int page) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<com.junhsiun.musicplayer.model.PlaylistInfo> playlistDetail(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> playlistTracksPage(String playlistId, int offset, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<com.junhsiun.musicplayer.model.UserPlaylistView> userPlaylists(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<TrackInfo>> artistTopSongs(String artistId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> topArtists(int limit, int offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<com.junhsiun.musicplayer.model.ArtistInfo> artistDetail(String artistId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> artistSongsPage(String artistId, int offset, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<com.junhsiun.musicplayer.model.RadioInfo> radioDetail(String rid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<com.junhsiun.musicplayer.model.ProgramInfo>> radioPrograms(
                String rid, int limit, int offset, boolean asc) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<com.junhsiun.musicplayer.model.ProgramInfo> programDetail(String programId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> hotRadios(int limit, int offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<SearchEntry>> radioCategories() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<TrackInfo>> randomHotTracks(int count) {
            throw new UnsupportedOperationException();
        }
    }

    private static final SearchEntry DIRECT_ENTRY = new SearchEntry("1", "direct", "直连结果");
    private static final SearchEntry PROXY_ENTRY = new SearchEntry("2", "proxy", "后端结果");

    /** 记录调用次数，并可切换成功/失败；返回结果由构造参数决定。 */
    private static class SearchStub extends StubMusicApi {
        final AtomicInteger calls = new AtomicInteger();
        final SearchEntry entry;
        volatile boolean fail;

        SearchStub(SearchEntry entry) {
            this.entry = entry;
        }

        @Override
        public CompletableFuture<List<SearchEntry>> searchSongs(String keyword) {
            calls.incrementAndGet();
            if (fail) {
                return CompletableFuture.failedFuture(new MusicApiException("boom"));
            }
            return CompletableFuture.completedFuture(List.of(entry));
        }
    }

    private static SearchStub directStub() {
        return new SearchStub(DIRECT_ENTRY);
    }

    private static SearchStub proxyStub() {
        return new SearchStub(PROXY_ENTRY);
    }

    private static List<SearchEntry> await(CompletableFuture<List<SearchEntry>> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void 直连模式优先返回直连结果() throws Exception {
        SearchStub direct = directStub();
        SearchStub proxy = proxyStub();
        MusicApiRouter router = new MusicApiRouter(() -> direct, proxy, () -> MusicApiMode.DIRECT);

        List<SearchEntry> result = await(router.searchSongs("x"));
        assertEquals(DIRECT_ENTRY, result.get(0));
        assertEquals(1, direct.calls.get());
        assertEquals(0, proxy.calls.get());
    }

    @Test
    void 后端模式只走后端() throws Exception {
        SearchStub direct = directStub();
        SearchStub proxy = proxyStub();
        MusicApiRouter router = new MusicApiRouter(() -> direct, proxy, () -> MusicApiMode.PROXY);

        List<SearchEntry> result = await(router.searchSongs("x"));
        assertEquals(PROXY_ENTRY, result.get(0));
        assertEquals(0, direct.calls.get());
        assertEquals(1, proxy.calls.get());
    }

    @Test
    void 自动模式下直连成功不调用后端() throws Exception {
        SearchStub direct = directStub();
        SearchStub proxy = proxyStub();
        MusicApiRouter router = new MusicApiRouter(() -> direct, proxy, () -> MusicApiMode.AUTO);

        List<SearchEntry> result = await(router.searchSongs("x"));
        assertEquals(DIRECT_ENTRY, result.get(0));
        assertEquals(0, proxy.calls.get());
    }

    @Test
    void 自动模式下直连失败回退后端() throws Exception {
        SearchStub direct = directStub();
        direct.fail = true;
        SearchStub proxy = proxyStub();
        MusicApiRouter router = new MusicApiRouter(() -> direct, proxy, () -> MusicApiMode.AUTO);

        List<SearchEntry> result = await(router.searchSongs("x"));
        assertEquals(PROXY_ENTRY, result.get(0));
        assertEquals(1, direct.calls.get());
        assertEquals(1, proxy.calls.get());
    }

    @Test
    void 仅直连模式下失败不回退() {
        SearchStub direct = directStub();
        direct.fail = true;
        SearchStub proxy = proxyStub();
        MusicApiRouter router = new MusicApiRouter(() -> direct, proxy, () -> MusicApiMode.DIRECT);

        CompletableFuture<List<SearchEntry>> future = router.searchSongs("x");
        assertTrue(future.isCompletedExceptionally());
        assertEquals(0, proxy.calls.get());
    }

    @Test
    void 直连连续失败达到阈值后熔断并直接走后端() throws Exception {
        SearchStub direct = directStub();
        direct.fail = true;
        SearchStub proxy = proxyStub();
        MusicApiRouter router = new MusicApiRouter(() -> direct, proxy, () -> MusicApiMode.AUTO);

        // 前三次仍会尝试直连（各自回退后端）
        for (int i = 0; i < 3; i++) {
            await(router.searchSongs("x"));
        }
        assertEquals(3, direct.calls.get());
        assertTrue(router.isDirectCircuitOpen());

        // 熔断后不再尝试直连
        await(router.searchSongs("x"));
        assertEquals(3, direct.calls.get());
        assertEquals(4, proxy.calls.get());
    }

    @Test
    void 重置健康状态后恢复直连尝试() throws Exception {
        SearchStub direct = directStub();
        direct.fail = true;
        SearchStub proxy = proxyStub();
        MusicApiRouter router = new MusicApiRouter(() -> direct, proxy, () -> MusicApiMode.AUTO);

        for (int i = 0; i < 3; i++) {
            await(router.searchSongs("x"));
        }
        assertTrue(router.isDirectCircuitOpen());

        router.resetHealth();
        assertFalse(router.isDirectCircuitOpen());

        direct.fail = false;
        List<SearchEntry> result = await(router.searchSongs("x"));
        assertEquals(DIRECT_ENTRY, result.get(0));
        assertEquals(4, direct.calls.get());
    }

    @Test
    void 直连成功会清除失败计数() throws Exception {
        SearchStub direct = directStub();
        SearchStub proxy = proxyStub();
        MusicApiRouter router = new MusicApiRouter(() -> direct, proxy, () -> MusicApiMode.AUTO);

        direct.fail = true;
        await(router.searchSongs("x"));
        await(router.searchSongs("x"));
        direct.fail = false;
        await(router.searchSongs("x"));

        // 计数已被清零，再失败两次不会触发熔断
        direct.fail = true;
        await(router.searchSongs("x"));
        await(router.searchSongs("x"));
        assertFalse(router.isDirectCircuitOpen());
    }

    @Test
    void 直连实例构造抛异常时回退后端() throws Exception {
        SearchStub proxy = proxyStub();
        MusicApiRouter router = new MusicApiRouter(() -> {
            throw new IllegalStateException("直连不可用");
        }, proxy, () -> MusicApiMode.AUTO);

        List<SearchEntry> result = await(router.searchSongs("x"));
        assertEquals(PROXY_ENTRY, result.get(0));
        assertEquals(1, proxy.calls.get());
    }

    @Test
    void 仅直连模式下熔断不生效仍继续尝试() {
        SearchStub direct = directStub();
        direct.fail = true;
        SearchStub proxy = proxyStub();
        MusicApiRouter router = new MusicApiRouter(() -> direct, proxy, () -> MusicApiMode.DIRECT);

        for (int i = 0; i < 5; i++) {
            router.searchSongs("x");
        }
        // 仅直连模式不应被熔断短路：每次都应真正尝试直连
        assertEquals(5, direct.calls.get());
        assertEquals(0, proxy.calls.get());
    }

    @Test
    void mode反映当前配置() {
        MusicApiRouter router = new MusicApiRouter(MusicApiRouterTest::directStub, proxyStub(), () -> MusicApiMode.PROXY);
        assertEquals(MusicApiMode.PROXY, router.mode());
    }
}
