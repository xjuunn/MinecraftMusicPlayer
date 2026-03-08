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
    private static final int SEGMENTS = 20;
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
        float rotation = elapsedSeconds * 72.0F;
        float slowRotation = elapsedSeconds * 24.0F;
        float bob = (float) Math.sin(elapsedSeconds * 2.2F) * 0.025F;
        int light = LightTexture.FULL_BRIGHT;

        double baseX = pos.getX() + 0.5D - camera.x;
        double baseY = pos.getY() + 0.5D - camera.y;
        double baseZ = pos.getZ() + 0.5D - camera.z;

        renderDisc(poseStack, consumers, coverTexture, baseX, baseY + 0.62D + bob, baseZ, 0.0F, -90.0F, rotation, 0.26F, 0.20F, light);
        renderDisc(poseStack, consumers, coverTexture, baseX, baseY, baseZ + 0.502D, 0.0F, 0.0F, slowRotation, 0.18F, 0.14F, light);
        renderDisc(poseStack, consumers, coverTexture, baseX, baseY, baseZ - 0.502D, 180.0F, 0.0F, -slowRotation, 0.18F, 0.14F, light);
        renderDisc(poseStack, consumers, coverTexture, baseX + 0.502D, baseY, baseZ, -90.0F, 0.0F, slowRotation, 0.18F, 0.14F, light);
        renderDisc(poseStack, consumers, coverTexture, baseX - 0.502D, baseY, baseZ, 90.0F, 0.0F, -slowRotation, 0.18F, 0.14F, light);
    }

    private static void renderDisc(
            PoseStack poseStack,
            MultiBufferSource consumers,
            Identifier coverTexture,
            double x,
            double y,
            double z,
            float yRotationDegrees,
            float xRotationDegrees,
            float spinDegrees,
            float outerRadius,
            float coverRadius,
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

        drawDisc(consumers.getBuffer(RenderTypes.entityTranslucent(BLACK_DISC_TEXTURE)), poseStack, outerRadius, light, 255, 255, 255, 235);
        drawDisc(consumers.getBuffer(RenderTypes.entityTranslucent(coverTexture)), poseStack, coverRadius, light, 255, 255, 255, 255);
        poseStack.popPose();
    }

    private static void drawDisc(VertexConsumer consumer, PoseStack poseStack, float radius, int light, int red, int green, int blue, int alpha) {
        for (int index = 0; index < SEGMENTS; index++) {
            float angleStart = (float) (Math.PI * 2.0D * index / SEGMENTS);
            float angleEnd = (float) (Math.PI * 2.0D * (index + 1) / SEGMENTS);
            addSliceVertex(consumer, poseStack, 0.0F, 0.0F, 0.0F, radius, light, red, green, blue, alpha);
            addSliceVertex(consumer, poseStack, (float) Math.cos(angleStart) * radius, (float) Math.sin(angleStart) * radius, 0.0F, radius, light, red, green, blue, alpha);
            addSliceVertex(consumer, poseStack, (float) Math.cos(angleEnd) * radius, (float) Math.sin(angleEnd) * radius, 0.0F, radius, light, red, green, blue, alpha);
            addSliceVertex(consumer, poseStack, 0.0F, 0.0F, 0.0F, radius, light, red, green, blue, alpha);
        }
    }

    private static void addSliceVertex(VertexConsumer consumer, PoseStack poseStack, float x, float y, float z, float radius, int light, int red, int green, int blue, int alpha) {
        float normalizedRadius = Math.max(0.0001F, radius);
        float u = 0.5F + (x / (normalizedRadius * 2.0F));
        float v = 0.5F + (y / (normalizedRadius * 2.0F));
        consumer.addVertex(poseStack.last().pose(), x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setLight(light)
                .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
    }

    private static void ensureBlackTexture() {
        if (blackTextureRegistered) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        NativeImage image = new NativeImage(1, 1, false);
        image.setPixelABGR(0, 0, 0xFF000000);
        DynamicTexture texture = new DynamicTexture(() -> "musicplayer_black_disc", image);
        texture.upload();
        minecraft.getTextureManager().register(BLACK_DISC_TEXTURE, texture);
        blackTextureRegistered = true;
    }
}
