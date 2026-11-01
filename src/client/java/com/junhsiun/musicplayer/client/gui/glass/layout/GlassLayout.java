package com.junhsiun.musicplayer.client.gui.glass.layout;

import com.junhsiun.musicplayer.client.gui.window.WindowBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 简易布局容器：按顺序摆放控件。
 *
 * <p>用法是「先声明、后放置」：往容器里 {@code add} 控件并给出高度（或宽度），
 * 布局完成后统一回调每个控件的定位函数。容器本身不持有控件引用以外的状态。</p>
 */
public final class GlassLayout {

    private GlassLayout() {
    }

    /** 纵向堆栈。 */
    public static final class Column {
        private final List<Item> items = new ArrayList<>();
        private int gap;
        private int align = Align.START;
        private int padding;

        public Column gap(int value) {
            this.gap = value;
            return this;
        }

        /** 横向对齐：START / CENTER / END。 */
        public Column align(int value) {
            this.align = value;
            return this;
        }

        public Column padding(int value) {
            this.padding = value;
            return this;
        }

        /** 添加一行，{@code height} 为该行高度。 */
        public Column add(int width, int height, Consumer<Slot> placer) {
            items.add(new Item(width, height, placer));
            return this;
        }

        /** 整行铺满内容区宽度。 */
        public Column fill(int height, Consumer<Slot> placer) {
            items.add(new Item(-1, height, placer));
            return this;
        }

        public void layout(WindowBounds area) {
            int x = area.x0() + padding;
            int y = area.y0() + padding;
            int available = area.width() - padding * 2;
            for (Item item : items) {
                int w = item.width < 0 ? available : item.width;
                int itemX = switch (align) {
                    case Align.CENTER -> x + (available - w) / 2;
                    case Align.END -> x + available - w;
                    default -> x;
                };
                item.placer.accept(new Slot(itemX, y, w, item.height));
                y += item.height + gap;
            }
        }

        /** 内容总高度，便于判断是否需要滚动或居中。 */
        public int height() {
            if (items.isEmpty()) {
                return 0;
            }
            int total = padding * 2 + gap * (items.size() - 1);
            for (Item item : items) {
                total += item.height;
            }
            return total;
        }
    }

    /** 横向行，常用于「标签 + 值」或并排按钮。 */
    public static final class Row {
        private final List<Item> items = new ArrayList<>();
        private int gap;
        private int padding;

        public Row gap(int value) {
            this.gap = value;
            return this;
        }

        public Row padding(int value) {
            this.padding = value;
            return this;
        }

        public Row add(int width, int height, Consumer<Slot> placer) {
            items.add(new Item(width, height, placer));
            return this;
        }

        /** 占满剩余宽度。 */
        public Row grow(int height, Consumer<Slot> placer) {
            items.add(new Item(-1, height, placer));
            return this;
        }

        public void layout(WindowBounds area) {
            int x = area.x0() + padding;
            int y = area.y0() + padding;
            int fixed = 0;
            int growCount = 0;
            for (Item item : items) {
                if (item.width < 0) {
                    growCount++;
                } else {
                    fixed += item.width;
                }
            }
            int gaps = gap * Math.max(0, items.size() - 1);
            int free = Math.max(0, area.width() - padding * 2 - fixed - gaps);
            int growWidth = growCount == 0 ? 0 : free / growCount;

            for (Item item : items) {
                int w = item.width < 0 ? growWidth : item.width;
                item.placer.accept(new Slot(x, y, w, item.height));
                x += w + gap;
            }
        }
    }

    /** 一个矩形槽位。 */
    public record Slot(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public int centerX() {
            return x + width / 2;
        }
    }

    /** 对齐常量。 */
    public static final class Align {
        public static final int START = 0;
        public static final int CENTER = 1;
        public static final int END = 2;

        private Align() {
        }
    }

    private record Item(int width, int height, Consumer<Slot> placer) {
    }
}
