package com.junhsiun.musicplayer.model;

import java.util.List;

/**
 * 一页浏览结果。
 *
 * @param items 本页条目
 * @param total 该关键词的总命中数；{@code <= 0} 表示数据来源没有提供，界面不显示总数，
 *              只按"本页是否满页"判断能不能翻到下一页
 */
public record MediaPage(List<MediaItem> items, int total) {

    public static final MediaPage EMPTY = new MediaPage(List.of(), -1);

    public MediaPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean hasTotal() {
        return total > 0;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
