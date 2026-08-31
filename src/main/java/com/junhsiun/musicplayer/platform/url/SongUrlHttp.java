package com.junhsiun.musicplayer.platform.url;

import com.google.gson.JsonObject;

import java.io.IOException;

/**
 * URL 提供者依赖的最小 HTTP 能力抽象。
 *
 * <p>将实际网络调用与解析逻辑解耦，便于单元测试中用内存假实现替代真实请求。
 */
public interface SongUrlHttp {
    /** 请求 JSON 响应，非 2xx 或解析失败时抛 {@link IOException}。 */
    JsonObject getJson(String url, String... queryPairs) throws IOException;

    /** 请求纯文本响应，不可用时返回 null。 */
    String getText(String url, String... queryPairs) throws IOException;

    /** 仅探测响应的 Content-Type（小写），用于区分音频直链。 */
    String getContentType(String url) throws IOException;
}
