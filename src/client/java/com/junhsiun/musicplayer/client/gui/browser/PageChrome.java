package com.junhsiun.musicplayer.client.gui.browser;

/**
 * 页面需要的外壳部件。
 *
 * <p>页面只声明"我要哪些部件"，具体怎么摆、要不要显示、切换时怎么动画，全部由
 * {@link MusicBrowserScreen} 统一处理。这样新增一个页面只需要实现
 * {@link BrowserPage#chrome()}，不用碰界面布局代码。</p>
 */
public enum PageChrome {

    /** 固定顶栏：返回按钮 + 页面标题。 */
    BACK_BAR,

    /** 只有标题的顶栏（侧边栏根页面用，不需要返回按钮）。 */
    TITLE_BAR,

    /** 搜索行：输入框 + 搜索按钮。 */
    SEARCH_BAR,

    /** 类型标签栏（歌曲 / 歌单 / 歌手 / 用户 / 播客）。 */
    TABS,

    /** 底部翻页栏：上一页 / 页码 / 下一页。 */
    PAGER
}
