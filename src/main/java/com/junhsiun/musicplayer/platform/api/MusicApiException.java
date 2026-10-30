package com.junhsiun.musicplayer.platform.api;

/**
 * 音乐接口调用失败时抛出的异常。
 *
 * <p>消息保持简短可读，既可写入日志，也可直接展示给玩家（与原有的 HTTP 错误提示风格一致）。
 * {@link MusicApiRouter} 捕获本异常后回退到后端接口。
 */
public class MusicApiException extends RuntimeException {
    public MusicApiException(String message) {
        super(message);
    }

    public MusicApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
