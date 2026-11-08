package com.junhsiun.musicplayer.client.gui.icon;

/**
 * 图标清单：所有界面图标都在这里以 SVG 路径声明，绘制处只引用常量。
 *
 * <p>路径直接照抄 SVG 的 {@code <path d="...">}，视口统一为 24×24。只写需要着色的那条路径——
 * 用来占位的 {@code <path fill="none" d="M0 0h24v24H0z"/>} 不参与绘制，不必抄进来。</p>
 */
public final class SvgIcons {

    /**
     * 放大镜（搜索框左侧）。
     *
     * <p>像素画风格：11 个 2×2 的实心方块拼出圆角方形镜片与 45° 阶梯斜柄，没有描边也没有曲线，
     * 所以小尺寸下边缘依旧是硬的。</p>
     */
    public static final SvgIcon SEARCH = SvgIcon.of(
            "M6 2h8v2H6z M4 4h2v2H4z M14 4h2v2h-2z M2 6h2v8H2z M16 6h2v8h-2z"
                    + " M4 14h2v2H4z M14 14h2v2h-2z M6 16h8v2H6z M16 16h2v2h-2z"
                    + " M18 18h2v2h-2z M20 20h2v2h-2z");

    /**
     * 加入播放队列（列表行尾按钮）。
     *
     * <p>一条竖脊加一串错位的方块，读起来就是「列表」；点它等价于 {@code /music play song <id>}，
     * 有空闲队列时是追加、没有时是开始播放。</p>
     */
    public static final SvgIcon QUEUE_ADD = SvgIcon.of(
            "M15 11h-2V9h2zm0 4h-2v-2h2zm-2 2h-2v-2h2zm0-8h-2V7h2zm-2-2H9V5h2z"
                    + "M9 21H7V3h2zm6-8h2v-2h-2zm-6 4h2v2H9z");

    /**
     * 唱片（唱片母版界面的行尾按钮）。
     *
     * <p>和 {@link #QUEUE_ADD} 一样是像素画，但读起来是"一张盘"：一圈 2 单位宽的阶梯方框拼出
     * 盘面边缘，中间一小块是标签孔。母版界面的行尾按钮动的是刻录，不能沿用"列表加号"那个图标
     * （会让人以为点下去是加入播放队列）。</p>
     */
    public static final SvgIcon DISC = SvgIcon.of(
            "M8 2h8v2H8z M6 4h2v2H6z M16 4h2v2h-2z M4 6h2v2H4z M18 6h2v2h-2z"
                    + "M2 8h2v8H2z M20 8h2v8h-2z M4 16h2v2H4z M18 16h2v2h-2z"
                    + "M6 18h2v2H6z M16 18h2v2h-2z M8 20h8v2H8z"
                    + "M11 11h2v2h-2z");

    private SvgIcons() {
    }
}
