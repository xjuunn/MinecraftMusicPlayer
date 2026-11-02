package com.junhsiun.musicplayer.client.gui.browser;

import com.junhsiun.musicplayer.client.gui.media.MediaListView;
import com.junhsiun.musicplayer.model.MediaItem;
import com.junhsiun.musicplayer.model.MediaKind;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;

/**
 * 浏览窗口里的一个页面。
 *
 * <p><b>为什么要这层抽象：</b>以前"搜索结果"和"详情"是同一个控件里的两种模式，靠一个
 * {@code Mode} 枚举切换，页面一多就变成一堆 if。现在每个页面是一个独立对象：</p>
 *
 * <ul>
 *   <li>自己持有一份数据（关键词、页码、条目……），返回上一页时原样复用，不重新请求；</li>
 *   <li>自己持有一个 {@link MediaListView}，滚动位置也跟着页面走；</li>
 *   <li>只声明需要哪些外壳（{@link #chrome()}），布局由界面统一处理；</li>
 *   <li>只通过 {@link BrowserHost} 与外界交互（压栈、返回、点播）。</li>
 * </ul>
 *
 * <p>新增一个页面要做的事：继承本类 → 实现 {@link #chrome()}、{@link #apply()}、
 * {@link #onEnter(BrowserHost)} → 在需要的地方 {@code host.push(new YourPage(...))}。
 * 界面布局、切换动画、返回栈都不用改。</p>
 */
public abstract class BrowserPage implements MediaListView.Listener {

    private final MediaListView view = new MediaListView(0, 0, 1, 1, this);
    /** 由 {@link BrowserHost#push(BrowserPage)} 注入，页面里可以直接用。 */
    protected BrowserHost host;

    /** 本页的列表控件。布局由界面负责，页面只往里塞数据。 */
    public final MediaListView view() {
        return view;
    }

    /** 顶栏标题。没有 {@link PageChrome#BACK_BAR} 的页面不会显示它。 */
    public abstract Component title();

    /** 本页需要的外壳部件。 */
    public abstract EnumSet<PageChrome> chrome();

    /** 把当前数据推给列表。每次布局、每次数据变化后都会被调用，必须可重复调用。 */
    public abstract void apply();

    /** 压栈时调用：在这里发起数据加载。 */
    public void onEnter(BrowserHost host) {
    }

    /** 出栈时调用。 */
    public void onExit(BrowserHost host) {
    }

    /** 从子页面返回、重新成为栈顶时调用。默认重新推一遍数据（不重新请求）。 */
    public void onResume(BrowserHost host) {
        apply();
    }

    /**
     * 列表行被点击：默认打开这一条的详情页。
     *
     * <p>单曲没有详情页——点开只有一个封面和一行时长，不如直接不给入口（列表那边也不会给
     * 它画右箭头、不会给按下反馈）。要把它加入队列请用行尾的加号。</p>
     */
    @Override
    public void onItemClick(MediaItem item) {
        if (host == null || item == null || item.id().isBlank()) {
            return;
        }
        if (item.kind() == MediaKind.SONG) {
            return;
        }
        host.push(new DetailPage(item.kind(), item.id()));
    }

    /** 行尾"加入歌单"按钮：默认交给宿主发点播命令（有歌在播时即为追加到队列）。 */
    @Override
    public void onPlay(MediaItem item) {
        if (host != null) {
            host.play(item);
        }
    }

    /** 列表末尾的"加载更多"。默认什么都不做（搜索页是翻页，不是追加）。 */
    @Override
    public void onLoadMore() {
    }

    final void attach(BrowserHost host) {
        this.host = host;
    }

    /** 数据变了、外壳可能需要跟着变（例如翻页栏该不该出现、顶栏标题换了）时调一下。 */
    protected final void refreshChrome() {
        if (host != null) {
            host.refreshChrome();
        }
    }
}
