package com.junhsiun.musicplayer.client.gui.glass.widget;

import net.minecraft.util.Util;

/**
 * 带惯性的平滑滚动。
 *
 * <p><b>为什么不是"滚轮直接把位置加减一个量"：</b>那是瞬时跳变，滚一格画面就跳一格，
 * 帧率再高也是阶梯状的，看起来就是"不丝滑"。这里把滚动拆成两层：</p>
 *
 * <ul>
 *   <li><b>目标位置</b>吃滚轮的冲量，按 {@code e^(-t/τ)} 衰减地往前滑——这一层是<b>惯性</b>，
 *       松手后画面还会再滑一小段然后停住；</li>
 *   <li><b>画面位置</b>再指数逼近目标位置——这一层是<b>平滑</b>，快速连滚时画面始终"追"着
 *       目标走，不会一格一格地跳。</li>
 * </ul>
 *
 * <p>两层都用 {@code 1 - e^(-dt/τ)} 而不是固定比例系数：这样 30 / 60 / 144 帧率下滑动
 * <b>距离与手感完全一致</b>，不会出现"高帧率滚得飞快、掉帧时一顿一顿"。{@code dt} 还夹了
 * 上限，卡顿一下回来也不会瞬移。</p>
 *
 * <p>用法：每帧调一次 {@link #tick()}，滚轮调 {@link #impulse(double)}，画的时候用
 * {@link #offset()}。</p>
 */
public final class GlassScroll {

    /** 冲量时间常数：一格滚轮的能量在约这么多秒内释放完。 */
    private static final double IMPULSE_SECONDS = 0.10D;
    /** 惯性衰减时间常数：越大滑得越远。 */
    private static final double DAMPING_SECONDS = 0.17D;
    /** 平滑跟随时间常数：画面追目标的"轻微滞后"。 */
    private static final double FOLLOW_SECONDS = 0.055D;
    /** 速度上限（像素/秒），防止快速连滚时一帧飞出好几屏。 */
    private static final double MAX_SPEED = 2400.0D;
    /** 单帧最大步长（秒）。 */
    private static final double MAX_STEP = 0.05D;
    /** 目标位置立即吃掉的滚轮位移比例：按了马上有反应，剩下的交给惯性。 */
    private static final double IMMEDIATE_SHARE = 0.35D;

    /** 画面位置（行按它排版）。 */
    private double position;
    /** 目标位置。 */
    private double target;
    /** 惯性速度（像素/秒）。 */
    private double velocity;
    /** 可滚动的最大位移。 */
    private double max;
    private long lastMillis = Util.getMillis();

    /** 当前画面位置。 */
    public double position() {
        return position;
    }

    /** 画面位置的整数偏移，用于排版。 */
    public int offset() {
        return (int) Math.round(position);
    }

    /** 能不能滚动（内容超过视口）。 */
    public boolean canScroll() {
        return max > 0.0D;
    }

    public double max() {
        return max;
    }

    /** 内容/视口变化后更新可滚动范围，并就地夹紧（撞到边界时把速度吃掉）。 */
    public void setMax(double value) {
        max = Math.max(0.0D, value);
        if (position > max) {
            position = max;
        }
        if (target > max) {
            target = max;
        }
        if (position < 0.0D) {
            position = 0.0D;
        }
        if (target < 0.0D) {
            target = 0.0D;
        }
        if (velocity != 0.0D && (target <= 0.0D || target >= max)) {
            velocity = 0.0D;
        }
    }

    /** 回到顶部并清掉速度（换了一批数据时用）。 */
    public void reset() {
        position = 0.0D;
        target = 0.0D;
        velocity = 0.0D;
    }

    /**
     * 平滑地移动到指定位置（Home / End 这类跳转用）。
     *
     * <p>和 {@link #snap()} 的区别：这里只改目标，画面位置仍然走跟随层，所以是"滑"过去
     * 而不是瞬间跳过去——按 End 直接瞬移会让人瞬间失去自己在列表里的位置感。</p>
     */
    public void jumpTo(double value) {
        velocity = 0.0D;
        target = clamp(value);
    }

    /** 立刻跳到目标位置，不做平滑（例如列表内容被整体替换）。 */
    public void snap() {
        position = target;
        velocity = 0.0D;
    }

    /**
     * 滚轮：给一次冲量。
     *
     * @param delta 位移（正数表示内容向上走，即向下滚动）
     */
    public void impulse(double delta) {
        if (max <= 0.0D || delta == 0.0D) {
            return;
        }
        double next = velocity + delta / IMPULSE_SECONDS;
        velocity = Math.max(-MAX_SPEED, Math.min(next, MAX_SPEED));
        target = clamp(target + delta * IMMEDIATE_SHARE);
    }

    /** 每帧推进一次物理。 */
    public void tick() {
        long now = Util.getMillis();
        double dt = Math.max(0.0D, Math.min((now - lastMillis) / 1000.0D, MAX_STEP));
        lastMillis = now;
        if (max <= 0.0D) {
            position = 0.0D;
            target = 0.0D;
            velocity = 0.0D;
            return;
        }
        if (velocity == 0.0D && Math.abs(target - position) <= 0.01D) {
            return;
        }
        if (dt <= 0.0D) {
            return;
        }
        // 惯性滑行
        velocity *= Math.exp(-dt / DAMPING_SECONDS);
        if (Math.abs(velocity) < 2.0D) {
            velocity = 0.0D;
        }
        double desired = target + velocity * dt;
        double clamped = clamp(desired);
        if (clamped != desired) {
            // 撞到上下边界：速度立刻归零，否则会一直"顶"着边界抖
            velocity = 0.0D;
        }
        // 平滑跟随
        double follow = 1.0D - Math.exp(-dt / FOLLOW_SECONDS);
        double rendered = position + (clamped - position) * follow;
        if (Math.abs(clamped - rendered) < 0.05D) {
            rendered = clamped;
        }
        target = clamped;
        position = clamp(rendered);
    }

    private double clamp(double value) {
        return Math.max(0.0D, Math.min(value, max));
    }
}
