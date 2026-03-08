package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class JukeboxCoverRenderer {
    private static final Identifier BLACK_DISC_TEXTURE = Identifier.fromNamespaceAndPath(MusicPlayerMod.MOD_ID, "dynamic/jukebox_black_disc");
    private static boolean blackTextureRegistered;

    private JukeboxCoverRenderer() {
    }

    public static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || context.consumers() == null || context.matrices() == null) {
            return;
        }

        ensureBlackTexture();
        MultiBufferSource consumers = context.consumers();
        PoseStack poseStack = context.matrices();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        long now = System.currentTimeMillis();

        for (ClientJukeboxController.JukeboxVisualState state : ClientJukeboxController.getInstance().getVisualStates()) {
            if (state.coverUrl() == null || state.coverUrl().isBlank()) {
                continue;
            }

            double distanceSqr = minecraft.player.distanceToSqr(
                    state.pos().getX() + 0.5D,
                    state.pos().getY() + 0.5D,
                    state.pos().getZ() + 0.5D
            );
            if (distanceSqr > 96.0D * 96.0D) {
                continue;
            }

            Identifier coverTexture = CoverArtTextureCache.getInstance().getTextureId(state.coverUrl());
            if (coverTexture == null) {
                CoverArtTextureCache.getInstance().request(state.coverUrl());
                continue;
            }

            renderForJukebox(poseStack, consumers, camera, state.pos(), coverTexture, now, state.startedAtMillis());
        }
    }

    private static void renderForJukebox(PoseStack poseStack, MultiBufferSource consumers, Vec3 camera, BlockPos pos, Identifier coverTexture, long now, long startedAtMillis) {
        float elapsedSeconds = Math.max(0L, now - startedAtMillis) / 1000.0F;
        float spin = elapsedSeconds * 72.0F;
        float sideSpin = elapsedSeconds * 24.0F;
        float bob = (float) Math.sin(elapsedSeconds * 2.2F) * 0.02F;
        int light = LightTexture.FULL_BRIGHT;

        double baseX = pos.getX() + 0.5D - camera.x;
        double baseY = pos.getY() + 0.5D - camera.y;
        double baseZ = pos.getZ() + 0.5D - camera.z;

        renderDiscQuad(poseStack, consumers, baseX, baseY + 0.62D + bob, baseZ, 0.0F, -90.0F, spin, 0.30F, 0.22F, coverTexture, light);
        renderDiscQuad(poseStack, consumers, baseX, baseY, baseZ + 0.505D, 0.0F, 0.0F, sideSpin, 0.22F, 0.16F, coverTexture, light);
        renderDiscQuad(poseStack, consumers, baseX, baseY, baseZ - 0.505D, 180.0F, 0.0F, -sideSpin, 0.22F, 0.16F, coverTexture, light);
        renderDiscQuad(poseStack, consumers, baseX + 0.505D, baseY, baseZ, -90.0F, 0.0F, sideSpin, 0.22F, 0.16F, coverTexture, light);
        renderDiscQuad(poseStack, consumers, baseX - 0.505D, baseY, baseZ, 90.0F, 0.0F, -sideSpin, 0.22F, 0.16F, coverTexture, light);
    }

    private static void renderDiscQuad(
            PoseStack poseStack,
            MultiBufferSource consumers,
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
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        if (yRotationDegrees != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yRotationDegrees));
        }
        if (xRotationDegrees != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(xRotationDegrees));
        }
        if (spinDegrees != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(spinDegrees));
        }

        VertexConsumer black = consumers.getBuffer(RenderTypes.entityCutoutNoCull(BLACK_DISC_TEXTURE));
        VertexConsumer cover = consumers.getBuffer(RenderTypes.entityCutoutNoCull(coverTexture));
        drawQuad(black, poseStack, outerHalfSize, light, 255, 255, 255, 255, -0.001F);
        drawQuad(cover, poseStack, innerHalfSize, light, 255, 255, 255, 255, 0.0F);
        poseStack.popPose();
    }

    private static void drawQuad(VertexConsumer consumer, PoseStack poseStack, float halfSize, int light, int red, int green, int blue, int alpha, float depthOffset) {
        consumer.addVertex(poseStack.last().pose(), -halfSize, -halfSize, depthOffset)
                .setColor(red, green, blue, alpha)
                .setUv(0.0F, 1.0F)
                .setLight(light)
                .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
        consumer.addVertex(poseStack.last().pose(), halfSize, -halfSize, depthOffset)
                .setColor(red, green, blue, alpha)
                .setUv(1.0F, 1.0F)
                .setLight(light)
                .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
        consumer.addVertex(poseStack.last().pose(), halfSize, halfSize, depthOffset)
                .setColor(red, green, blue, alpha)
                .setUv(1.0F, 0.0F)
                .setLight(light)
                .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
        consumer.addVertex(poseStack.last().pose(), -halfSize, halfSize, depthOffset)
                .setColor(red, green, blue, alpha)
                .setUv(0.0F, 0.0F)
                .setLight(light)
                .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
    }

    private static void ensureBlackTexture() {
        if (blackTextureRegistered) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        NativeImage image = new NativeImage(64, 64, true);
        float center = 31.5F;
        float radius = 30.0F;
        float holeRadius = 4.0F;
        float feather = 1.5F;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                float dx = x - center;
                float dy = y - center;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance > radius + feather || distance < holeRadius) {
                    image.setPixel(x, y, 0x00000000);
                    continue;
                }
                float alphaFactor = clamp((radius - distance) / feather);
                int alpha = Math.max(180, Math.round(alphaFactor * 255.0F));
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
