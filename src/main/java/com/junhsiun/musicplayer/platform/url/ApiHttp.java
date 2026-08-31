package com.junhsiun.musicplayer.platform.url;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Objects;

/**
 * 面向网易云 API / 第三方音源的轻量 GET 帮助类。
 *
 * <p>统一封装带查询参数的 GET 请求、JSON 与纯文本响应读取，复用缓存的底层客户端。
 * 所有方法均为阻塞调用，供异步执行器内使用。
 */
public final class ApiHttp {
    private static final String USER_AGENT = "MinecraftMusicPlayer/2.0";
    private static final String JSON_ACCEPT = "application/json,text/plain,*/*";

    private ApiHttp() {
    }

    /** 发起 GET 请求并解析为 JSON 对象；非 2xx 或非法 JSON 时抛异常。 */
    public static JsonObject getJson(String url, String... queryPairs) throws IOException {
        Request request = buildRequest(url, JSON_ACCEPT, queryPairs);
        try (Response response = execute(request)) {
            return JsonParser.parseString(Objects.requireNonNull(response.body()).string()).getAsJsonObject();
        }
    }

    /** 发起 GET 请求返回纯文本响应；非 2xx 或空文本时返 null。 */
    public static String getText(String url, String... queryPairs) throws IOException {
        Request request = buildRequest(url, "text/plain,*/*", queryPairs);
        try (Response response = execute(request)) {
            String body = Objects.requireNonNull(response.body()).string().trim();
            if (body.isEmpty() || body.startsWith("404 ")) {
                return null;
            }
            return body;
        }
    }

    /**
     * 发起 GET 请求，仅用于探测响应是否成功及其 Content-Type。
     * 返回响应头 Content-Type（小写），调用方关闭响应体。
     */
    public static String getContentType(String url) throws IOException {
        Request request = buildRequest(url, JSON_ACCEPT);
        Response response = execute(request);
        try {
            String contentType = response.header("Content-Type", "");
            if (contentType == null) contentType = "";
            return contentType.toLowerCase();
        } finally {
            response.close();
        }
    }

    private static Request buildRequest(String url, String accept, String... queryPairs) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            throw new IllegalArgumentException("无效的 URL: " + url);
        }
        HttpUrl.Builder builder = parsed.newBuilder();
        for (int index = 0; index + 1 < queryPairs.length; index += 2) {
            builder.addQueryParameter(queryPairs[index], queryPairs[index + 1]);
        }
        return new Request.Builder()
                .url(builder.build())
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .get()
                .build();
    }

    private static Response execute(Request request) throws IOException {
        OkHttpClient client = HttpClientFactory.createApiClient();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            response.close();
            throw new IOException("HTTP " + response.code() + " " + request.url());
        }
        return response;
    }
}
