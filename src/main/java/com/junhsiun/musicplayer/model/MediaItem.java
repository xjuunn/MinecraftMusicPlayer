package com.junhsiun.musicplayer.model;

/**
 * 浏览界面里的一条结果：歌曲、歌单、歌手、用户或播客。
 *
 * <p>和 {@link SearchEntry} 的区别只有一个 {@code coverUrl}，但那一个字段决定了界面能不能画出
 * 封面/头像。{@code SearchEntry} 是命令输出用的（聊天栏里只有文字），改动它要动几十处构造调用，
 * 所以浏览界面单独用这个模型，两条链路互不影响。</p>
 *
 * @param id       平台 ID
 * @param kind     条目类型
 * @param title    主标题（歌名 / 歌单名 / 昵称 / 播客名）
 * @param subtitle 副标题（歌手 / 创建者 / 签名），可为 {@code null}
 * @param coverUrl 封面或头像地址；数据来源没给时为空串，界面画占位
 * @param meta     右对齐的补充信息（歌曲时长、播放量等），可为 {@code null}
 */
public record MediaItem(String id, MediaKind kind, String title, String subtitle, String coverUrl,
                        String meta) {

    public MediaItem {
        id = id == null ? "" : id;
        kind = kind == null ? MediaKind.SONG : kind;
        title = title == null ? "" : title;
        coverUrl = coverUrl == null ? "" : coverUrl;
        subtitle = subtitle == null || subtitle.isBlank() ? null : subtitle;
        meta = meta == null || meta.isBlank() ? null : meta;
    }

    public MediaItem(String id, MediaKind kind, String title, String subtitle, String coverUrl) {
        this(id, kind, title, subtitle, coverUrl, null);
    }

    public MediaItem(String id, MediaKind kind, String title, String subtitle) {
        this(id, kind, title, subtitle, "", null);
    }

    /** 有没有封可用；没有时界面画占位块，不去请求空地址。 */
    public boolean hasCover() {
        return !coverUrl.isBlank();
    }
}
