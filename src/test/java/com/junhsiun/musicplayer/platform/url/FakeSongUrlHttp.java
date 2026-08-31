package com.junhsiun.musicplayer.platform.url;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用的内存 HTTP 假实现。
 *
 * <p>按 URL（忽略查询参数）匹配预设响应，与各 Provider 上报的 base URL 对应。
 * 同时记录每次调用的查询参数顺序，便于验证提供者是否正确传递音质档位等参数。
 * 支持触发异常以模拟网络失败。
 */
final class FakeSongUrlHttp implements SongUrlHttp {
    private final Map<String, String> jsonResponses = new HashMap<>();
    private final Map<String, String> textResponses = new HashMap<>();
    private final Map<String, String> contentTypes = new HashMap<>();
    private final Map<String, RuntimeException> errors = new HashMap<>();
    private final Map<String, List<List<String>>> callLog = new LinkedHashMap<>();

    /** 为完全匹配的 URL 设置 JSON 响应。 */
    void stubJson(String url, String json) {
        jsonResponses.put(url, json);
    }

    /** 为完全匹配的 URL 设置纯文本响应。 */
    void stubText(String url, String text) {
        textResponses.put(url, text);
    }

    /** 为完全匹配的 URL 设置 Content-Type（GET 探测）。 */
    void stubContentType(String url, String contentType) {
        contentTypes.put(url, contentType);
    }

    /** 为完全匹配的 URL 设置抛出的异常。 */
    void stubError(String url, RuntimeException error) {
        errors.put(url, error);
    }

    /** 记录一次对给定 URL 的调用（含查询参数），提高测试可观测性。 */
    private void log(String url, String... queryPairs) {
        callLog.computeIfAbsent(url, k -> new ArrayList<>()).add(List.of(queryPairs));
    }

    /** 返回某 URL 下所有被调用的查询参数组合（按调用顺序），用于校验探测顺序。 */
    List<List<String>> callParams(String url) {
        return callLog.getOrDefault(url, List.of());
    }

    /** 返回某 URL 是否被调用过。 */
    boolean wasCalled(String url) {
        return callLog.containsKey(url);
    }

    private String lookup(Map<String, String> map, String url) {
        if (errors.containsKey(url)) {
            throw errors.get(url);
        }
        return map.getOrDefault(url, null);
    }

    @Override
    public JsonObject getJson(String url, String... queryPairs) throws RuntimeException {
        log(url, queryPairs);
        String body = lookup(jsonResponses, url);
        if (body == null) {
            throw new RuntimeException("no stub for " + url);
        }
        return JsonParser.parseString(body).getAsJsonObject();
    }

    @Override
    public String getText(String url, String... queryPairs) throws RuntimeException {
        log(url, queryPairs);
        return lookup(textResponses, url);
    }

    @Override
    public String getContentType(String url) throws RuntimeException {
        log(url);
        String ct = lookup(contentTypes, url);
        return ct != null ? ct : "";
    }
}
