package com.junhsiun.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

public class OkHttpUtil {
    private static OkHttpClient client = new OkHttpClient.Builder()
            .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7897)))
            .build();
    private static String Base_Url = "https://odlimemusicapi.vercel.app";

    public static <T> T get(String url, Class<T> clazz) throws IOException {
        return get(url, clazz, false);
    }

    public static <T> T get(String url, Class<T> clazz, Boolean ignoreBaseUrl) throws IOException {
        String resultStr = getString((ignoreBaseUrl ? "" : Base_Url) + url);
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
        client = new OkHttpClient.Builder().proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ip, port))).build();
    }

    public static String getProxy() {
        if (client.proxy() != null) {
            return client.proxy().toString();
        }
        return "";
    }

    public static String getBase_Url() {
        return Base_Url;
    }

    public static void setBase_Url(String base_Url) {
        Base_Url = base_Url;
    }
}
