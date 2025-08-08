package com.junhsiun.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.Serializers;
import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

public class OkHttpUtil {
    private static OkHttpClient client = new OkHttpClient.Builder()
            .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7897)))
            .build();
    private static String Base_Url = "https://odlimemusicapi.vercel.app";

    public static <T> T get(String url, Class<T> clazz) throws IOException {
        Request request = new Request.Builder().url(Base_Url + url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code: " + response);
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            String bodyStr = body.string();
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(bodyStr, clazz);
        }
    }

    public static String get(String url) throws IOException {
        Request request = new Request.Builder().url(Base_Url + url)
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
