package com.junhsiun.musicplayer.platform.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 网易云各接口返回字段的空安全读取工具。
 *
 * <p>网易云不同接口对同一概念使用了不同字段名（新接口用 {@code ar}/{@code al}/{@code dt}，
 * 旧接口用 {@code artists}/{@code album}/{@code duration}），本类统一兼容这些差异，
 * 避免每个调用点各写一遍判断。
 */
public final class ApiJson {
    private ApiJson() {
    }

    public static JsonObject obj(JsonObject parent, String key) {
        if (parent == null) return null;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static JsonArray arr(JsonObject parent, String key) {
        if (parent == null) return null;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    public static String str(JsonObject parent, String key) {
        return str(parent, key, "");
    }

    public static String str(JsonObject parent, String key, String fallback) {
        if (parent == null) return fallback;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    /** 读取第一个非空字符串字段，用于兼容同一概念的不同字段名。 */
    public static String firstNonBlank(JsonObject parent, String... keys) {
        for (String key : keys) {
            String value = str(parent, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public static long lng(JsonObject parent, String key) {
        if (parent == null) return 0L;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsLong() : 0L;
    }

    public static int intVal(JsonObject parent, String key) {
        if (parent == null) return 0;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt() : 0;
    }

    /** 读取数组第一个元素的 ID，缺失时返回空串。 */
    public static String firstId(JsonArray array) {
        if (array == null || array.isEmpty() || !array.get(0).isJsonObject()) return "";
        return str(array.get(0).getAsJsonObject(), "id");
    }

    /** 首位歌手名，兼容 {@code ar} 与 {@code artists} 两种字段。 */
    public static String firstArtistName(JsonObject song) {
        String name = firstOf(song, "ar", "name");
        return name.isBlank() ? firstOf(song, "artists", "name") : name;
    }

    /** 首位歌手 ID，兼容 {@code ar} 与 {@code artists} 两种字段。 */
    public static String firstArtistId(JsonObject song) {
        String id = firstOf(song, "ar", "id");
        return id.isBlank() ? firstOf(song, "artists", "id") : id;
    }

    /** 专辑封面地址，兼容 {@code al} 与 {@code album} 两种字段。 */
    public static String albumCover(JsonObject song) {
        String cover = firstOf(song, "al", "picUrl");
        return cover.isBlank() ? firstOf(song, "album", "picUrl") : cover;
    }

    /** 专辑名，兼容 {@code al} 与 {@code album} 两种字段。 */
    public static String albumName(JsonObject song) {
        String name = firstOf(song, "al", "name");
        return name.isBlank() ? firstOf(song, "album", "name") : name;
    }

    /** 歌曲时长（毫秒），兼容 {@code dt} 与 {@code duration} 两种字段。 */
    public static long durationMillis(JsonObject song) {
        long duration = lng(song, "dt");
        return duration > 0L ? duration : lng(song, "duration");
    }

    /**
     * 读取"可能是对象、也可能是数组"的字段里的某个子字段。
     *
     * <p>网易云对同一概念有两种写法：歌手是数组（{@code ar}/{@code artists}），
     * 而专辑是<b>对象</b>（{@code al}/{@code album}）。早先这里只按数组读，
     * 于是 {@code al} 这种对象一律读不到，专辑封面和专辑名恒为空——
     * 封面在刻录时就是空串，之后无论怎么下载都不可能显示。</p>
     */
    private static String firstOf(JsonObject parent, String key, String fieldKey) {
        JsonObject node = obj(parent, key);
        if (node != null) {
            return str(node, fieldKey);
        }
        JsonArray array = arr(parent, key);
        if (array == null || array.isEmpty() || !array.get(0).isJsonObject()) return "";
        return str(array.get(0).getAsJsonObject(), fieldKey);
    }
}
