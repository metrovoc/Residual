package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.net.WaveSync;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Plays the waves a training model sends through its blocks: a teal pulse runs from the tokens to the loss, then an
 * amber one runs back. Each block glows as its node's turn comes, as brightly as the vector it carries.
 */
public final class Waves {
    private static final ContextKey<List<Glow>> KEY = new ContextKey<>(Residual.id("waves"));
    private static final long CYCLE = 2_000_000_000L;
    /** Each pulse crosses the model in this part of a cycle; the backward one starts halfway. */
    private static final float CROSSING = 0.42f;
    private static final float BACK = 0.5f;
    private static final int TEAL = 0x3CD6C6, AMBER = 0xFFB547;
    private static final double RANGE = 96;
    private static final Map<BlockPos, Arrived> ARRIVED = new HashMap<>();

    private record Arrived(WaveSync wave, long at, LongOpenHashSet blocks) {
    }

    /** A glowing box in world space. */
    private record Glow(AABB box, int argb) {
    }

    private Waves() {
    }

    public static void receive(WaveSync wave) {
        ARRIVED.put(wave.core(), new Arrived(wave, System.nanoTime(), new LongOpenHashSet(wave.blocks())));
    }

    /** Whether the block belongs to a model that is training, as far as the last waves tell. */
    public static boolean training(BlockPos pos) {
        long now = System.nanoTime(), p = pos.asLong();
        for (Arrived a : ARRIVED.values()) {
            if (now - a.at < 2 * CYCLE && a.blocks.contains(p)) {
                return true;
            }
        }
        return false;
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        if (ARRIVED.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        // a late wave keeps the model counted as training, but only a fresh one is played
        ARRIVED.values().removeIf(a -> now - a.at > 2 * CYCLE);
        ClientLevel level = event.getLevel();
        Vec3 camera = event.getCamera().position();
        List<Glow> glows = new ArrayList<>();
        for (Arrived a : ARRIVED.values()) {
            WaveSync w = a.wave;
            float phase = (now - a.at) / (float) CYCLE;
            if (phase >= 1) {
                continue;
            }
            float width = Math.max(1.5f / w.slots(), 0.05f);
            for (int i = 0; i < w.blocks().length; i++) {
                float at = (w.slot()[i] + 0.5f) / w.slots();
                float f = pulse(phase / CROSSING - at, width) * (w.forward()[i] & 0xff) / 255f;
                float b = pulse((phase - BACK) / CROSSING - (1 - at), width) * (w.backward()[i] & 0xff) / 255f;
                if (f < 0.02f && b < 0.02f) {
                    continue;
                }
                BlockPos pos = BlockPos.of(w.blocks()[i]);
                if (!pos.closerToCenterThan(camera, RANGE)) {
                    continue;
                }
                VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
                int argb = color(f, b);
                for (AABB box : shape.toAabbs()) {
                    glows.add(new Glow(box.move(pos).inflate(box.getSize() > 0.9 ? 0.03 : 0.015), argb));
                }
            }
        }
        if (!glows.isEmpty()) {
            event.getRenderState().setRenderData(KEY, glows);
        }
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        List<Glow> glows = event.getLevelRenderState().getRenderData(KEY);
        if (glows == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, buffer) -> {
            for (Glow g : glows) {
                box(pose, buffer, g.box.move(-camera.x, -camera.y, -camera.z), g.argb);
            }
        });
    }

    private static float pulse(float x, float width) {
        float t = x / width;
        return t * t > 16 ? 0 : (float) Math.exp(-t * t);
    }

    /** Teal for activations and amber for gradients, added where both pass at once; alpha is the brightness. */
    private static int color(float f, float b) {
        float sum = f + b;
        int r = channel(TEAL >> 16, AMBER >> 16, f, b, sum);
        int g = channel(TEAL >> 8 & 0xff, AMBER >> 8 & 0xff, f, b, sum);
        int bl = channel(TEAL & 0xff, AMBER & 0xff, f, b, sum);
        int alpha = Math.round(Math.min(1f, sum) * 0.6f * 255);
        return alpha << 24 | r << 16 | g << 8 | bl;
    }

    private static int channel(int from, int to, float f, float b, float sum) {
        return Math.round((from * f + to * b) / sum);
    }

    /** All six faces, wound both ways so the glow reads as a volume from any side. */
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
                buffer.addVertex(pose, q[v * 3], q[v * 3 + 1], q[v * 3 + 2]).setColor(argb);
            }
            for (int v = 3; v >= 0; v--) {
                buffer.addVertex(pose, q[v * 3], q[v * 3 + 1], q[v * 3 + 2]).setColor(argb);
            }
        }
    }
}
