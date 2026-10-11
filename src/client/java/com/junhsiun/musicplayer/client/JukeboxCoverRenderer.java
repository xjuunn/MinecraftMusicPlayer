package com.junhsiun.musicplayer.client;

import com.junhsiun.musicplayer.MusicPlayerMod;
import com.junhsiun.musicplayer.util.Java8;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class JukeboxCoverRenderer {
    private static final ResourceLocation BLACK_DISC_TEXTURE = new ResourceLocation(MusicPlayerMod.MOD_ID, "dynamic/jukebox_black_disc");
    private static final double SIDE_OFFSET = 0.507D;
    private static final double SIDE_CENTER_Y = 0.02D;
    private static final float OUTER_HALF_SIZE = 0.34F;
    private static final float INNER_HALF_SIZE = 0.235F;
    private static boolean blackTextureRegistered;

    private JukeboxCoverRenderer() {
    }

    public static void render(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || camera == null) {
            return;
        }

        ensureBlackTexture();
        Vec3 cameraPos = camera.getPosition();
        long now = System.currentTimeMillis();

        for (ClientJukeboxController.JukeboxVisualState state : ClientJukeboxController.getInstance().getVisualStates()) {
            double dx = state.pos().getX() + 0.5D - minecraft.player.x;
            double dy = state.pos().getY() + 0.5D - minecraft.player.y;
            double dz = state.pos().getZ() + 0.5D - minecraft.player.z;
            double distanceSqr = dx * dx + dy * dy + dz * dz;
            if (distanceSqr > 96.0D * 96.0D) {
                continue;
            }

            ResourceLocation coverTexture = null;
            if (state.coverUrl() != null && Java8.isNotBlank(state.coverUrl())) {
                coverTexture = CoverArtTextureCache.getInstance().getTextureId(state.coverUrl());
                if (coverTexture == null) {
                    CoverArtTextureCache.getInstance().request(state.coverUrl());
                }
            }

            renderForJukebox(cameraPos, state.pos(), coverTexture, now, state.startedAtMillis(), state.finished(), state.finishedAtMillis());
        }
    }

    private static void renderForJukebox(Vec3 camera, BlockPos pos, ResourceLocation coverTexture, long now, long startedAtMillis, boolean finished, long finishedAtMillis) {
        float sideSpin;
        if (finished && now >= finishedAtMillis) {
            sideSpin = 0.0F;
        } else {
            sideSpin = Math.max(0L, now - startedAtMillis) / 1000.0F * 18.0F;
        }

        double baseX = pos.getX() + 0.5D - camera.x;
        double baseY = pos.getY() + 0.5D - camera.y;
        double baseZ = pos.getZ() + 0.5D - camera.z;

        renderDiscQuad(baseX, baseY + SIDE_CENTER_Y, baseZ + SIDE_OFFSET, 0.0F, sideSpin, OUTER_HALF_SIZE, INNER_HALF_SIZE, coverTexture);
        renderDiscQuad(baseX, baseY + SIDE_CENTER_Y, baseZ - SIDE_OFFSET, 180.0F, sideSpin, OUTER_HALF_SIZE, INNER_HALF_SIZE, coverTexture);
        renderDiscQuad(baseX + SIDE_OFFSET, baseY + SIDE_CENTER_Y, baseZ, 90.0F, sideSpin, OUTER_HALF_SIZE, INNER_HALF_SIZE, coverTexture);
        renderDiscQuad(baseX - SIDE_OFFSET, baseY + SIDE_CENTER_Y, baseZ, -90.0F, sideSpin, OUTER_HALF_SIZE, INNER_HALF_SIZE, coverTexture);
    }

    private static void renderDiscQuad(
            double x,
            double y,
            double z,
            float yRotationDegrees,
            float spinDegrees,
            float outerHalfSize,
            float innerHalfSize,
            ResourceLocation coverTexture
    ) {
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float) x, (float) y, (float) z);
        if (yRotationDegrees != 0.0F) {
            GlStateManager.rotatef(yRotationDegrees, 0.0F, 1.0F, 0.0F);
        }
        if (spinDegrees != 0.0F) {
            GlStateManager.rotatef(spinDegrees, 0.0F, 0.0F, 1.0F);
        }

        drawQuad(BLACK_DISC_TEXTURE, outerHalfSize, 0.0F);
        if (coverTexture != null) {
            drawQuad(coverTexture, innerHalfSize, 0.003F);
        }
        GlStateManager.popMatrix();
    }

    private static void drawQuad(ResourceLocation texture, float halfSize, float depthOffset) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getTextureManager().bind(texture);

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(7, DefaultVertexFormat.POSITION_TEX_NORMAL);
        vertex(bufferBuilder, -halfSize, -halfSize, depthOffset, 0.0F, 1.0F);
        vertex(bufferBuilder, halfSize, -halfSize, depthOffset, 1.0F, 1.0F);
        vertex(bufferBuilder, halfSize, halfSize, depthOffset, 1.0F, 0.0F);
        vertex(bufferBuilder, -halfSize, halfSize, depthOffset, 0.0F, 0.0F);
        Tesselator.getInstance().end();

        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
    }

    private static void vertex(BufferBuilder builder, float x, float y, float z, float u, float v) {
        builder.vertex(x, y, z)
                .uv(u, v)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
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
                    image.setPixelRGBA(x, y, 0x00000000);
                    continue;
                }
                float outerFade = clamp((radius - distance) / feather);
                float innerFade = clamp((distance - holeRadius) / feather);
                int alpha = Math.round(235.0F * Math.min(outerFade, innerFade));
                image.setPixelRGBA(x, y, (alpha << 24));
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
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
