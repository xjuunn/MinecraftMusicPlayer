package com.junhsiun.musicplayer.platform.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.junhsiun.musicplayer.platform.NeteaseApiClient;
import com.junhsiun.musicplayer.platform.url.SongUrlProvider;
import com.junhsiun.musicplayer.platform.url.UrlUtil;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 网易云官方播放地址提供者（直连）。
 *
 * <p>GET {@code /api/song/enhance/player/url?ids=[id]&br=999000} 返回最高可用码率的直链。
 * 作为直连模式下的首选音源排在第三方音源之前：官方直链最稳定，
 * 但 VIP 歌曲仍返回空，此时由后续第三方音源补位。
 */
public final class DirectSongUrlProvider implements SongUrlProvider {
    /** 999000 表示「尽可能高」，实际返回 128k/192k/320k 中最优可用档位。 */
    private static final String MAX_BITRATE = "999000";

    @Override
    public String name() {
        return "Direct";
    }

    @Override
    public CompletableFuture<List<String>> resolve(String songId) {
        return CompletableFuture.supplyAsync(() -> fetch(songId), NeteaseApiClient.executor());
    }

    private List<String> fetch(String songId) {
        try {
            JsonObject root = DirectHttp.get(NeteaseEndpoints.SONG_PLAYER_URL,
                    "ids", "[" + songId + "]", "br", MAX_BITRATE);
            JsonArray data = ApiJson.arr(root, "data");
            if (data == null || data.isEmpty() || !data.get(0).isJsonObject()) {
                return List.of();
            }
            String url = ApiJson.str(data.get(0).getAsJsonObject(), "url");
            return UrlUtil.isHttpUrl(url) ? List.of(url.trim()) : List.<String>of();
        } catch (Exception exception) {
            // 直连音源失败不应中断整条探测链，交由下一个提供者处理
            return List.of();
        }
    }
}
