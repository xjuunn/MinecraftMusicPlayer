package com.junhsiun.musicplayer.platform.url;

import com.google.gson.JsonObject;

import java.util.concurrent.TimeUnit;

/**
 * URL 解析相关的轻量工具。
 */
public final class UrlUtil {
    /**
     * 单个音源一次解析的总耗时预算，与解析器的单音源超时保持一致。
     *
     * <p>音源站点不可达时，逐档重试会成倍放大等待时间并长期占用探测线程。
     * 超过该预算后直接放弃剩余音质档位，交由优先级更低的下一个音源。
     */
    public static final long PROVIDER_BUDGET_NANOS =
            TimeUnit.SECONDS.toNanos(SongUrlResolver.PROVIDER_TIMEOUT_SECONDS);

    private UrlUtil() {
    }

    /** 返回本次音源解析的截止时间戳（{@link System#nanoTime()} 基准）。 */
    public static long providerDeadline() {
        return System.nanoTime() + PROVIDER_BUDGET_NANOS;
    }

    /** 判断音源解析预算是否已用完。 */
    public static boolean isProviderBudgetExhausted(long deadlineNanos) {
        return System.nanoTime() >= deadlineNanos;
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

    /** 提取异常链最内层的消息，用于玩家可见提示；null 时返回翻译键。 */
    public static String rootMessageKey(Throwable throwable) {
        if (throwable == null) {
            return "musicplayer.common.unknown_error";
        }
        Throwable current = throwable;
        int depth = 0;
        while (current.getCause() != null && depth < 100) {
            current = current.getCause();
            depth++;
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }
}
