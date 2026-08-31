package com.junhsiun.musicplayer.platform.url;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlUtilTest {

    // ── isHttpUrl ─────────────────────────────────────────

    @Test
    void isHttpUrl_识别http与https() {
        assertTrue(UrlUtil.isHttpUrl("http://a.mp3"));
        assertTrue(UrlUtil.isHttpUrl("https://a.mp3"));
    }

    @Test
    void isHttpUrl_非HTTP协议返回false() {
        assertFalse(UrlUtil.isHttpUrl("ftp://a.mp3"));
        assertFalse(UrlUtil.isHttpUrl("file:///a.mp3"));
        assertFalse(UrlUtil.isHttpUrl("javascript:void(0)"));
    }

    @Test
    void isHttpUrl_非URL字符串返回false() {
        assertFalse(UrlUtil.isHttpUrl("not-url"));
        assertFalse(UrlUtil.isHttpUrl("http-only"));
        assertFalse(UrlUtil.isHttpUrl(""));
    }

    @Test
    void isHttpUrl_null返回false() {
        assertFalse(UrlUtil.isHttpUrl(null));
    }

    @Test
    void isHttpUrl_空格开头不视为有效() {
        assertFalse(UrlUtil.isHttpUrl(" https://a.mp3"));
    }

    @Test
    void isHttpUrl_HTTP大写不识别() {
        assertFalse(UrlUtil.isHttpUrl("HTTP://a.mp3"));
        assertFalse(UrlUtil.isHttpUrl("HTTPS://a.mp3"));
    }

    // ── firstUrl ──────────────────────────────────────────

    @Test
    void firstUrl_从data数组首个url读取() {
        JsonObject root = JsonParser.parseString("{\"data\":[{\"url\":\"https://x.mp3\"}]}").getAsJsonObject();
        assertEquals("https://x.mp3", UrlUtil.firstUrl(root));
    }

    @Test
    void firstUrl_url为null时返回null() {
        JsonObject root = JsonParser.parseString("{\"data\":[{\"url\":null}]}").getAsJsonObject();
        assertNull(UrlUtil.firstUrl(root));
    }

    @Test
    void firstUrl_缺少data时返回null() {
        assertNull(UrlUtil.firstUrl(JsonParser.parseString("{}").getAsJsonObject()));
    }

    @Test
    void firstUrl_根节点为null时返回null() {
        assertNull(UrlUtil.firstUrl(null));
    }

    @Test
    void firstUrl_data为空数组时返回null() {
        JsonObject root = JsonParser.parseString("{\"data\":[]}").getAsJsonObject();
        assertNull(UrlUtil.firstUrl(root));
    }

    @Test
    void firstUrl_data元素非对象时返回null() {
        JsonObject root = JsonParser.parseString("{\"data\":[\"string\"]}").getAsJsonObject();
        assertNull(UrlUtil.firstUrl(root));
    }

    @Test
    void firstUrl_data元素为数字时返回null() {
        JsonObject root = JsonParser.parseString("{\"data\":[123]}").getAsJsonObject();
        assertNull(UrlUtil.firstUrl(root));
    }

    @Test
    void firstUrl_url为非HTTP协议时返回null() {
        JsonObject root = JsonParser.parseString("{\"data\":[{\"url\":\"ftp://file.mp3\"}]}").getAsJsonObject();
        assertNull(UrlUtil.firstUrl(root));
    }

    @Test
    void firstUrl_data字段存在但非数组时返回null() {
        JsonObject root = JsonParser.parseString("{\"data\":\"string\"}").getAsJsonObject();
        assertNull(UrlUtil.firstUrl(root));
    }

    @Test
    void firstUrl_第二个元素有url但第一个没有时返回null() {
        JsonObject root = JsonParser.parseString("{\"data\":[{}, {\"url\":\"https://x.mp3\"}]}").getAsJsonObject();
        assertNull(UrlUtil.firstUrl(root));
    }

    @Test
    void firstUrl_url字段存在但非原始类型时返回null() {
        JsonObject root = JsonParser.parseString("{\"data\":[{\"url\":[\"a\"]}]}").getAsJsonObject();
        assertNull(UrlUtil.firstUrl(root));
    }

    @Test
    void firstUrl_url为http也有效() {
        JsonObject root = JsonParser.parseString("{\"data\":[{\"url\":\"http://insecure.mp3\"}]}").getAsJsonObject();
        assertEquals("http://insecure.mp3", UrlUtil.firstUrl(root));
    }

    // ── rootMessage ───────────────────────────────────────

    @Test
    void rootMessage_提取最内层原因消息() {
        RuntimeException inner = new RuntimeException("inner-msg");
        RuntimeException outer = new RuntimeException("outer", inner);
        assertEquals("inner-msg", UrlUtil.rootMessage(outer));
    }

    @Test
    void rootMessage_空消息回退类名() {
        RuntimeException e = new RuntimeException();
        assertEquals("RuntimeException", UrlUtil.rootMessage(e));
    }

    @Test
    void rootMessage_null输入返回默认消息() {
        assertEquals("未知错误", UrlUtil.rootMessage(null));
    }

    @Test
    void rootMessage_多层嵌套提取最内层() {
        Exception level3 = new RuntimeException("deepest");
        Exception level2 = new RuntimeException("mid", level3);
        Exception level1 = new RuntimeException("top", level2);
        assertEquals("deepest", UrlUtil.rootMessage(level1));
    }

    @Test
    void rootMessage_最内层无消息时用类名() {
        Exception level2 = new RuntimeException(new NullPointerException());
        assertEquals("NullPointerException", UrlUtil.rootMessage(level2));
    }

    @Test
    void rootMessage_最内层消息为空字符串时用类名() {
        Exception level2 = new RuntimeException("");
        assertEquals("RuntimeException", UrlUtil.rootMessage(level2));
    }

    @Test
    void rootMessage_最内层消息为纯空白时用类名() {
        Exception level2 = new RuntimeException("   ");
        assertEquals("RuntimeException", UrlUtil.rootMessage(level2));
    }
}
