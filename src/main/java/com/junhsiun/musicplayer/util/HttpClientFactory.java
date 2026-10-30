package com.junhsiun.musicplayer.util;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.config.MusicPlayerConfig;
import com.junhsiun.musicplayer.config.MusicPlayerConfigManager;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class HttpClientFactory {
    private static final int API_CONNECT_TIMEOUT = 5;
    private static final int API_READ_TIMEOUT = 10;
    /** 音源探测单次请求上限：站点不可达时尽快失败，避免长期占用探测线程。 */
    private static final int PROBE_CONNECT_TIMEOUT = 3;
    private static final int PROBE_READ_TIMEOUT = 4;
    private static final int PROBE_CALL_TIMEOUT = 6;

    /** 缓存的 API 客户端，复用连接池，降低重复建连开销。 */
    private static volatile OkHttpClient cachedApiClient;
    private static volatile String cachedApiClientConfigKey = "";
    /** 缓存的音源探测客户端，超时远小于 API 客户端。 */
    private static volatile OkHttpClient cachedProbeClient;
    private static volatile String cachedProbeClientConfigKey = "";
    /** 缓存的全配置客户端（超时/代理/IPv4 完整生效），供播放与下载复用连接池。 */
    private static volatile OkHttpClient cachedFullClient;
    private static volatile String cachedFullClientConfigKey = "";

    private HttpClientFactory() {
    }

    public static OkHttpClient create() {
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        String configKey = config.connectTimeoutSeconds + "|" + config.readTimeoutSeconds + "|" + config.proxy
                + "|" + config.useSystemProxy + "|" + config.preferIpv4;
        OkHttpClient cached = cachedFullClient;
        if (cached != null && cachedFullClientConfigKey.equals(configKey)) {
            return cached;
        }
        synchronized (HttpClientFactory.class) {
            cached = cachedFullClient;
            if (cached != null && cachedFullClientConfigKey.equals(configKey)) {
                return cached;
            }
            OkHttpClient client = buildFullClient(config);
            cachedFullClient = client;
            cachedFullClientConfigKey = configKey;
            return client;
        }
    }

    private static OkHttpClient buildFullClient(MusicPlayerConfig config) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(config.readTimeoutSeconds))
                .callTimeout(Duration.ofSeconds(config.connectTimeoutSeconds + config.readTimeoutSeconds))
                .followRedirects(true)
                .followSslRedirects(true);

        Proxy resolvedProxy = resolveManualProxy(config.proxy);
        if (resolvedProxy != null) {
            builder.proxy(resolvedProxy);
        } else {
            if (config.useSystemProxy) {
                System.setProperty("java.net.useSystemProxies", "true");
            }

            Proxy environmentProxy = resolveEnvironmentProxy();
            if (environmentProxy != null) {
                builder.proxy(environmentProxy);
            } else if (config.useSystemProxy) {
                builder.proxySelector(ProxySelector.getDefault());
            }
        }

        if (config.preferIpv4) {
            builder.dns(hostname -> {
                List<InetAddress> all = Dns.SYSTEM.lookup(hostname);
                List<InetAddress> ipv4 = all.stream().filter(address -> address instanceof Inet4Address).toList();
                if (!ipv4.isEmpty()) {
                    return ipv4;
                }
                return all;
            });
        }

        return builder.build();
    }

    public static OkHttpClient createApiClient() {
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        String configKey = config.connectTimeoutSeconds + "|" + config.readTimeoutSeconds + "|" + config.proxy + "|" + config.useSystemProxy;
        OkHttpClient cached = cachedApiClient;
        if (cached != null && cachedApiClientConfigKey.equals(configKey)) {
            return cached;
        }
        synchronized (HttpClientFactory.class) {
            cached = cachedApiClient;
            if (cached != null && cachedApiClientConfigKey.equals(configKey)) {
                return cached;
            }
            OkHttpClient client = buildApiClient(config);
            cachedApiClient = client;
            cachedApiClientConfigKey = configKey;
            return client;
        }
    }

    private static OkHttpClient buildApiClient(MusicPlayerConfig config) {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(Math.min(config.connectTimeoutSeconds, API_CONNECT_TIMEOUT)))
                .readTimeout(Duration.ofSeconds(Math.min(config.readTimeoutSeconds, API_READ_TIMEOUT)))
                .callTimeout(Duration.ofSeconds(Math.min(config.connectTimeoutSeconds + config.readTimeoutSeconds, API_CONNECT_TIMEOUT + API_READ_TIMEOUT)))
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /**
     * 创建音源探测客户端。
     *
     * <p>第三方音源在弱网或不可达时常常要等到连接超时才返回，
     * {@code SongUrlResolver} 的 3 秒结果超时并不会中断底层阻塞请求，
     * 因此这里用更短的超时确保探测线程能快速释放。
     */
    public static OkHttpClient createProbeClient() {
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
        String configKey = config.connectTimeoutSeconds + "|" + config.readTimeoutSeconds;
        OkHttpClient cached = cachedProbeClient;
        if (cached != null && cachedProbeClientConfigKey.equals(configKey)) {
            return cached;
        }
        synchronized (HttpClientFactory.class) {
            cached = cachedProbeClient;
            if (cached != null && cachedProbeClientConfigKey.equals(configKey)) {
                return cached;
            }
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(Math.min(config.connectTimeoutSeconds, PROBE_CONNECT_TIMEOUT)))
                    .readTimeout(Duration.ofSeconds(Math.min(config.readTimeoutSeconds, PROBE_READ_TIMEOUT)))
                    .callTimeout(Duration.ofSeconds(Math.min(config.connectTimeoutSeconds + config.readTimeoutSeconds,
                            PROBE_CALL_TIMEOUT)))
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build();
            cachedProbeClient = client;
            cachedProbeClientConfigKey = configKey;
            return client;
        }
    }

    private static Proxy resolveManualProxy(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        Proxy proxy = parseProxy(value.trim());
        if (proxy == null) {
            MusicPlayerMod.LOGGER.warn("无效的代理格式，应为 host:port 或 http://host:port，当前值: {}", value);
        }
        return proxy;
    }

    private static Proxy resolveEnvironmentProxy() {
        for (String key : List.of("HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy", "ALL_PROXY", "all_proxy")) {
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                continue;
            }

            Proxy proxy = parseProxy(value.trim());
            if (proxy != null) {
                return proxy;
            }
        }
        return null;
    }

    private static Proxy parseProxy(String value) {
        try {
            if (value.contains("://")) {
                URI uri = URI.create(value);
                if (uri.getHost() == null || uri.getPort() <= 0) {
                    return null;
                }
                Proxy.Type type = uri.getScheme() != null
                        && uri.getScheme().toLowerCase(Locale.ROOT).startsWith("socks")
                        ? Proxy.Type.SOCKS
                        : Proxy.Type.HTTP;
                return new Proxy(type, new InetSocketAddress(uri.getHost(), uri.getPort()));
            }

            String[] parts = value.split(":");
            if (parts.length != 2) {
                return null;
            }

            int port = Integer.parseInt(parts[1]);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(parts[0], port));
        } catch (NumberFormatException exception) {
            MusicPlayerMod.LOGGER.warn("代理地址端口号无效: {}", value);
            return null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
