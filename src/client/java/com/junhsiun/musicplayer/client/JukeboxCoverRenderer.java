package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class JukeboxCoverRenderer {
    private static final Identifier BLACK_DISC_TEXTURE = Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "dynamic/jukebox_black_disc");
    private static final double SIDE_OFFSET = 0.507D;
    private static final double SIDE_CENTER_Y = 0.02D;
    private static final float OUTER_HALF_SIZE = 0.34F;
    private static final float INNER_HALF_SIZE = 0.235F;
    private static final int FULL_BRIGHTNESS = 0xF000F0;
    private static boolean blackTextureRegistered;

    private JukeboxCoverRenderer() {
    }

    public static void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || context.poseStack() == null) {
            return;
        }

        ensureBlackTexture();
        SubmitNodeCollector collector = context.submitNodeCollector();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        long now = System.currentTimeMillis();

        for (ClientJukeboxController.JukeboxVisualState state : ClientJukeboxController.getInstance().getVisualStates()) {
            double distanceSqr = minecraft.player.distanceToSqr(
                    state.pos().getX() + 0.5D,
                    state.pos().getY() + 0.5D,
                    state.pos().getZ() + 0.5D
            );
            if (distanceSqr > 96.0D * 96.0D) {
                continue;
            }

            Identifier coverTexture = null;
            if (state.coverUrl() != null && !state.coverUrl().isBlank()) {
                coverTexture = CoverArtTextureCache.getInstance().getTextureId(state.coverUrl());
                if (coverTexture == null) {
                    CoverArtTextureCache.getInstance().request(state.coverUrl());
                }
            }

            renderForJukebox(collector, camera, state.pos(), coverTexture, now, state.startedAtMillis(), state.finished(), state.finishedAtMillis());
        }
    }

    private static void renderForJukebox(SubmitNodeCollector collector, Vec3 camera, BlockPos pos, Identifier coverTexture, long now, long startedAtMillis, boolean finished, long finishedAtMillis) {
        float sideSpin;
        if (finished && now >= finishedAtMillis) {
            sideSpin = 0.0F;
        } else {
            sideSpin = Math.max(0L, now - startedAtMillis) / 1000.0F * 18.0F;
        }
        int light = FULL_BRIGHTNESS;

        double baseX = pos.getX() + 0.5D - camera.x;
        double baseY = pos.getY() + 0.5D - camera.y;
        double baseZ = pos.getZ() + 0.5D - camera.z;

        renderDiscQuad(collector, baseX, baseY + SIDE_CENTER_Y, baseZ + SIDE_OFFSET, 0.0F, 0.0F, sideSpin, OUTER_HALF_SIZE, INNER_HALF_SIZE, coverTexture, light);
        renderDiscQuad(collector, baseX, baseY + SIDE_CENTER_Y, baseZ - SIDE_OFFSET, 180.0F, 0.0F, sideSpin, OUTER_HALF_SIZE, INNER_HALF_SIZE, coverTexture, light);
        renderDiscQuad(collector, baseX + SIDE_OFFSET, baseY + SIDE_CENTER_Y, baseZ, 90.0F, 0.0F, sideSpin, OUTER_HALF_SIZE, INNER_HALF_SIZE, coverTexture, light);
        renderDiscQuad(collector, baseX - SIDE_OFFSET, baseY + SIDE_CENTER_Y, baseZ, -90.0F, 0.0F, sideSpin, OUTER_HALF_SIZE, INNER_HALF_SIZE, coverTexture, light);
    }

    private static void renderDiscQuad(
            SubmitNodeCollector collector,
            double x,
            double y,
            double z,
            float yRotationDegrees,
            float xRotationDegrees,
            float spinDegrees,
            float outerHalfSize,
            float innerHalfSize,
            Identifier coverTexture,
            int light
    ) {
        PoseStack ps = new PoseStack();
        ps.translate(x, y, z);
        if (yRotationDegrees != 0.0F) {
            ps.mulPose(Axis.YP.rotationDegrees(yRotationDegrees));
        }
        if (xRotationDegrees != 0.0F) {
            ps.mulPose(Axis.XP.rotationDegrees(xRotationDegrees));
        }
        if (spinDegrees != 0.0F) {
            ps.mulPose(Axis.ZP.rotationDegrees(spinDegrees));
        }

        collector.submitCustomGeometry(ps, RenderTypes.entityCutout(BLACK_DISC_TEXTURE), (pose, consumer) ->
                writeQuadVertices(consumer, pose, outerHalfSize, light, 255, 255, 255, 255, 0.0F)
        );
        if (coverTexture != null) {
            collector.submitCustomGeometry(ps, RenderTypes.entityCutout(coverTexture), (pose, consumer) ->
                    writeQuadVertices(consumer, pose, innerHalfSize, light, 255, 255, 255, 255, 0.003F)
            );
        }
    }

    private static void writeQuadVertices(VertexConsumer consumer, PoseStack.Pose pose, float halfSize, int light, int red, int green, int blue, int alpha, float depthOffset) {
        consumer.addVertex(pose.pose(), -halfSize, -halfSize, depthOffset)
                .setColor(red, green, blue, alpha)
                .setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose.pose(), halfSize, -halfSize, depthOffset)
                .setColor(red, green, blue, alpha)
                .setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose.pose(), halfSize, halfSize, depthOffset)
                .setColor(red, green, blue, alpha)
                .setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose.pose(), -halfSize, halfSize, depthOffset)
                .setColor(red, green, blue, alpha)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private static void ensureBlackTexture() {
        if (blackTextureRegistered) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        NativeImage image = new NativeImage(256, 256, true);
        float center = 127.5F;
        float radius = 122.0F;
        float holeRadius = 18.0F;
        float feather = 5.0F;
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                float dx = x - center;
                float dy = y - center;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance > radius + feather || distance < holeRadius) {
                    image.setPixel(x, y, 0x00000000);
                    continue;
                }
                float outerFade = clamp((radius - distance) / feather);
                float innerFade = clamp((distance - holeRadius) / feather);
                int alpha = Math.round(235.0F * Math.min(outerFade, innerFade));
                image.setPixel(x, y, (alpha << 24));
            }
        }
        DynamicTexture texture = new DynamicTexture(() -> "musicplayer_black_disc", image);
        texture.upload();
        minecraft.getTextureManager().register(BLACK_DISC_TEXTURE, texture);
        blackTextureRegistered = true;
    }

    private static float clamp(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, value);
    }
}
