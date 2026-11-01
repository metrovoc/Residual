package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.net.SurveySync;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * What Surveyors found, seen through the ground while a Survey Rod is held: a box in each find's colour in every place a
 * survey found one. A Surveyor sends its places every second to the players near it holding a rod; a survey not heard of
 * for three seconds fades out.
 */
public final class SurveyView {
    private static final ContextKey<List<Mark>> KEY = new ContextKey<>(Residual.id("survey"));
    private static final double RANGE = 48;
    private static final long KEPT = 3_000_000_000L;
    private static final int FULL_BRIGHT = 0xF000F0;
    /**
     * Each find's colour, in the order of the survey's letters: coal, iron, copper, gold, redstone, lapis, diamond,
     * emerald, and violet for a cache.
     */
    private static final int[] COLORS = {0x80909090, 0x80E6C7A8, 0x80E8835A, 0x80FFE04A, 0x80FF4A4A, 0x805A7DFF, 0x8060F0E8, 0x8040E07A,
            0x80C68CFF};
    private static final Map<BlockPos, Found> SURVEYS = new HashMap<>();

    private record Found(long[] places, byte[] ores, long at) {
    }

    private record Mark(AABB box, int argb) {
    }

    private SurveyView() {
    }

    public static void receive(SurveySync sync) {
        SURVEYS.put(sync.surveyor(), new Found(sync.places(), sync.ores(), System.nanoTime()));
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (SURVEYS.isEmpty() || player == null) {
            return;
        }
        long now = System.nanoTime();
        SURVEYS.values().removeIf(f -> now - f.at > KEPT);
        if (!player.getMainHandItem().is(ModItems.SURVEY_ROD.get()) && !player.getOffhandItem().is(ModItems.SURVEY_ROD.get())) {
            return;
        }
        Vec3 camera = event.getCamera().position();
        List<Mark> marks = new ArrayList<>();
        for (Found f : SURVEYS.values()) {
            for (int i = 0; i < f.places.length; i++) {
                BlockPos p = BlockPos.of(f.places[i]);
                if (p.getCenter().distanceToSqr(camera) < RANGE * RANGE) {
                    marks.add(new Mark(new AABB(p).deflate(0.2), COLORS[Math.floorMod(f.ores[i], COLORS.length)]));
                }
            }
        }
        if (!marks.isEmpty()) {
            event.getRenderState().setRenderData(KEY, marks);
        }
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        List<Mark> marks = event.getLevelRenderState().getRenderData(KEY);
        if (marks == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        // drawn without the depth test, as a name tag's background is, so the ground does not hide them
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.textBackgroundSeeThrough(), (pose, buffer) -> {
            for (Mark m : marks) {
                box(pose, buffer, m.box().move(-camera.x, -camera.y, -camera.z), m.argb());
            }
        });
    }

    /** A box's six faces, each wound both ways, at full light. */
    private static void box(PoseStack.Pose pose, VertexConsumer buffer, AABB b, int argb) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
        };
        for (float[] q : faces) {
            for (int v = 0; v < 4; v++) {
                buffer.addVertex(pose, q[v * 3], q[v * 3 + 1], q[v * 3 + 2]).setColor(argb).setLight(FULL_BRIGHT);
            }
            for (int v = 3; v >= 0; v--) {
                buffer.addVertex(pose, q[v * 3], q[v * 3 + 1], q[v * 3 + 2]).setColor(argb).setLight(FULL_BRIGHT);
            }
        }
    }
}
