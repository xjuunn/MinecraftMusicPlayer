package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 客户端网络检测入口：探测配置的音乐服务地址是否可达。
 * 由网络背景音乐流程在播放失败时触发，结果用于决定是否降级回原版音乐。
 */
public final class ClientNetworkChecker {
    public enum Status {
        /** 服务可达（任意 HTTP 响应均视为在线）。 */
        ONLINE,
        /** 无法连接、地址不存在或超时，视为离线并降级。 */
        OFFLINE,
        /** 其他未知情况，按在线尝试。 */
        UNKNOWN
    }

    private static volatile Status lastStatus = Status.UNKNOWN;

    private ClientNetworkChecker() {
    }

    public static CompletableFuture<Status> check() {
        CompletableFuture<Status> future = new CompletableFuture<>();
        String baseUrl = MusicPlayerConfigManager.get().neteaseBaseUrl;
        // 基于缓存的 API 客户端派生（共享连接池/分发线程），仅覆盖短超时
        OkHttpClient client = HttpClientFactory.createApiClient().newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(3))
                .callTimeout(Duration.ofSeconds(4))
                .build();
        Request request;
        try {
            request = new Request.Builder()
                    .url(baseUrl)
                    .header("User-Agent", "MinecraftMusicPlayer/" + com.junhsiun.musicplayer.MusicPlayerMod.VERSION)
                    .get()
                    .build();
        } catch (IllegalArgumentException exception) {
            lastStatus = Status.UNKNOWN;
            future.complete(Status.UNKNOWN);
            return future;
        }
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                Status status = isOffline(exception) ? Status.OFFLINE : Status.UNKNOWN;
                lastStatus = status;
                future.complete(status);
            }

            @Override
            public void onResponse(Call call, Response response) {
                response.close();
                lastStatus = Status.ONLINE;
                future.complete(Status.ONLINE);
            }
        });
        return future;
    }

    private static boolean isOffline(Throwable exception) {
        return exception instanceof UnknownHostException
                || exception instanceof ConnectException
                || exception instanceof SocketTimeoutException;
    }
}
