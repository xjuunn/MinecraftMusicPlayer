package com.junhsiun.musicplayer.platform.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.util.HttpClientFactory;
import com.junhsiun.musicplayer.util.Messages;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * 网易云网页版（明文）接口的最小 HTTP 传输层。
 *
 * <p>只负责「拼 URL → 发请求 → 返回 JSON」，不含任何业务字段解析，
 * 便于 {@link DirectMusicApi} 专注数据映射，也便于将来替换传输实现（如补签名）。
 */
final class DirectHttp {
    private static final String ACCEPT = "application/json,text/plain,*/*";

    private DirectHttp() {
    }

    /** GET 请求并解析 JSON，非 2xx 或接口错误码时抛异常。 */
    static JsonObject get(String path, String... queryPairs) throws IOException {
        HttpUrl parsed = HttpUrl.parse(NeteaseEndpoints.HOST + path);
        if (parsed == null) {
            throw new MusicApiException("musicplayer.error.invalid_api_url");
        }
        HttpUrl.Builder builder = parsed.newBuilder();
        for (int index = 0; index + 1 < queryPairs.length; index += 2) {
            builder.addQueryParameter(queryPairs[index], queryPairs[index + 1]);
        }
        Request request = baseRequest(builder.build()).get().build();
        return execute(request);
    }

    /** POST 表单请求并解析 JSON；网易云部分接口只接受表单提交。 */
    static JsonObject post(String path, Map<String, String> form) throws IOException {
        HttpUrl parsed = HttpUrl.parse(NeteaseEndpoints.HOST + path);
        if (parsed == null) {
            throw new MusicApiException("musicplayer.error.invalid_api_url");
        }
        FormBody.Builder builder = new FormBody.Builder();
        form.forEach((key, value) -> {
            if (key != null && value != null) {
                builder.add(key, value);
            }
        });
        RequestBody body = builder.build();
        Request request = baseRequest(parsed).post(body).build();
        return execute(request);
    }

    private static Request.Builder baseRequest(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", NeteaseEndpoints.USER_AGENT)
                .header("Referer", NeteaseEndpoints.REFERER)
                .header("Accept", ACCEPT);
    }

    private static JsonObject execute(Request request) throws IOException {
        OkHttpClient client = HttpClientFactory.createApiClient();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new MusicApiException("HTTP " + response.code());
            }
            String body = Objects.requireNonNull(response.body()).string();
            // 1.14.4 运行时自带 Gson 2.8.0，静态方法 parseString 直到 2.8.6 才提供，
            // 这里沿用新版仍保留的实例方法，保证新旧 Gson 都能运行。
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            int code = ApiJson.intVal(root, "code");
            if (code != 0 && code != 200) {
                // 406 通常是网易云的频率限制，其他代码为参数或区域限制
                throw new MusicApiException("音乐接口返回 code=" + code);
            }
            return root;
        } catch (MusicApiException exception) {
            throw exception;
        } catch (Exception exception) {
            // 逐次失败由 {@link MusicApiRouter} 统计并在熔断时告警，这里保持 debug 级别避免刷屏
            MusicPlayerMod.LOGGER.debug("直连音乐接口失败: {} - {} ({})", request.method(),
                    Messages.sanitizeForLog(request.url().toString()), exception.getMessage());
            MusicPlayerMod.LOGGER.trace("直连音乐接口失败详情", exception);
            throw new IOException(exception);
        }
    }
}
