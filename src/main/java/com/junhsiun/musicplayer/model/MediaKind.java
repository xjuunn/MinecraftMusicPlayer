package com.junhsiun.musicplayer.model;

/**
 * 浏览界面里的条目类型。
 *
 * <p>顺带承载标签页文案的翻译键：界面按类型排标签页，命令层也按类型区分"点开之后是详情页
 * 还是直接播放"，把这份对应关系放在枚举里，避免两个地方各写一遍 switch。</p>
 */
public enum MediaKind {

    SONG("musicplayer.browser.tab.song"),
    PLAYLIST("musicplayer.browser.tab.playlist"),
    ARTIST("musicplayer.browser.tab.artist"),
    USER("musicplayer.browser.tab.user"),
    RADIO("musicplayer.browser.tab.radio");

    private final String labelKey;

    MediaKind(String labelKey) {
        this.labelKey = labelKey;
    }

    /** 标签页 / 类型标签的翻译键。 */
    public String labelKey() {
        return labelKey;
    }

    /**
     * 是否有可打开的详情页。
     *
     * <p>单曲没有详情页：它就是一个点一下就播放的条目，硬给它做一页只会多一层无意义的跳转。</p>
     */
    public boolean hasDetail() {
        return this != SONG;
    }
}
