package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.net.FlowSync;
import io.github.metrovoc.residual.storage.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Items moving through a storage network, drawn as pulses of light running along its links from a Port or an
 * Assembler to the drive they went into, or back out of it: teal into the network, amber out. A bigger load makes a
 * brighter, longer pulse.
 */
public final class FlowView {
    private static final ContextKey<List<Dot>> KEY = new ContextKey<>(Residual.id("flows"));
    /** Blocks a pulse travels a second. */
    private static final double SPEED = 12;
    private static final int TEAL = 0x3CD6C6, AMBER = 0xFFB547;
    private static final int MOST = 256, LONGEST = 1024;
    private static final double RANGE = 64;
    private static final List<Pulse> PULSES = new ArrayList<>();

    private record Pulse(List<Vec3> path, long start, int rgb, int dots) {
    }

    private record Dot(AABB box, int argb) {
    }

    private FlowView() {
    }

    public static void receive(FlowSync flow) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || PULSES.size() >= MOST) {
            return;
        }
        List<Vec3> path = route(level, flow.from(), flow.to());
        if (path.size() < 2) {
            return;
        }
        if (!flow.in()) {
            Collections.reverse(path);
        }
        int dots = Math.clamp(1 + (int) (Math.log(flow.count()) / Math.log(4)), 1, 5);
        PULSES.add(new Pulse(path, System.nanoTime(), flow.in() ? TEAL : AMBER, dots));
    }

    /** The way through storage blocks from one to the other, block centre to block centre, or nothing. */
    private static List<Vec3> route(ClientLevel level, BlockPos from, BlockPos to) {
        Map<BlockPos, BlockPos> came = new HashMap<>();
        ArrayDeque<BlockPos> next = new ArrayDeque<>();
        came.put(from, from);
        next.add(from);
        while (!next.isEmpty() && came.size() < LONGEST) {
            BlockPos at = next.poll();
            if (at.equals(to)) {
                List<Vec3> path = new ArrayList<>();
                for (BlockPos p = to; ; p = came.get(p)) {
                    path.add(p.getCenter());
                    if (p.equals(from)) {
                        break;
                    }
                }
                Collections.reverse(path);
                return path;
            }
            BlockState here = level.getBlockState(at);
            if (!(here.getBlock() instanceof Node node)) {
                continue;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = at.relative(d);
                if (came.containsKey(n) || !node.joins(here, d)) {
                    continue;
                }
                BlockState there = level.getBlockState(n);
                if (there.getBlock() instanceof Node other && other.joins(there, d.getOpposite())) {
                    came.put(n, at);
                    next.add(n);
                }
            }
        }
        return List.of();
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        if (PULSES.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        Vec3 camera = event.getCamera().position();
        List<Dot> dots = new ArrayList<>();
        PULSES.removeIf(p -> (now - p.start) / 1e9 * SPEED > p.path.size() + p.dots);
        for (Pulse p : PULSES) {
            double head = (now - p.start) / 1e9 * SPEED;
            for (int i = 0; i < p.dots; i++) {
                double at = head - i * 0.35;
                if (at < 0 || at > p.path.size() - 1) {
                    continue;
                }
                int k = (int) Math.floor(at);
                Vec3 a = p.path.get(k), b = p.path.get(Math.min(k + 1, p.path.size() - 1));
                Vec3 pos = a.lerp(b, at - k);
                if (pos.distanceToSqr(camera) > RANGE * RANGE) {
                    continue;
                }
                // just wider than a link's 6 px core, so the pulse glows around it rather than hidden inside
                double r = i == 0 ? 0.22 : 0.2;
                int alpha = (int) (170 * (1 - i / (double) (p.dots + 1)));
                dots.add(new Dot(new AABB(pos.subtract(r, r, r), pos.add(r, r, r)), alpha << 24 | p.rgb));
            }
        }
        if (!dots.isEmpty()) {
            event.getRenderState().setRenderData(KEY, dots);
        }
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        List<Dot> dots = event.getLevelRenderState().getRenderData(KEY);
        if (dots == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, buffer) -> {
            for (Dot d : dots) {
                Waves.box(pose, buffer, d.box.move(-camera.x, -camera.y, -camera.z), d.argb);
            }
        });
    }
}
