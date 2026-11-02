package com.junhsiun.musicplayer.client.media;

import com.junhsiun.musicplayer.model.MediaKind;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从粘贴进来的网易云链接里认出"这是什么、ID 是多少"。
 *
 * <p>支持的形态（都是官方分享出来的实际样式）：</p>
 * <ul>
 *   <li>{@code https://music.163.com/song?id=1940308971&uct2=...}</li>
 *   <li>{@code https://music.163.com/#/playlist?id=2953901152}（带井号的旧版路由）</li>
 *   <li>{@code https://music.163.com/playlist?id=...}、{@code /artist?id=...}、{@code /user?id=...}、
 *       {@code /djradio?id=...}、{@code /djradio/...}、{@code y.music.163.com/m/song?id=...}</li>
 * </ul>
 *
 * <p>只认 ID：链接里的 {@code uct2} 之类参数是分享追踪用的，和取数无关，直接忽略。</p>
 */
public final class MediaLinks {

    /** 形如 {@code id=123} 或 {@code id=123&} 的参数。 */
    private static final Pattern ID_PARAM = Pattern.compile("[?&#]id=(\\d+)");
    /** 分享短链 {@code https://163cn.tv/xxxx} 不含 ID，解析不了。 */
    private static final Pattern HOST = Pattern.compile("(^|\\.)music\\.163\\.com$|^163cn\\.tv$");

    /**
     * 一条可直达的浏览目标。
     *
     * @param kind 类型
     * @param id   平台 ID
     */
    public record Target(MediaKind kind, String id) {
    }

    private MediaLinks() {
    }

    /** 解析输入；不像网易云链接时返回空。 */
    public static Optional<Target> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String text = input.trim();
        if (text.isEmpty() || !text.contains("163")) {
            return Optional.empty();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (!normalized.contains("music.163.com") && !normalized.contains("163cn.tv")) {
            return Optional.empty();
        }
        if (!hostMatches(normalized)) {
            return Optional.empty();
        }
        String id = firstId(text);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        MediaKind kind = kindOf(normalized);
        return kind == null ? Optional.empty() : Optional.of(new Target(kind, id));
    }

    private static boolean hostMatches(String normalized) {
        String withoutScheme = normalized.replaceFirst("^[a-z]+://", "");
        int slash = withoutScheme.indexOf('/');
        String host = slash >= 0 ? withoutScheme.substring(0, slash) : withoutScheme;
        return HOST.matcher(host).find();
    }

    private static String firstId(String text) {
        Matcher matcher = ID_PARAM.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // 少数分享串是 /song/1940308971 这种纯路径形式
        Matcher path = Pattern.compile("/(song|playlist|artist|user|djradio)/(\\d+)").matcher(text);
        return path.find() ? path.group(2) : "";
    }

    /**
     * 按路径判断类型。
     *
     * <p>顺序有讲究：{@code /djradio} 要排在 {@code /dj} 前面，{@code /playlist} 要排在
     * {@code /user} 前面（用户主页链接可能是 {@code /user/playlist?...}）。</p>
     */
    private static MediaKind kindOf(String normalized) {
        if (normalized.contains("/playlist")) {
            return MediaKind.PLAYLIST;
        }
        if (normalized.contains("/djradio") || normalized.contains("/dj/")) {
            return MediaKind.RADIO;
        }
        if (normalized.contains("/song")) {
            return MediaKind.SONG;
        }
        if (normalized.contains("/artist")) {
            return MediaKind.ARTIST;
        }
        if (normalized.contains("/user")) {
            return MediaKind.USER;
        }
        // 节目（/program）不单独支持：节目 ID 推不出所属播客，也没有节目详情接口；
        // 返回空让界面提示"无法识别这个链接"，比跳到一个必定失败的页面好。
        return null;
    }
}
