package com.junhsiun.musicplayer.client.gui.animation;

/**
 * 共享元素过渡的一个端点快照：封面矩形 + 标题 / 副标题 / 末尾文字的位置与样式。
 *
 * <p>列表行、歌单详情头部、单曲详情各自知道自己的布局，把「封面在哪、文字在哪、
 * 多大、什么颜色」抽成这个中立的数据结构交给 {@link SharedElementTransition}，
 * 两边就不必互相引用。动画期间同一张封面、同一段文字从 {@code from} 平滑插值到 {@code to}。</p>
 *
 * <p><b>副标题与末尾文字是可选的：</b>只有两端都提供同一个语义槽位时才会飞；某一端
 * 拿不到对应元素（例如列表行有时长、详情页的时长要等接口回来）就保持 {@code null}，
 * 由页面自己按原有逻辑淡入/显示，不会硬凑一个并不存在的对象。</p>
 *
 * @param coverX0      封面矩形左边界（GUI 像素）
 * @param coverY0      封面矩形上边界
 * @param coverX1      封面矩形右边界
 * @param coverY1      封面矩形下边界
 * @param titleCenterX 标题水平中心
 * @param titleTopY    标题文字顶边（与 {@code GlassWidgets.centeredText} 的 y 一致）
 * @param titleScale   标题缩放
 * @param titleColor   标题 ARGB 颜色
 * @param title        标题文本（具体用起点还是终点由过渡决定，保证落地不换字）
 * @param coverUrl     封面地址
 * @param itemId       条目 ID：反向返回时靠它在上一页列表里找到落点行
 * @param subtitle     副标题（歌曲的歌手 / 歌单创建者等）；任一端没有则为 {@code null}
 * @param album        专辑（歌曲列表的副标题里带专辑时，单独拆出来飞向详情页的专辑行）
 * @param meta         末尾文字（时长等）；任一端没有则为 {@code null}
 */
public record HeroElement(
        int coverX0, int coverY0, int coverX1, int coverY1,
        float titleCenterX, float titleTopY, float titleScale,
        int titleColor, String title, String coverUrl, String itemId,
        HeroText subtitle, HeroText album, HeroText meta) {

    /**
     * 一段可参与飞行的文字：内容 + 居中位置 + 缩放 + 颜色。
     *
     * <p>{@code centerX} / {@code topY} 与 {@code GlassWidgets.centeredText} 的参数语义一致，
     * 这样两端都按"居中位置"描述，插值时不会因为左边距/右对齐的差异产生偏移。</p>
     */
    public record HeroText(String text, float centerX, float topY, float scale, int color) {
    }
}