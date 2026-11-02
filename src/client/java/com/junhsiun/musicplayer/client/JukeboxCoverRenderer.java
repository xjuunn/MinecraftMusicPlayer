package com.junhsiun.musicplayer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 把封面贴进唱片机四个侧面凹进去的槽里，并处理装载/卸载的滑入滑出动画。
 *
 * <p>方块模型（{@code assets/minecraft/models/block/jukebox.json}）在四个侧面各挖了一个
 * 1 像素深的槽，槽口对应贴图 {@code (4,5)}–{@code (13,14)} 这块 10×10 像素的区域。
 * 所以这里不画黑胶盘面、不做旋转、也不做圆形裁切，只是把封面贴在槽底，靠模型的槽壁
 * 和面明暗产生立体感。</p>
 *
 * <p><b>UV 方向：</b>照原版方块模型的 {@code FaceInfo} 顶点表，贴图 u 沿屏幕<b>右方</b>增大
 * （北面沿 -X、南面沿 +X、西面沿 +Z、东面沿 -Z），v 沿 -Y 增大。四个面上这块区域的屏幕
 * 位置完全一致（距左侧 3/16 ~ 13/16），只是朝向不同：</p>
 *
 * <pre>
 * 北面 (-Z)：屏幕左 = x 较大   南面 (+Z)：屏幕左 = x 较小
 * 西面 (-X)：屏幕左 = z 较小   东面 (+X)：屏幕左 = z 较大
 * </pre>
 *
 * <p><b>动画：</b>槽口只有 1 像素深，靠遮挡挡不住滑到槽口外面的部分（槽口上方就是方块表面
 * 和方块外面），所以滑动的封面对槽口矩形做<b>几何裁剪</b>：每帧算出封面与槽口的交集，
 * 只画交集那一块，同时按比例把 UV 一起截断。这样封面看起来就是从槽口上沿滑进来、滑出去，
 * 像被塞进唱片机内部一样。</p>
 */
public final class JukeboxCoverRenderer {
    /**
     * 封面在侧面贴图上的区域（单位：贴图边缘坐标，贴图 16×16）。
     *
     * <p>像素 {@code (4,5)}–{@code (13,14)} 覆盖的边缘范围是 u 3/16~13/16、v 4/16~14/16，
     * 也就是 10×10 像素的正方形。</p>
     */
    private static final float REGION_U0 = 3.0F / 16.0F;
    private static final float REGION_U1 = 13.0F / 16.0F;
    private static final float REGION_V0 = 4.0F / 16.0F;
    private static final float REGION_V1 = 14.0F / 16.0F;
    /** 槽深：方块模型在四个侧面各挖进去 1 像素（1/16 格）。 */
    private static final float RECESS_DEPTH = 1.0F / 16.0F;
    /** 封面比槽底再往外推一点，避免和槽底表面 z-fighting。 */
    private static final float FACE_OFFSET = 0.002F;
    /** 整体压暗一点（0-255），让封面比边框暗，看着是凹进去的。 */
    private static final int COVER_SHADE = 236;
    /**
     * 最低方块光照等级（0-15）。
     *
     * <p>环境光全黑时封面不能跟着变黑，否则就是"整块黑色"。15 等于永远满亮，
     * 数值越小越容易融进黑暗环境。</p>
     */
    private static final int MIN_BLOCK_LIGHT = 12;
    /** 超过这个距离就不画（区块方块坐标的平方）。 */
    private static final double RENDER_DISTANCE_SQR = 96.0D * 96.0D;

    /** 滑入时长（秒）：从槽口上方落到槽底。 */
    private static final double SLIDE_IN_SECONDS = 0.32D;
    /** 滑出时长（秒）：从槽底退到槽口上方。 */
    private static final double SLIDE_OUT_SECONDS = 0.20D;
    /**
     * 状态"短暂消失"的宽限时间（秒）。
     *
     * <p>服务端 stop/play 抖动、区块重载、玩家在可听范围边界来回走，都会让状态消失一两帧。
     * 连续消失超过这个时间才当作唱片真的被取走，否则封面一直挂着不动。</p>
     */
    private static final double MISSING_GRACE_SECONDS = 0.12D;
    /** 等新封面下载的最长时间（秒），超时就直接换，交给 {@code HIDDEN} 继续等。 */
    private static final double SWITCH_WAIT_SECONDS = 1.20D;
    private static final double NANOS_PER_SECOND = 1.0e9D;

    /** 每个唱片机的封面动画状态，按方块坐标索引。 */
    private static final Map<BlockPos, CoverAnimation> ANIMATIONS = new HashMap<>();
    /** 当前记录动画所属的世界，换维度/换世界时清空。 */
    private static ClientLevel animationLevel;

    private JukeboxCoverRenderer() {
    }

    public static void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || context.poseStack() == null) {
            return;
        }
        if (level != animationLevel) {
            ANIMATIONS.clear();
            animationLevel = level;
        }

        double now = System.nanoTime() / NANOS_PER_SECOND;

        // 本帧期望显示的封面（没有封面或没在播的方块不在表里）
        Map<BlockPos, String> targets = new HashMap<>();
        for (ClientJukeboxController.JukeboxVisualState state : ClientJukeboxController.getInstance().getVisualStates()) {
            String url = state.coverUrl();
            targets.put(state.pos(), url == null || url.isBlank() ? null : url);
        }

        Set<BlockPos> positions = new HashSet<>(targets.keySet());
        positions.addAll(ANIMATIONS.keySet());

        SubmitNodeCollector collector = context.submitNodeCollector();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();

        for (BlockPos pos : positions) {
            String target = targets.get(pos);
            CoverAnimation animation = ANIMATIONS.get(pos);

            if (animation == null) {
                if (target == null) {
                    continue;
                }
                Identifier texture = CoverArtTextureCache.getInstance().getTextureId(target);
                if (texture == null) {
                    // 封面还没下载好：先请求，等就绪了再开始滑入，避免滑进一个空框
                    CoverArtTextureCache.getInstance().request(target);
                    continue;
                }
                animation = new CoverAnimation(target, texture, now);
                ANIMATIONS.put(pos, animation);
            } else if (!advance(animation, target, now)) {
                ANIMATIONS.remove(pos);
                continue;
            }

            double slide = slideOf(animation, now);
            if (slide >= 1.0D) {
                // 完全退到槽口上方，看不见
                continue;
            }

            double distanceSqr = minecraft.player.distanceToSqr(
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D
            );
            if (distanceSqr > RENDER_DISTANCE_SQR) {
                continue;
            }

            renderSides(collector, camera, pos, animation.texture, slide);
        }
    }

    /**
     * 推进动画状态机。
     *
     * <p>两条"防抖"规则，都是为了不再出现封面闪一下/收回再滑下来：</p>
     * <ul>
     *   <li><b>状态短暂消失不算取出</b>：服务端 {@code stop}/{@code play} 抖动、区块重载、
     *       玩家在可听范围边界来回走，都会让状态消失一两帧。要连续消失
     *       {@link #MISSING_GRACE_SECONDS} 才认为唱片真的被取走。</li>
     *   <li><b>新封面下载好才换</b>：目标封面变了之后，先让旧封面继续挂着，等新图就绪再滑出
     *       滑入。否则换封面那一瞬间槽是空的，看起来就是闪一下。等太久（
     *       {@link #SWITCH_WAIT_SECONDS}）则照常滑出，在 {@link Phase#HIDDEN} 里继续等。</li>
     * </ul>
     *
     * @param target 本帧期望显示的封面地址，{@code null} 表示唱片机里已经没有唱片
     * @return {@code false} 表示动画已经结束、可以从表里删掉
     */
    private static boolean advance(CoverAnimation animation, String target, double now) {
        if (target == null) {
            if (animation.missingSince < 0.0D) {
                animation.missingSince = now;
            }
        } else {
            animation.missingSince = -1.0D;
        }
        // 消失超过宽限时间才当作"唱片被取出"
        boolean removed = target == null && now - animation.missingSince >= MISSING_GRACE_SECONDS;

        switch (animation.phase) {
            case SLIDE_IN -> {
                double progress = (now - animation.phaseStart) / SLIDE_IN_SECONDS;
                if (progress >= 1.0D) {
                    animation.phase = Phase.IDLE;
                    animation.phaseStart = now;
                } else if (removed || shouldSwitch(animation, target, now)) {
                    // 滑到一半换歌或者被取出：从当前位置接着往上滑，不要跳变
                    startPhase(animation, Phase.SLIDE_OUT, now);
                }
            }
            case IDLE -> {
                if (removed || shouldSwitch(animation, target, now)) {
                    startPhase(animation, Phase.SLIDE_OUT, now);
                }
            }
            case SLIDE_OUT -> {
                if ((now - animation.phaseStart) / SLIDE_OUT_SECONDS >= 1.0D) {
                    if (target == null) {
                        // 唱片被取出，滑出去就结束
                        return false;
                    }
                    if (target.equals(animation.url)) {
                        // 同一张唱片又回来了，重新滑进来
                        startPhase(animation, Phase.SLIDE_IN, now);
                    } else {
                        Identifier texture = CoverArtTextureCache.getInstance().getTextureId(target);
                        if (texture == null) {
                            // 下一首的封面还在下载：停在槽口上方等它
                            CoverArtTextureCache.getInstance().request(target);
                            startPhase(animation, Phase.HIDDEN, now);
                        } else {
                            animation.url = target;
                            animation.texture = texture;
                            startPhase(animation, Phase.SLIDE_IN, now);
                        }
                    }
                    animation.switchSince = -1.0D;
                }
            }
            case HIDDEN -> {
                if (target == null) {
                    if (removed) {
                        return false;
                    }
                    return true;
                }
                if (target.equals(animation.url)) {
                    startPhase(animation, Phase.SLIDE_IN, now);
                    return true;
                }
                Identifier texture = CoverArtTextureCache.getInstance().getTextureId(target);
                if (texture == null) {
                    CoverArtTextureCache.getInstance().request(target);
                    return true;
                }
                animation.url = target;
                animation.texture = texture;
                startPhase(animation, Phase.SLIDE_IN, now);
            }
        }
        return true;
    }

    /**
     * 目标封面变了，并且新封面已经下载好（或者等太久了）才允许开始换。
     *
     * <p>新封面还没就绪时返回 {@code false}，让旧封面继续显示，避免槽出现空档。</p>
     */
    private static boolean shouldSwitch(CoverAnimation animation, String target, double now) {
        if (target == null || target.equals(animation.url)) {
            animation.switchSince = -1.0D;
            return false;
        }
        if (animation.switchSince < 0.0D) {
            animation.switchSince = now;
        }
        if (CoverArtTextureCache.getInstance().getTextureId(target) != null) {
            return true;
        }
        CoverArtTextureCache.getInstance().request(target);
        return now - animation.switchSince >= SWITCH_WAIT_SECONDS;
    }

    /**
     * 切换动画阶段，并把当前滑动量记成起点。
     *
     * <p>这样无论在哪一帧被打断（滑到一半换歌、滑到一半被取出），封面都会从当前位置继续，
     * 不会瞬移。</p>
     */
    private static void startPhase(CoverAnimation animation, Phase phase, double now) {
        animation.fromSlide = slideOf(animation, now);
        animation.phase = phase;
        animation.phaseStart = now;
    }

    /** 当前滑动量：0 = 贴在槽底，1 = 完全退到槽口上方。 */
    private static double slideOf(CoverAnimation animation, double now) {
        return switch (animation.phase) {
            case SLIDE_IN -> animation.fromSlide
                    * (1.0D - easeOutQuint(clamp01((now - animation.phaseStart) / SLIDE_IN_SECONDS)));
            case IDLE -> 0.0D;
            case SLIDE_OUT -> animation.fromSlide
                    + (1.0D - animation.fromSlide) * easeInQuad(clamp01((now - animation.phaseStart) / SLIDE_OUT_SECONDS));
            case HIDDEN -> 1.0D;
        };
    }

    private static void renderSides(SubmitNodeCollector collector, Vec3 camera, BlockPos pos,
                                    Identifier texture, double slide) {
        double x0 = pos.getX() - camera.x;
        double y0 = pos.getY() - camera.y;
        double z0 = pos.getZ() - camera.z;
        int packedLight = resolvePackedLight(pos);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            renderSide(collector, x0, y0, z0, side, texture, packedLight, slide);
        }
    }

    /**
     * 方块位置的光照，但给方块光照留一个下限。
     *
     * <p>直接跟随环境光时，无光的地方（封闭房间、夜里没灯）光照坐标是 0，封面会整块变黑。
     * 这里把方块光照抬到 {@link #MIN_BLOCK_LIGHT}，保证任何环境下都看得见；环境更亮时仍然
     * 跟随环境变亮。</p>
     */
    private static int resolvePackedLight(BlockPos pos) {
        int light = LightCoordsUtil.getLightCoords(Minecraft.getInstance().level, pos);
        int blockLight = Math.max(LightCoordsUtil.block(light), MIN_BLOCK_LIGHT);
        int skyLight = LightCoordsUtil.sky(light);
        return LightCoordsUtil.pack(blockLight, skyLight);
    }

    private static void renderSide(SubmitNodeCollector collector, double x0, double y0, double z0,
                                   Direction side, Identifier texture, int packedLight, double slide) {
        double x1 = x0 + 1.0D;
        double y1 = y0 + 1.0D;
        double z1 = z0 + 1.0D;
        double width = REGION_U1 - REGION_U0;
        double height = REGION_V1 - REGION_V0;

        // 槽口上下沿（世界坐标）与当前封面的上下沿
        double pocketTop = y1 - REGION_V0;
        double pocketBottom = y1 - REGION_V1;
        double coverTop = pocketTop + slide * height;
        double coverBottom = coverTop - height;

        // 只画和槽口的交集：滑到槽口外面的部分直接裁掉
        double visibleTop = Math.min(coverTop, pocketTop);
        double visibleBottom = Math.max(coverBottom, pocketBottom);
        if (visibleTop - visibleBottom <= 1.0e-4D) {
            // 已经完全退到槽口上方（y 轴向上，所以是上沿减下沿）
            return;
        }
        // 交集对应封面贴图上的哪一段（0 = 封面顶边，1 = 封面底边）
        double texTop = (coverTop - visibleTop) / height;
        double texBottom = (coverTop - visibleBottom) / height;

        // 顶点顺序：左上、左下、右下、右上——从外面看是逆时针，正面朝外
        boolean[] atBottom = {false, true, true, false};
        double[] acrossValues = {0.0D, 0.0D, 1.0D, 1.0D};
        float[] texU = {0.0F, 0.0F, 1.0F, 1.0F};

        float[] xs = new float[4];
        float[] ys = new float[4];
        float[] zs = new float[4];
        float[] vs = new float[4];
        for (int index = 0; index < 4; index++) {
            // across：从封面屏幕左边缘量起的横向位置（0=左，1=右）
            double across = REGION_U0 + acrossValues[index] * width;
            ys[index] = (float) (atBottom[index] ? visibleBottom : visibleTop);
            vs[index] = (float) (atBottom[index] ? texBottom : texTop);
            switch (side) {
                case NORTH -> {
                    // 屏幕左 = x 较大；封面贴在槽底（往里 1 像素）
                    xs[index] = (float) (x1 - across);
                    zs[index] = (float) (z0 + RECESS_DEPTH - FACE_OFFSET);
                }
                case SOUTH -> {
                    // 屏幕左 = x 较小
                    xs[index] = (float) (x0 + across);
                    zs[index] = (float) (z1 - RECESS_DEPTH + FACE_OFFSET);
                }
                case WEST -> {
                    // 屏幕左 = z 较小
                    xs[index] = (float) (x0 + RECESS_DEPTH - FACE_OFFSET);
                    zs[index] = (float) (z0 + across);
                }
                case EAST -> {
                    // 屏幕左 = z 较大
                    xs[index] = (float) (x1 - RECESS_DEPTH + FACE_OFFSET);
                    zs[index] = (float) (z1 - across);
                }
                default -> throw new IllegalArgumentException("Unexpected side: " + side);
            }
        }

        float normalX = side.getStepX();
        float normalY = side.getStepY();
        float normalZ = side.getStepZ();

        PoseStack poseStack = new PoseStack();
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, consumer) -> {
            for (int index = 0; index < 4; index++) {
                consumer.addVertex(pose.pose(), xs[index], ys[index], zs[index])
                        .setColor(COVER_SHADE, COVER_SHADE, COVER_SHADE, 255)
                        .setUv(texU[index], vs[index])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(packedLight)
                        .setNormal(pose, normalX, normalY, normalZ);
            }
        });
    }

    private static double clamp01(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        return Math.min(value, 1.0D);
    }

    /**
     * 起步快、后面长长地减速，滑下来像有惯性。
     *
     * <p>刻意<b>不过冲</b>：滑动量一旦越过 0，封面顶部就会退到槽口上沿之上，露出槽口里的
     * 唱片机材质，看起来像弹了一下。</p>
     */
    private static double easeOutQuint(double progress) {
        double inverse = 1.0D - progress;
        return 1.0D - inverse * inverse * inverse * inverse * inverse;
    }

    /** 起步慢、越抽越快，像被从槽里抽出去。 */
    private static double easeInQuad(double progress) {
        return progress * progress;
    }

    private enum Phase {
        /** 从槽口上方滑到槽底。 */
        SLIDE_IN,
        /** 贴在槽底。 */
        IDLE,
        /** 从槽底退到槽口上方。 */
        SLIDE_OUT,
        /** 已经退到槽口上方，等下一首的封面下载好。 */
        HIDDEN
    }

    private static final class CoverAnimation {
        private String url;
        private Identifier texture;
        private Phase phase;
        private double phaseStart;
        /** 本阶段开始时的滑动量，用来保证被打断时从当前位置接着走。 */
        private double fromSlide;
        /** 状态从什么时候开始"看不见"；-1 表示当前有状态。 */
        private double missingSince = -1.0D;
        /** 从什么时候开始想换封面（用来给下载留时间）；-1 表示没在等。 */
        private double switchSince = -1.0D;

        private CoverAnimation(String url, Identifier texture, double now) {
            this.url = url;
            this.texture = texture;
            this.phase = Phase.SLIDE_IN;
            this.phaseStart = now;
            this.fromSlide = 1.0D;
        }
    }
}
