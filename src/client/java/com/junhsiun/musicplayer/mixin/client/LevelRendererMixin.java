package com.junhsiun.musicplayer.mixin.client;

import com.junhsiun.musicplayer.client.JukeboxCoverRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Culler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderEntities", at = @At("TAIL"))
    private void musicplayer$renderJukeboxCovers(Camera camera, Culler culler, float partialTicks, CallbackInfo ci) {
        JukeboxCoverRenderer.render(camera);
    }
}
