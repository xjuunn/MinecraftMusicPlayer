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

    /** 我的歌单：列表。 */
    public static final SvgIcon PLAYLIST = SvgIcon.of(
            "M3 5h2v2H3z M7 5h14v2H7z M3 11h2v2H3z M7 11h14v2H7z M3 17h2v2H3z M7 17h10v2H7z");

    /** 当前队列：三条待播条目 + 右侧播放三角。 */
    public static final SvgIcon QUEUE = SvgIcon.of(
            "M3 5h11v2H3z M3 11h11v2H3z M3 17h11v2H3z"
                    + " M17 6h2v12h-2z M19 8h2v8h-2z M21 10h2v4h-2z");

    /** 最近播放：时钟。 */
    public static final SvgIcon HISTORY = SvgIcon.of(
            "M8 2h8v2H8z M6 4h2v2H6z M16 4h2v2h-2z M4 6h2v2H4z M18 6h2v2h-2z"
                    + " M2 8h2v8H2z M20 8h2v8h-2z M4 16h2v2H4z M18 16h2v2h-2z"
                    + " M6 18h2v2H6z M16 18h2v2h-2z M8 20h8v2H8z"
                    + " M11 7h2v6h-2z M11 11h5v2h-5z");

    /** 设置：三条带滑块的横线。 */
    public static final SvgIcon SETTINGS = SvgIcon.of(
            "M3 5h18v2H3z M3 11h18v2H3z M3 17h18v2H3z"
                    + " M7 3h3v6H7z M14 9h3v6h-3z M9 15h3v6H9z");

    /** 播放：右向三角。 */
    public static final SvgIcon PLAY = SvgIcon.of(
            "M7 4h2v16H7z M9 6h2v12H9z M11 8h2v8h-2z M13 10h2v4h-2z M15 11h2v2h-2z");

    /** 暂停：两条竖线。 */
    public static final SvgIcon PAUSE = SvgIcon.of(
            "M7 5h3v14H7z M14 5h3v14h-3z");

    /** 后退：左向三角 + 竖线。 */
    public static final SvgIcon REWIND = SvgIcon.of(
            "M4 5h2v14H4z M18 5h2v14h-2z M16 7h2v10h-2z M14 9h2v6h-2z M12 11h2v2h-2z");

    /** 前进：竖线 + 右向三角。 */
    public static final SvgIcon FORWARD = SvgIcon.of(
            "M6 5h2v14H6z M8 7h2v10H8z M10 9h2v6h-2z M12 11h2v2h-2z M18 5h2v14h-2z");

    /** 快进：连着的两个右向三角（双击箭头）。 */
    public static final SvgIcon FAST_FORWARD = SvgIcon.of(
            "M3 5h2v14H3z M5 7h2v10H5z M7 9h2v6H7z M9 11h2v2H9z"
                    + " M13 5h2v14h-2z M15 7h2v10h-2z M17 9h2v6h-2z M19 11h2v2h-2z");

    /** 跳过：右向三角 + 竖线。 */
    public static final SvgIcon SKIP = SvgIcon.of(
            "M4 5h2v14H4z M6 7h2v10H6z M8 9h2v6H8z M10 11h2v2h-2z M15 5h3v14h-3z");

    /** 收藏：像素心形。 */
    public static final SvgIcon HEART = SvgIcon.of(
            "M4 4h4v2H4z M16 4h4v2h-4z M2 6h2v4H2z M20 6h2v4h-2z"
                    + " M4 6h4v2H4z M16 6h4v2h-4z M6 8h12v2H6z M4 10h16v2H4z"
                    + " M6 12h12v2H6z M8 14h8v2H8z M10 16h4v2h-4z M12 18h2v2h-2z");

    /**
     * 收藏歌单：像素描边星形。
     *
     * <p>绘制层只支持轴对齐矩形，所以把图标源文件里的 L 形子路径逐格拆成了纯矩形块：
     * 五个尖角与中间的空心一起保留，形状和原 SVG 完全一致。</p>
     */
    public static final SvgIcon STAR = SvgIcon.of(
            "M11 1h2v2H11z M9 3h2v4H9z M13 3h2v4H13z"
                    + " M1 7h8v2H1z M15 7h8v2H15z M1 9h2v2H1z"
                    + " M21 9h2v2H21z M3 11h2v2H3z M19 11h2v2H19z"
                    + " M5 13h2v3H5z M17 13h2v3H17z M3 16h2v6H3z"
                    + " M10 16h4v2H10z M19 16h2v6H19z M8 18h2v2H8z"
                    + " M14 18h2v2H14z M5 20h3v2H5z M16 20h3v2H16z");

    /**
     * 上一页：像素左尖括号。
     *
     * <p>用真正的 SVG 图标替代原来的文字字符 {@code ‹}：字符在行框里天然偏上，
     * 又带字距侧边空白，很难在圆形按钮里真正做到水平垂直居中；换成图标后交给
     * {@link SvgIcon#drawCentered} 按墨迹居中，稳定对齐。厚度 2、每级错开 2 个单位。</p>
     */
    public static final SvgIcon CHEVRON_LEFT = SvgIcon.of(
            "M14 4h2v2h-2z M12 6h2v2h-2z M10 8h2v2h-2z M8 10h2v2H8z"
                    + " M8 12h2v2H8z M10 14h2v2h-2z M12 16h2v2h-2z M14 18h2v2h-2z");

    /** 下一页：像素右尖括号（{@link #CHEVRON_LEFT} 的镜像）。 */
    public static final SvgIcon CHEVRON_RIGHT = SvgIcon.of(
            "M8 4h2v2H8z M10 6h2v2h-2z M12 8h2v2h-2z M14 10h2v2h-2z"
                    + " M14 12h2v2h-2z M12 14h2v2h-2z M10 16h2v2h-2z M8 18h2v2H8z");

    private SvgIcons() {
    }
}
