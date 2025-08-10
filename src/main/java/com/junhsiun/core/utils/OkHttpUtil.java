package com.junhsiun.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junhsiun.core.config.ServerConfigManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

public class OkHttpUtil {
    public static OkHttpClient client = new OkHttpClient.Builder().build();

    public static <T> T get(String url, Class<T> clazz) throws IOException {
        String resultStr = getString(url);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(resultStr, clazz);
    }

    public static String getString(String url) throws IOException {
        Request request = new Request.Builder().url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code: " + response);
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            return body.string();
        }
    }

    public static void setProxy(String ip, Integer port) {
        ServerConfigManager.getConfig().proxy = ip + ":" + port;
        ServerConfigManager.saveConfig();
        client = new OkHttpClient.Builder().proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ip, port))).build();
    }

    public static void setProxy(String proxy) {
        if (proxy.isEmpty()) return;
        try {
            String ip = proxy.split(":")[0];
            int port = Integer.parseInt(proxy.split(":")[1]);
            setProxy(ip, port);
        } catch (NumberFormatException e) {
            ModLogger.info("OkHttp设置代理失败");
        }

    }

    public static String getProxy() {
        if (client.proxy() != null) {
            return client.proxy().toString();
        }
        return "";
    }
}
