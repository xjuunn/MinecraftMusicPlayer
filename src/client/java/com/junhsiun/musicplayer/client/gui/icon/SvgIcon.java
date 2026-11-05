package com.junhsiun.musicplayer.client.gui.icon;

import com.junhsiun.musicplayer.MusicPlayerMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * 可配置的 SVG 图标：把 {@code <path d="...">} 解析成一组实心矩形，绘制时按目标尺寸缩放填充。
 *
 * <p><b>为什么是矩形：</b>原版界面只有 {@link GuiGraphicsExtractor#fill} 能画形状，它只接受轴对齐
 * 矩形，没有描边、没有曲线填充。本项目的图标都是像素画——每个色块本身就是一个小矩形，
 * 所以按矩形还原是无损的，而且小尺寸下边缘是硬的，正是像素画该有的样子。</p>
 *
 * <p><b>支持的命令：</b>{@code M/m}、{@code L/l}、{@code H/h}、{@code V/v}、{@code Z/z}（含隐式重复）。
 * 曲线（{@code C/S/Q/T}）、弧线（{@code A}）不支持；子路径不是轴对齐矩形时跳过并打一条警告，
 * 不会画出半个图形。</p>
 *
 * <p><b>用法：</b>图标本身是常量（见 {@link SvgIcons}），颜色在绘制时传入，相当于 CSS 里的
 * {@code currentColor}；尺寸可以每处不同，缩放交给位姿矩阵，半像素尺寸也不会被取整搞歪。</p>
 */
public final class SvgIcon {

    /** 每个子路径归一成的矩形，按 {@code x0, y0, x1, y1} 连续存放，单位是 viewBox。 */
    private final int[] rects;
    /** viewBox 边长（{@code viewBox="0 0 N N"} 里的 N）。 */
    private final float viewBox;
    /** 图形墨迹的范围（viewBox 单位）：SVG 自带的留白不算数，居中要按墨迹算。 */
    private final float inkMinX;
    private final float inkMinY;
    private final float inkMaxX;
    private final float inkMaxY;

    private SvgIcon(float viewBox, int[] rects) {
        this.viewBox = viewBox;
        this.rects = rects;
        float minX = 0.0F;
        float minY = 0.0F;
        float maxX = viewBox;
        float maxY = viewBox;
        if (rects.length > 0) {
            minX = Float.MAX_VALUE;
            minY = Float.MAX_VALUE;
            maxX = -Float.MAX_VALUE;
            maxY = -Float.MAX_VALUE;
            for (int index = 0; index < rects.length; index += 4) {
                minX = Math.min(minX, rects[index]);
                minY = Math.min(minY, rects[index + 1]);
                maxX = Math.max(maxX, rects[index + 2]);
                maxY = Math.max(maxY, rects[index + 3]);
            }
        }
        this.inkMinX = minX;
        this.inkMinY = minY;
        this.inkMaxX = maxX;
        this.inkMaxY = maxY;
    }

    /** 按 24×24 视口解析（最常见的 SVG 视口）。 */
    public static SvgIcon of(String pathData) {
        return of(24.0F, pathData);
    }

    /** 按指定视口边长解析 {@code pathData}。 */
    public static SvgIcon of(float viewBox, String pathData) {
        if (viewBox <= 0.0F) {
            throw new IllegalArgumentException("viewBox 必须为正数：" + viewBox);
        }
        return new SvgIcon(viewBox, parse(viewBox, pathData == null ? "" : pathData));
    }

    /**
     * 画到 {@code (x, y)}，占 {@code size × size} 个 GUI 像素。
     *
     * <p>颜色相当于 SVG 的 {@code currentColor}；alpha 为 0 时直接跳过，连位姿都不压。</p>
     */
    public void draw(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        if (((color >>> 24) & 0xFF) == 0 || size <= 0 || rects.length == 0) {
            return;
        }
        float scale = size / viewBox;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        for (int index = 0; index < rects.length; index += 4) {
            graphics.fill(rects[index], rects[index + 1], rects[index + 2], rects[index + 3], color);
        }
        graphics.pose().popMatrix();
    }

    /**
     * 在 {@code boxSize × boxSize} 的方框里，按<b>墨迹</b>居中绘制边长 {@code iconSize} 的图标。
     *
     * <p>按墨迹而不是按 viewBox 居中：SVG 的留白每个图标都不一样（列表图标左右各留 7 个单位、
     * 上下只留 3 个），按 viewBox 居中会让图形在按钮里看着偏。原点取整到像素，
     * 这样缩放后的边缘不会落在两个像素中间，避免"整体偏半个像素"的观感。</p>
     */
    public void drawCentered(GuiGraphicsExtractor graphics, int x, int y, int boxSize, int iconSize, int color) {
        if (rects.length == 0 || iconSize <= 0) {
            return;
        }
        float scale = iconSize / viewBox;
        float inkCenterX = (inkMinX + inkMaxX) * 0.5F * scale;
        float inkCenterY = (inkMinY + inkMaxY) * 0.5F * scale;
        int left = Math.round(x + boxSize * 0.5F - inkCenterX);
        int top = Math.round(y + boxSize * 0.5F - inkCenterY);
        draw(graphics, left, top, iconSize, color);
    }

    // ── 解析 ────────────────────────────────────────────────────────────

    private static int[] parse(float viewBox, String pathData) {
        List<Integer> rects = new ArrayList<>();
        List<float[]> points = new ArrayList<>();
        List<String> tokens = tokenize(pathData);

        float x = 0.0F;
        float y = 0.0F;
        float startX = 0.0F;
        float startY = 0.0F;
        char command = 0;
        int index = 0;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (token.length() == 1 && Character.isLetter(token.charAt(0))) {
                command = token.charAt(0);
                index++;
                if (command == 'Z' || command == 'z') {
                    // 闭合：当前点回到子路径起点，闭合边由矩形判定隐含处理
                    addRect(rects, points, pathData);
                    points.clear();
                    x = startX;
                    y = startY;
                }
                continue;
            }
            switch (command) {
                case 'M', 'L' -> {
                    if (index + 1 >= tokens.size()) {
                        index = tokens.size();
                        break;
                    }
                    float nextX = number(tokens.get(index));
                    float nextY = number(tokens.get(index + 1));
                    index += 2;
                    if (command == 'M') {
                        addRect(rects, points, pathData);
                        points.clear();
                        startX = nextX;
                        startY = nextY;
                        // 后续坐标对按 lineto 处理（SVG 规范里的隐式重复）
                        command = 'L';
                    }
                    x = nextX;
                    y = nextY;
                    points.add(new float[]{x, y});
                }
                case 'm', 'l' -> {
                    if (index + 1 >= tokens.size()) {
                        index = tokens.size();
                        break;
                    }
                    float deltaX = number(tokens.get(index));
                    float deltaY = number(tokens.get(index + 1));
                    index += 2;
                    if (command == 'm') {
                        addRect(rects, points, pathData);
                        points.clear();
                        startX = x + deltaX;
                        startY = y + deltaY;
                        x = startX;
                        y = startY;
                        command = 'l';
                    } else {
                        x += deltaX;
                        y += deltaY;
                    }
                    points.add(new float[]{x, y});
                }
                case 'H', 'h', 'V', 'v' -> {
                    float value = number(token);
                    index++;
                    switch (command) {
                        case 'H' -> x = value;
                        case 'h' -> x += value;
                        case 'V' -> y = value;
                        default -> y += value;
                    }
                    points.add(new float[]{x, y});
                }
                default -> {
                    // 不支持的命令（曲线、弧线等）：吞掉这个数继续，不让整条路径作废
                    index++;
                }
            }
        }
        addRect(rects, points, pathData);

        int[] result = new int[rects.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = rects.get(i);
        }
        return result;
    }

    /** 把一条子路径归一成矩形；不是轴对齐矩形就跳过（并提示一次，方便发现写错的路径）。 */
    private static void addRect(List<Integer> rects, List<float[]> points, String source) {
        if (points.isEmpty()) {
            return;
        }
        if (points.size() != 4) {
            warn(source);
            return;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (float[] point : points) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }
        boolean topLeft = false;
        boolean topRight = false;
        boolean bottomLeft = false;
        boolean bottomRight = false;
        for (float[] point : points) {
            boolean onLeft = point[0] == minX;
            boolean onRight = point[0] == maxX;
            boolean onTop = point[1] == minY;
            boolean onBottom = point[1] == maxY;
            if (!(onLeft || onRight) || !(onTop || onBottom)) {
                // 落在边上但不是角：L 形或斜边，画成矩形会变形
                warn(source);
                return;
            }
            topLeft |= onLeft && onTop;
            topRight |= onRight && onTop;
            bottomLeft |= onLeft && onBottom;
            bottomRight |= onRight && onBottom;
        }
        if (!(topLeft && topRight && bottomLeft && bottomRight)) {
            warn(source);
            return;
        }
        rects.add(Math.round(minX));
        rects.add(Math.round(minY));
        rects.add(Math.round(maxX));
        rects.add(Math.round(maxY));
    }

    private static void warn(String source) {
        MusicPlayerMod.LOGGER.warn("SVG 图标解析失败：子路径不是轴对齐矩形，已跳过：{}", source);
    }

    private static float number(String token) {
        try {
            return Float.parseFloat(token);
        } catch (NumberFormatException exception) {
            return 0.0F;
        }
    }

    /** 切成「命令字母」和「数字」两类记号，分隔符（空白、逗号）丢掉。 */
    private static List<String> tokenize(String data) {
        List<String> tokens = new ArrayList<>();
        int index = 0;
        int length = data.length();
        while (index < length) {
            char ch = data.charAt(index);
            if (Character.isLetter(ch)) {
                tokens.add(String.valueOf(ch));
                index++;
                continue;
            }
            if (ch == ',' || Character.isWhitespace(ch)) {
                index++;
                continue;
            }
            int begin = index;
            if (ch == '-' || ch == '+') {
                index++;
            }
            while (index < length) {
                char current = data.charAt(index);
                if (Character.isDigit(current) || current == '.') {
                    index++;
                } else {
                    break;
                }
            }
            if (index == begin) {
                // 认不出的字符：跳过，避免死循环
                index++;
                continue;
            }
            tokens.add(data.substring(begin, index));
        }
        return tokens;
    }
}
