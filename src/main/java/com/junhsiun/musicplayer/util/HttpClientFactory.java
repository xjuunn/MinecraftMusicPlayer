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
import java.time.Duration;
import java.util.List;

public final class HttpClientFactory {
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

        if (config.proxy != null && !config.proxy.isBlank()) {
            String[] parts = config.proxy.trim().split(":");
            if (parts.length == 2) {
                try {
                    int port = Integer.parseInt(parts[1]);
                    builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(parts[0], port)));
                } catch (NumberFormatException exception) {
                    MusicPlayerMod.LOGGER.warn("无效的代理端口: {}", config.proxy);
                }
            } else {
                MusicPlayerMod.LOGGER.warn("无效的代理格式，应为 host:port，当前值: {}", config.proxy);
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
}
