package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.idle.IdleController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 纯享模式的鼠标输入探针。
 *
 * <p>只在事件入口记录"刚刚有输入"，不改动任何鼠标逻辑。移动事件要判断实际位移，
 * 因为鼠标被锁定后窗口仍会持续派发 0 位移事件，无条件重置会让挂机永远无法触发。</p>
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void musicplayer$onButton(long window, MouseButtonInfo buttonInfo, int action,
                                      CallbackInfo callbackInfo) {
        IdleController.getInstance().onRawInput();
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void musicplayer$onScroll(long window, double xOffset, double yOffset,
                                      CallbackInfo callbackInfo) {
        IdleController.getInstance().onRawInput();
    }

    /** 上一次 onMove 的指针位置，用于自行计算位移（本版本的 onMove 不带 delta 参数）。 */
    private double musicplayer$lastX = Double.MIN_VALUE;
    private double musicplayer$lastY;

    @Inject(method = "onMove", at = @At("HEAD"))
    private void musicplayer$onMove(long window, double x, double y,
                                    CallbackInfo callbackInfo) {
        // 鼠标锁定后窗口仍会持续派发事件，只有真实位移才算输入。
        // 阈值取半像素：既滤掉驱动偶尔给出的亚像素噪声，又能捕捉到任何真实移动。
        if (musicplayer$lastX != Double.MIN_VALUE
                && Math.abs(x - musicplayer$lastX) + Math.abs(y - musicplayer$lastY) > 0.5D) {
            IdleController.getInstance().onRawInput();
        }
        musicplayer$lastX = x;
        musicplayer$lastY = y;
    }
}
