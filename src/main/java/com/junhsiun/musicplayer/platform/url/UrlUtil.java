package com.junhsiun.musicplayer.platform.url;

import com.google.gson.JsonObject;

/**
 * URL 解析相关的轻量工具。
 */
public final class UrlUtil {
    private UrlUtil() {
    }

    /** 判断是否为有效的 http(s) URL。 */
    public static boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /** 从响应根节点读取 {@code data[0].url}，缺失时返回 null。 */
    public static String firstUrl(JsonObject root) {
        if (root == null || !root.has("data") || !root.get("data").isJsonArray()) {
            return null;
        }
        var data = root.getAsJsonArray("data");
        if (data.isEmpty() || !data.get(0).isJsonObject()) {
            return null;
        }
        JsonObject first = data.get(0).getAsJsonObject();
        if (first.has("url") && first.get("url").isJsonPrimitive()) {
            String url = first.get("url").getAsString().trim();
            return isHttpUrl(url) ? url : null;
        }
        return null;
    }

    /** 提取异常链最内层的消息，用于日志。 */
    public static String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        Throwable current = throwable;
        int depth = 0;
        while (current.getCause() != null && depth < 100) {
            current = current.getCause();
            depth++;
        }
        String msg = current.getMessage();
        return msg != null && !msg.isBlank() ? msg : current.getClass().getSimpleName();
    }
}
