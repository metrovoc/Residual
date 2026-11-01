package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.net.ShimmerSync;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Blocks being run ahead shimmer: a pulse of light around each, faster the faster it runs, teal up to ×16, amber past it and
 * near white at ×256. A block not heard of for a second stops.
 */
public final class ShimmerView {
    private record Shine(AABB box, int argb) {
    }

    private record Running(int pace, long at) {
    }

    private static final ContextKey<List<Shine>> KEY = new ContextKey<>(Residual.id("shimmer"));
    private static final double RANGE = 64;
    private static final long KEPT = 1_000_000_000L;
    private static final Map<Long, Running> RUNNING = new HashMap<>();

    private ShimmerView() {
    }

    public static void receive(ShimmerSync sync) {
        long now = System.nanoTime();
        for (int i = 0; i < sync.places().length; i++) {
            RUNNING.put(sync.places()[i], new Running(sync.paces().get(i), now));
        }
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        if (RUNNING.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        RUNNING.values().removeIf(r -> now - r.at > KEPT);
        Vec3 camera = event.getCamera().position();
        List<Shine> shines = new ArrayList<>();
        RUNNING.forEach((packed, r) -> {
            BlockPos p = BlockPos.of(packed);
            if (p.getCenter().distanceToSqr(camera) < RANGE * RANGE) {
                // a pulse a second at ×2, and up to five a second by ×256, each block in its own time
                double beats = 1 + Math.min(4, Math.log(r.pace) / Math.log(2) / 2);
                double wave = 0.5 + 0.5 * Math.sin(now / 1e9 * beats * 2 * Math.PI + packed % 97);
                int alpha = (int) (0x10 + wave * 0x28);
                int rgb = r.pace <= 16 ? 0x3CD6C6 : r.pace < 256 ? 0xFFB44A : 0xFFF2D0;
                shines.add(new Shine(new AABB(p).inflate(0.02 + 0.02 * wave), alpha << 24 | rgb));
            }
        });
        if (!shines.isEmpty()) {
            event.getRenderState().setRenderData(KEY, shines);
        }
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        List<Shine> shines = event.getLevelRenderState().getRenderData(KEY);
        if (shines == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, buffer) -> {
            for (Shine s : shines) {
                Waves.box(pose, buffer, s.box.move(-camera.x, -camera.y, -camera.z), s.argb);
            }
        });
    }
}
