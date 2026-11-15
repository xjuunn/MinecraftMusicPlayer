package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.client.gui.browser.MusicBrowserScreen;
import com.junhsiun.musicplayer.client.gui.window.GlassWindowScreen;
import com.junhsiun.musicplayer.client.idle.IdleController;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * 临时自动化验证脚手架，仅在环境变量 {@code MUSICPLAYER_VERIFY=1} 时启用。
 *
 * <p>验证完成后会随提交一起删除，不属于正式功能。</p>
 */
public final class VerifyHarness {

    private static final boolean ENABLED = "1".equals(System.getenv("MUSICPLAYER_VERIFY"));

    private static final long WORLD_SETTLE = 1500L;
    private static final long WINDOW_SETTLE = 1600L;

    private static long start;
    private static long previewStart;
    private static boolean installed;
    private static boolean opened;
    private static boolean previewed;
    private static int shotIndex;
    private static boolean lastActive;

    /** 预览时间轴上的采样点（毫秒）：用于确认特效只出现一次并持续存在。 */
    private static final long[] PREVIEW_SHOTS = {300L, 600L, 900L, 1100L, 1300L, 1600L, 2000L,
            2500L, 5000L, 8000L};

    private VerifyHarness() {
    }

    public static void install() {
        if (!ENABLED || installed) {
            return;
        }
        installed = true;
        ClientTickEvents.END_CLIENT_TICK.register(VerifyHarness::tick);
        MusicPlayerMod.LOGGER.info("[verify] harness installed");
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        long now = Util.getMillis();
        if (start == 0L) {
            start = now;
            MusicPlayerMod.LOGGER.info("[verify] world ready");
            return;
        }
        long elapsed = now - start;

        if (!opened && elapsed >= WORLD_SETTLE) {
            shot("v1_world");
            GlassWindowScreen.open(new MusicBrowserScreen());
            opened = true;
            MusicPlayerMod.LOGGER.info("[verify] opened browser screen");
            return;
        }
        if (opened && !previewed) {
            if (elapsed < WORLD_SETTLE + WINDOW_SETTLE) {
                return;
            }
            shot("v2_window");
            if (client.screen instanceof GlassWindowScreen glass) {
                IdleController.getInstance().requestPreview();
                glass.onClose();
                previewed = true;
                previewStart = now;
                MusicPlayerMod.LOGGER.info("[verify] preview requested");
            } else {
                MusicPlayerMod.LOGGER.warn("[verify] not a glass window, aborting");
                client.stop();
            }
            return;
        }
        if (!previewed) {
            return;
        }
        long previewElapsed = now - previewStart;

        IdleController controller = IdleController.getInstance();
        boolean active = controller.isActive();
        String screen = client.screen == null ? "null" : client.screen.getClass().getSimpleName();
        MusicPlayerMod.LOGGER.info("[verify] t={} fade={} active={} hideHud={} screen={} grabbed={} keys={}",
                previewElapsed, String.format("%.3f", controller.fade(now)), active,
                controller.shouldHideHud(now), screen, client.mouseHandler.isMouseGrabbed(),
                heldKeys(client));

        // 一旦 active 从 true 变 false，立刻打一帧「当时」的现场，供事后比对
        if (lastActive && !active) {
            shot("v3_drop_" + previewElapsed);
            MusicPlayerMod.LOGGER.info("[verify] active dropped at t={} screen={}", previewElapsed, screen);
        }
        lastActive = active;

        while (shotIndex < PREVIEW_SHOTS.length && previewElapsed >= PREVIEW_SHOTS[shotIndex]) {
            shot("v3_preview_" + PREVIEW_SHOTS[shotIndex]);
            shotIndex++;
        }
        if (previewElapsed > PREVIEW_SHOTS[PREVIEW_SHOTS.length - 1] + 1500L) {
            MusicPlayerMod.LOGGER.info("[verify] done, stopping");
            client.stop();
        }
    }

    private static String heldKeys(Minecraft client) {
        StringBuilder builder = new StringBuilder();
        for (net.minecraft.client.KeyMapping mapping : client.options.keyMappings) {
            if (mapping != null && mapping.isDown()) {
                builder.append(mapping.getName()).append(' ');
            }
        }
        return builder.toString().trim();
    }

    private static void shot(String name) {
        Minecraft client = Minecraft.getInstance();
        RenderTarget main = client.getMainRenderTarget();
        if (main == null) {
            return;
        }
        try (NativeImage image = Screenshot.takeScreenshot(main)) {
            image.writeToFile(new java.io.File(name + ".png"));
            MusicPlayerMod.LOGGER.info("[verify] wrote {}.png {}x{}", name, image.getWidth(),
                    image.getHeight());
        } catch (Throwable throwable) {
            MusicPlayerMod.LOGGER.warn("[verify] screenshot failed", throwable);
        }
    }
}
