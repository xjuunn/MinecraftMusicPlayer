package com.junhsiun.musicplayer.model;

import java.util.List;

/**
 * 详情页的元数据（不含条目本体）。
 *
 * <p>条目另走 {@code mediaEntries} 分页拉取：歌单可能有上千首，把正文塞进详情里会让一次请求
 * 变成几兆，详情页的打开动画就全耗在等数据上了。</p>
 *
 * @param id          平台 ID
 * @param kind        条目类型
 * @param title       标题
 * @param subtitle    副标题（歌手 / 创建者 / 分类），可为 {@code null}
 * @param coverUrl    封面或头像，可为空串
 * @param description 简介，可为空串
 * @param stats       数据小标签，可为空列表
 */
public record MediaDetail(String id, MediaKind kind, String title, String subtitle, String coverUrl,
                          String description, List<MediaStat> stats) {

    public MediaDetail {
        id = id == null ? "" : id;
        kind = kind == null ? MediaKind.SONG : kind;
        title = title == null ? "" : title;
        subtitle = subtitle == null || subtitle.isBlank() ? null : subtitle;
        coverUrl = coverUrl == null ? "" : coverUrl;
        description = description == null ? "" : description;
        stats = stats == null ? List.of() : List.copyOf(stats);
    }

    /** 数据来源什么都没给时的占位，界面据此显示"查无此项"而不是空白页。 */
    public static MediaDetail empty(MediaKind kind, String id) {
        return new MediaDetail(id, kind, "", null, "", "", List.of());
    }

    public boolean hasCover() {
        return !coverUrl.isBlank();
    }

    public boolean isEmpty() {
        return title.isBlank() && description.isBlank() && stats.isEmpty();
    }
}
