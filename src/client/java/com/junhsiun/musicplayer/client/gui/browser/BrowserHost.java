package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.client.gui.animation.HeroElement;
import com.junhsiun.musicplayer.model.MediaItem;

/**
 * 页面能对"外面的世界"做的所有事。
 *
 * <p>页面自己不碰界面控件、不碰网络客户端、也不碰命令发送：需要什么就调这里的一个方法。
 * 这样页面之间不会互相引用，新增页面也不会把 {@link MusicBrowserScreen} 越撑越大。</p>
 */
public interface BrowserHost {

    /** 压入一页（新页面从右侧滑入）。 */
    void push(BrowserPage page);

    /**
     * 压入一页，并带上源元素的屏幕几何，用于共享元素（Hero）过渡。
     *
     * <p>默认退回 {@link #push(BrowserPage)}：不关心动画的宿主不用实现。</p>
     */
    default void push(BrowserPage page, HeroElement origin) {
        push(page);
    }

    /** 返回上一页（上一页从左侧滑入）。栈里只剩一页时什么都不做。 */
    void pop();

    /** 还能不能返回。 */
    boolean canGoBack();

    /** 按条目类型发送点播命令，效果等同于在聊天栏里敲 {@code /music play ...}。 */
    void play(MediaItem item);

    /** 跳过当前歌曲，等同于在聊天栏执行 {@code /music skip}。 */
    void skip();

    /** 快进当前歌曲 10 秒，等同于在聊天栏执行 {@code /music seek 10}。 */
    void seekForward();

    /** 让外壳重新按当前页面的 {@link BrowserPage#chrome()} 摆一遍（一般不需要手动调）。 */
    void refreshChrome();
}
