package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.idle.IdleController;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 纯享模式的键盘输入探针：任意按键 / 字符输入都会立刻退出。
 *
 * <p>1.21.1 的 {@code KeyboardHandler.keyPress} 是 (long, int, int, int, int)
 * 老签名，没有 KeyEvent 事件对象。</p>
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void musicplayer$onKeyPress(long window, int key, int scanCode, int action, int modifiers,
                                        CallbackInfo callbackInfo) {
        IdleController.getInstance().onRawInput();
    }

    @Inject(method = "charTyped", at = @At("HEAD"))
    private void musicplayer$onCharTyped(long window, int codePoint, int modifiers,
                                         CallbackInfo callbackInfo) {
        IdleController.getInstance().onRawInput();
    }
}
