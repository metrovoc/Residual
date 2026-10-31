package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.net.GhostSync;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The Architect's builds before they stand: a faint teal block in every place an agent will still lay one. Each agent
 * sends its places every two seconds while it builds; a ghost not heard of for three seconds fades out.
 */
public final class GhostView {
    private static final ContextKey<List<AABB>> KEY = new ContextKey<>(Residual.id("ghosts"));
    private static final int ARGB = 0x283CD6C6;
    private static final double RANGE = 64;
    private static final long KEPT = 3_000_000_000L;
    private static final Map<Integer, Ghost> GHOSTS = new HashMap<>();

    private record Ghost(long[] places, long at) {
    }

    private GhostView() {
    }

    public static void receive(GhostSync sync) {
        if (sync.places().length == 0) {
            GHOSTS.remove(sync.agent());
        } else {
            GHOSTS.put(sync.agent(), new Ghost(sync.places(), System.nanoTime()));
        }
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (GHOSTS.isEmpty() || level == null) {
            return;
        }
        long now = System.nanoTime();
        GHOSTS.values().removeIf(g -> now - g.at > KEPT);
        Vec3 camera = event.getCamera().position();
        List<AABB> boxes = new ArrayList<>();
        for (Ghost g : GHOSTS.values()) {
            for (long packed : g.places) {
                BlockPos p = BlockPos.of(packed);
                // laid since: the block itself shows now
                if (p.getCenter().distanceToSqr(camera) < RANGE * RANGE && level.getBlockState(p).canBeReplaced()) {
                    boxes.add(new AABB(p).deflate(0.04));
                }
            }
        }
        if (!boxes.isEmpty()) {
            event.getRenderState().setRenderData(KEY, boxes);
        }
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        List<AABB> boxes = event.getLevelRenderState().getRenderData(KEY);
        if (boxes == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, buffer) -> {
            for (AABB box : boxes) {
                Waves.box(pose, buffer, box.move(-camera.x, -camera.y, -camera.z), ARGB);
            }
        });
    }
}
