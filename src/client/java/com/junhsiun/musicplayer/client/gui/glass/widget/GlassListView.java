package com.junhsiun.musicplayer.client.gui.glass.widget;

import com.junhsiun.musicplayer.client.gui.glass.theme.GlassTheme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 可滚动的玻璃列表：每行「标题 · 副标题」+ 一个玻璃操作按钮。
 *
 * <p><b>为什么行内按钮不是真正的控件：</b>行是滚动出来的，位置每帧都在变。
 * 若每行都挂一个 {@code GlassButton}，滚动时就要逐个同步坐标、还要跟着
 * {@code GlassWindowScreen} 的开场动画位移，状态会散在列表外面。这里把行和按钮
 * 都画在同一个控件里，命中区由 {@link #rowAt}/{@link #actionRowAt} 现算，
 * 滚动与动画都只影响一次坐标计算。</p>
 *
 * <p>裁切用 {@code enableScissor}：窗口开场时整个窗口在缩放，剪刀框取的是控件
 * <b>当前</b>坐标，所以动画期间也贴得住。</p>
 *
 * <p>同一时刻只可能有一行被悬停 / 按下，因此整套交互态只需要两组弹簧，
 * 不用给每行各配一组。</p>
 */
public class GlassListView extends AbstractWidget {

    /** 行高（GUI 像素）。 */
    public static final int ROW_HEIGHT = 22;
    /** 行内操作按钮尺寸。 */
    private static final int ACTION_WIDTH = 44;
    private static final int ACTION_HEIGHT = 14;
    /** 右侧滚动条宽度。 */
    private static final int SCROLLBAR_WIDTH = 3;
    /** 行内文字左内边距。 */
    private static final int TEXT_INSET = 10;
    /** 文字相对行高上沿的额外偏移。 */
    private static final float TEXT_SCALE = GlassTheme.Text.CONTROL;
    private static final float ACTION_SCALE = 0.65F;

    /**
     * 一行数据。
     *
     * @param title  主文本
     * @param detail 次文本（作者、时长等），可为 {@code null}
     * @param action 操作按钮文字
     * @param onAction 点击操作按钮时执行；{@code null} 表示这一行不可操作
     */
    public record Row(Component title, Component detail, Component action, Runnable onAction) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final GlassWidgets.Spring hover = new GlassWidgets.Spring();
    private final GlassWidgets.Spring press = new GlassWidgets.Spring();

    /** 滚动带惯性 + 平滑，和浏览界面的列表共用一套手感。 */
    private final GlassScroll scroll = new GlassScroll();
    private int pressedRow = -1;

    public GlassListView(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    public void setRows(List<Row> rows) {
        this.rows.clear();
        if (rows != null) {
            this.rows.addAll(rows);
        }
        this.pressedRow = -1;
        this.scroll.reset();
        this.scroll.setMax(maxScroll());
    }

    public void clear() {
        setRows(List.of());
    }

    public int rowCount() {
        return rows.size();
    }

    // ── 几何 ────────────────────────────────────────────────────────────

    private int contentHeight() {
        return rows.size() * ROW_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - height);
    }

    private int rowTop(int index) {
        return getY() + index * ROW_HEIGHT - scroll.offset();
    }

    private int actionX0() {
        return getX() + width - 4 - ACTION_WIDTH;
    }

    private static int actionY0(int rowTop) {
        return rowTop + (ROW_HEIGHT - ACTION_HEIGHT) / 2;
    }

    /** 鼠标所在的整行（任意位置），不在行上返回 {@code -1}。 */
    private int rowAt(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return -1;
        }
        int relative = (int) Math.floor(mouseY - getY() + scroll.position());
        if (relative < 0) {
            return -1;
        }
        int index = relative / ROW_HEIGHT;
        return index < rows.size() ? index : -1;
    }

    /** 鼠标是否落在某一行的操作按钮上。 */
    private int actionRowAt(double mouseX, double mouseY) {
        int index = rowAt(mouseX, mouseY);
        if (index < 0) {
            return -1;
        }
        int top = actionY0(rowTop(index));
        if (mouseX < actionX0() || mouseX > actionX0() + ACTION_WIDTH
                || mouseY < top || mouseY > top + ACTION_HEIGHT) {
            return -1;
        }
        return index;
    }

    // ── 交互 ────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (!active || !visible || event.button() != InputConstants.MOUSE_BUTTON_LEFT
                || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        int index = actionRowAt(event.x(), event.y());
        if (index < 0 || rows.get(index).onAction() == null) {
            // 点在行上但不点按钮：吞掉这次点击，避免穿透到下面的卡片
            return true;
        }
        pressedRow = index;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressedRow < 0) {
            return false;
        }
        int released = pressedRow;
        pressedRow = -1;
        if (actionRowAt(event.x(), event.y()) == released) {
            Row row = rows.get(released);
            if (row.onAction() != null) {
                row.onAction().run();
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroll.setMax(maxScroll());
        if (!scroll.canScroll()) {
            return false;
        }
        // 只给冲量：惯性与平滑跟随由 GlassScroll 推进（和浏览界面同一套手感）
        scroll.impulse(-scrollY * ROW_HEIGHT);
        return true;
    }

    // ── 绘制 ────────────────────────────────────────────────────────────

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (rows.isEmpty()) {
            return;
        }
        // 先推进滚动：位置在这一帧内保持不变，下面所有坐标都用同一份
        scroll.setMax(maxScroll());
        scroll.tick();
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;


        int hoverRow = rowAt(mouseX, mouseY);
        int hoverAction = actionRowAt(mouseX, mouseY);
        hover.tick(hoverRow >= 0, GlassTheme.Motion.HOVER);
        press.tickSpring(pressedRow >= 0,
                pressedRow >= 0 ? GlassTheme.Motion.PRESS : GlassTheme.Motion.RELEASE);
        float lift = hover.eased();
        float down = GlassWidgets.clamp01(press.value());

        Font font = Minecraft.getInstance().font;
        // 一道裁剪管住文字和玻璃：滚出可见区的行、行尾按钮都由它遮挡。
        // 只用 enableScissor 是不够的——玻璃是手写 BlitRenderState 提交的，看不到原版
        // 的 scissor 栈，行尾按钮会在滚动区外露出来。
        GlassWidgets.beginClip(graphics, x0, y0, x1, y1);
        for (int index = 0; index < rows.size(); index++) {
            int top = rowTop(index);
            if (top + ROW_HEIGHT <= y0 || top >= y1) {
                continue;
            }
            Row row = rows.get(index);
            boolean rowHovered = index == hoverRow;

            // 每一行都是一小块液态玻璃（ROW 材质），悬停时靠 lift 提亮。
            // 和浏览界面的列表行用同一套材质，两个列表看起来才是同一种东西。
            // 越界部分交给外层的裁剪，不再夹几何（夹了会把圆角压歪）。
            if (!GlassWidgets.glassBackground(graphics, x0, top + 1, x1 - SCROLLBAR_WIDTH - 3,
                    top + ROW_HEIGHT - 1, GlassTheme.ROW, rowHovered ? 1.0F : 0.18F, 0.0F)) {
                GlassWidgets.roundedFill(graphics, x0, top + 1, x1 - SCROLLBAR_WIDTH - 3,
                        top + ROW_HEIGHT - 1,
                        0xFFFFFF, 0xFFFFFF, 6.0F, 0.05F + 0.05F * (rowHovered ? lift : 0.0F), 1.0F);
            }

            int textY = top + Math.round((ROW_HEIGHT - 8 * TEXT_SCALE) / 2.0F);
            drawRowText(graphics, font, row, x0 + TEXT_INSET, textY);

            int bx0 = actionX0();
            int by0 = actionY0(top);
            int bx1 = bx0 + ACTION_WIDTH;
            int by1 = by0 + ACTION_HEIGHT;
            boolean pressed = index == pressedRow;
            float actionLift = index == hoverAction ? lift : 0.0F;
            float actionDown = pressed ? down : 0.0F;
            if (!GlassWidgets.glassBackground(graphics, bx0, by0, bx1, by1, GlassTheme.BUTTON,
                    actionLift, actionDown)) {
                // 退化路径的圆角必须和玻璃一致：着色器取 min(材质半径, 半高)
                GlassWidgets.roundedFill(graphics, bx0, by0, bx1, by1, 0xFFFFFF, 0xFFFFFF,
                        ACTION_HEIGHT / 2.0F, 0.25F, 1.0F);
            }
            GlassWidgets.centeredText(graphics, font, row.action(), (bx0 + bx1) / 2,
                    by0 + Math.round((ACTION_HEIGHT - 8 * ACTION_SCALE) / 2.0F),
                    GlassWidgets.dim(GlassTheme.TEXT, 0.10F * actionDown), ACTION_SCALE);
        }
        drawScrollbar(graphics, x1, y0, y1);
        GlassWidgets.endClip(graphics);
    }

    /**
     * 主文本 + 次文本。
     *
     * <p>窗口做窄之后不能再把两者拼成一个字符串统一截断：那样歌名一长，作者就被整段吃掉，
     * 连"这里有作者"都看不出来。这里分开处理——</p>
     * <ul>
     *   <li>歌名放不下：截断歌名并补省略号，次文本直接不画；</li>
     *   <li>歌名放得下：剩余宽度够放次文本才画，放不下就只留歌名。</li>
     * </ul>
     */
    private void drawRowText(GuiGraphics graphics, Font font, Row row, int x, int y) {
        String title = row.title() == null ? "" : row.title().getString();
        String detail = row.detail() == null ? "" : row.detail().getString();
        int available = Math.round((actionX0() - x - 6) / TEXT_SCALE);
        if (available <= 0) {
            return;
        }

        String line;
        int titleWidth = font.width(title);
        if (titleWidth >= available) {
            line = ellipsize(font, title, available);
        } else if (detail.isBlank()) {
            line = title;
        } else {
            String separator = "  ·  ";
            int room = available - titleWidth - font.width(separator);
            // 剩余宽度连一个字都放不下就别硬塞，只显示歌名
            line = room >= font.width("字")
                    ? title + separator + ellipsize(font, detail, room)
                    : title;
        }
        GlassWidgets.text(graphics, font, Component.literal(line), x, y, GlassTheme.TEXT, TEXT_SCALE);
    }

    /** 按像素宽度截断，超出部分换成省略号。 */
    private static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis))) + ellipsis;
    }

    private void drawScrollbar(GuiGraphics graphics, int x1, int y0, int y1) {
        if (maxScroll() <= 0) {
            return;
        }
        int trackX0 = x1 - SCROLLBAR_WIDTH;
        int trackHeight = y1 - y0;
        int thumbHeight = Math.max(12, trackHeight * trackHeight / contentHeight());
        int thumbY = y0 + (int) Math.round((trackHeight - thumbHeight) * (scroll.position() / maxScroll()));
        GlassWidgets.roundedFill(graphics, trackX0, thumbY, trackX0 + SCROLLBAR_WIDTH, thumbY + thumbHeight,
                0xFFFFFF, 0xFFFFFF, SCROLLBAR_WIDTH / 2.0F, 0.28F, 1.0F);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
