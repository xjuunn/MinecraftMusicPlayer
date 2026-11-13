package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.idle.IdleController;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 纯享模式的键盘输入探针：任意按键 / 字符输入都会立刻退出。
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void musicplayer$onKeyPress(long window, int key, KeyEvent event,
                                        CallbackInfo callbackInfo) {
        IdleController.getInstance().onRawInput();
    }

    @Inject(method = "charTyped", at = @At("HEAD"))
    private void musicplayer$onCharTyped(long window, CharacterEvent event,
                                         CallbackInfo callbackInfo) {
        IdleController.getInstance().onRawInput();
    }
}
