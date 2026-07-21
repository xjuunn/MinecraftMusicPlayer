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

    private HttpClientFactory() {
    }

    public static OkHttpClient create() {
        MusicPlayerConfig config = MusicPlayerConfigManager.get();
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
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(Math.min(config.connectTimeoutSeconds, API_CONNECT_TIMEOUT)))
                .readTimeout(Duration.ofSeconds(Math.min(config.readTimeoutSeconds, API_READ_TIMEOUT)))
                .callTimeout(Duration.ofSeconds(Math.min(config.connectTimeoutSeconds + config.readTimeoutSeconds, API_CONNECT_TIMEOUT + API_READ_TIMEOUT)))
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
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
