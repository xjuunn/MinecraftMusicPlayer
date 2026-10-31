package com.junhsiun.musicplayer.client.gui.glass;

/**
 * 液态玻璃的投影参数，对应 DangoMusic 的 {@code ShadowStyle}。
 *
 * @param size    投影柔和半径（GUI 像素），0 表示不绘制投影
 * @param offsetX 投影偏移 x（GUI 像素，正值向右）
 * @param offsetY 投影偏移 y，采用与参考实现一致的"向上为正"约定：
 *                {@code -10} 表示投影向下偏移 10 像素（光源在左上方）
 * @param alpha   投影不透明度
 */
public record GlassShadow(float size, float offsetX, float offsetY, float alpha) {

    public static final GlassShadow NONE = new GlassShadow(0.0F, 0.0F, 0.0F, 0.0F);

    public GlassShadow {
        size = Math.max(0.0F, size);
        alpha = Math.max(0.0F, Math.min(1.0F, alpha));
    }

    /** 阴影实际在 GUI 坐标下向下的位移（着色器里的 y 向下）。 */
    public float effectiveOffsetY() {
        return -offsetY;
    }

    /**
     * 绘制阴影需要向外扩张的像素数，与参考实现一致：
     * {@code size + max(|offsetX|, |offsetY|)}。
     */
    public int padding() {
        if (size <= 0.0F) {
            return 0;
        }
        return (int) Math.ceil(size + Math.max(Math.abs(offsetX), Math.abs(offsetY)));
    }

    public boolean visible() {
        return size > 0.0F && alpha > 0.0F;
    }
}
