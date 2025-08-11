package com.junhsiun.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junhsiun.core.config.ServerConfigManager;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

public class OkHttpUtil {
    public static OkHttpClient client = new OkHttpClient.Builder().build();

    public static <T> void get(String url, Class<T> clazz, HttpCallback<T> callback) {
        getString(url, new HttpCallback<String>() {
            @Override
            public void onSuccess(String response) {
                ObjectMapper mapper = new ObjectMapper();
                try {
                    callback.onSuccess(mapper.readValue(response, clazz));
                } catch (JsonProcessingException e) {
                    callback.onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    public static void getString(String url, HttpCallback<String> callback) {
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                ModLogger.error(e.toString());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                ResponseBody body = response.body();
                if (!response.isSuccessful()) {
                    ModLogger.error("错误的请求：" + url);
                    callback.onFailure(new IOException("错误的请求：" + url));
                    return;
                }
                if (body == null) {
                    callback.onFailure(new IOException("未收到返回数据：" + url));
                    return;
                }
                String string = body.string();
                if (string.isEmpty()) {
                    callback.onFailure(new IOException("未收到返回数据：" + url));
                    return;
                }
                callback.onSuccess(string);

            }
        });
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
