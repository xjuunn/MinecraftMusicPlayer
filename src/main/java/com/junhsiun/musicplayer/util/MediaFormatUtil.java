package com.junhsiun.musicplayer.util;

import java.util.Locale;
import java.util.Set;

/**
 * 音频格式判断的轻量工具。
 *
 * <p>客户端使用 JLayer（javazoom.jl，仅支持 MPEG 音频 Layer I/II/III，即 MP3）解码。
 * 本工具通过 URL 的扩展名预判某一直链是否可能为 MP3，用于在执行耗时播放前跳过
 * JLayer 无法解码的无损/其他格式（如 FLAC、M4A、AAC、WAV、OGG、OPUS 等）。
 */
public final class MediaFormatUtil {
    private static final Set<String> JLAYER_UNSUPPORTED = Set.of(
            "flac", "m4a", "aac", "wav", "wave", "ogg", "oga", "opus",
            "ape", "wma", "alac", "aiff", "aif", "amr", "mid", "midi", "mp4", "m4b"
    );

    private MediaFormatUtil() {
    }

    /**
     * 判断 URL 是否属于 JLayer 可解码的格式。
     *
     * @param url 直链地址
     * @return 可播放返回 true；URL 为空或扩展名为已知非 MP3 格式时返回 false。
     *         无扩展名或扩展名未知时按可播放处理（避免误杀与请求路径形式的直链）。
     */
    public static boolean isJlayerPlayable(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        String extension = extensionOf(lower);
        if (extension == null) {
            return true;
        }
        return !JLAYER_UNSUPPORTED.contains(extension);
    }

    /** 提取 URL 末尾的扩展名（不含点），无扩展名返回 null；仅对形如 /path/name.ext 识别。 */
    private static String extensionOf(String url) {
        int slash = url.lastIndexOf('/');
        int query = url.indexOf('?');
        int hash = url.lastIndexOf('#');
        String pathTail = url;
        if (query >= 0) {
            pathTail = url.substring(0, query);
        }
        if (hash >= 0) {
            int end = hash;
            if (end >= 0 && end < pathTail.length()) {
                pathTail = pathTail.substring(0, end);
            }
        }
        int lastDot = pathTail.lastIndexOf('.');
        if (lastDot < 0 || lastDot <= slash) {
            return null;
        }
        String ext = pathTail.substring(lastDot + 1);
        return ext.isEmpty() ? null : ext;
    }
}
