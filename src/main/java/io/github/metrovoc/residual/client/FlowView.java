package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.compute.ComputeHost;
import io.github.metrovoc.residual.compute.ComputeNode;
import io.github.metrovoc.residual.energy.PowerNode;
import io.github.metrovoc.residual.net.CurrentSync;
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
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

/**
 * What moves through a storage network, drawn along its links from a Port, a Dock or an Assembler to the drive it went into,
 * or back out of it: the things themselves riding on the link, and under them pulses of light, teal into the network, amber
 * out, red where a link is full; a fluid's pulses are its own color. A bigger load makes a brighter, longer stream. A program on a Port
 * shows what it reaches the same way, straight through the air. Power runs along its
 * cables the same way, in coral, and a split run's work along its Interconnect, in teal.
 */
public final class FlowView {
    private static final ContextKey<List<Dot>> DOTS = new ContextKey<>(Residual.id("flows"));
    private static final ContextKey<List<Riding>> RIDING = new ContextKey<>(Residual.id("flow_items"));
    /** Blocks a pulse travels a second. */
    private static final double SPEED = 12;
    private static final int TEAL = 0x3CD6C6, AMBER = 0xFFB547, RED = 0xFF6B6B, CORAL = 0xFF7A5C;
    private static final int MOST = 256, LONGEST = 4096;
    private static final double RANGE = 64;
    /** How close things have to be to be drawn, how many a frame, and how far apart in a stream. */
    private static final double SEEN_RIDING = 32;
    private static final int MOST_RIDING = 96;
    private static final double GAP = 0.45;
    /** How far above a link's centre they ride. */
    private static final double RIDE = 0.2;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final List<Pulse> PULSES = new ArrayList<>();

    private record Pulse(List<Vec3> path, long start, int rgb, int dots, @Nullable ItemStackRenderState item) {
    }

    private record Dot(AABB box, int argb) {
    }

    private record Riding(Vec3 pos, float spin, ItemStackRenderState item) {
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
            path = straight(flow.from(), flow.to());
        }
        if (!flow.in()) {
            Collections.reverse(path);
        }
        int dots = Math.clamp(1 + (int) (Math.log(flow.count()) / Math.log(4)), 1, 5);
        int rgb = flow.full() ? RED : !flow.fluid().isEmpty() ? tint(flow.fluid()) : flow.in() ? TEAL : AMBER;
        ItemStackRenderState item = null;
        if (!flow.item().isEmpty()) {
            item = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, flow.item().toStack(), ItemDisplayContext.GROUND, level, null, 0);
        }
        PULSES.add(new Pulse(path, System.nanoTime(), rgb, dots, item));
    }

    /** A fluid's color, as a tank's window shows it. */
    private static int tint(FluidResource fluid) {
        var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        if (model.fluidTintSource() != null) {
            return model.fluidTintSource().colorAsStack(fluid.toStack(1000)) & 0xFFFFFF;
        }
        return Ui.sprited(model.stillMaterial().sprite());
    }

    /** Where no links join them, as between a Port and what its program reaches: a straight line, a point a block. */
    private static List<Vec3> straight(BlockPos from, BlockPos to) {
        Vec3 a = from.getCenter(), b = to.getCenter();
        int blocks = Math.max(1, (int) Math.ceil(a.distanceTo(b)));
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= blocks; i++) {
            path.add(a.lerp(b, (double) i / blocks));
        }
        return path;
    }

    public static void receive(CurrentSync current) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || PULSES.size() >= MOST) {
            return;
        }
        List<Vec3> path = route(level, current.from(), current.to());
        if (path.size() >= 2) {
            PULSES.add(new Pulse(path, System.nanoTime(), current.work() ? TEAL : CORAL, Math.clamp(1 + (int) (Math.log(Math.max(1, current.count())) / Math.log(16)), 1, 5), null));
        }
    }

    private static boolean joins(BlockState state, Direction d) {
        Block b = state.getBlock();
        return b instanceof Node link ? link.joins(state, d) : b instanceof PowerNode cable && cable.joins(state, d)
                || b instanceof ComputeNode line && line.links(state, d) || b instanceof ComputeHost;
    }

    /** The way through storage, power or compute blocks from one to the other, block centre to block centre, or nothing. */
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
            for (Direction d : Direction.values()) {
                BlockPos n = at.relative(d);
                if (came.containsKey(n) || !joins(here, d)) {
                    continue;
                }
                if (joins(level.getBlockState(n), d.getOpposite())) {
                    came.put(n, at);
                    next.add(n);
                }
            }
        }
        return List.of();
    }

    /** Where a stream's point is {@code at} blocks along its path. */
    private static Vec3 along(List<Vec3> path, double at) {
        int k = (int) Math.floor(at);
        return path.get(k).lerp(path.get(Math.min(k + 1, path.size() - 1)), at - k);
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        if (PULSES.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        Vec3 camera = event.getCamera().position();
        List<Dot> dots = new ArrayList<>();
        List<Riding> riding = new ArrayList<>();
        PULSES.removeIf(p -> (now - p.start) / 1e9 * SPEED > p.path.size() + p.dots);
        float spin = (float) (now / 1e9 * 90 % 360);
        for (Pulse p : PULSES) {
            double head = (now - p.start) / 1e9 * SPEED;
            for (int i = 0; i < p.dots; i++) {
                double at = head - i * 0.35;
                if (at >= 0 && at <= p.path.size() - 1) {
                    Vec3 pos = along(p.path, at);
                    if (pos.distanceToSqr(camera) <= RANGE * RANGE) {
                        // just wider than a link's 6 px core, so the pulse glows around it rather than hidden inside
                        double r = i == 0 ? 0.22 : 0.2;
                        int alpha = (int) (170 * (1 - i / (double) (p.dots + 1)));
                        dots.add(new Dot(new AABB(pos.subtract(r, r, r), pos.add(r, r, r)), alpha << 24 | p.rgb));
                    }
                }
                double thing = head - i * GAP;
                if (p.item != null && riding.size() < MOST_RIDING && thing >= 0 && thing <= p.path.size() - 1) {
                    Vec3 pos = along(p.path, thing).add(0, RIDE, 0);
                    if (pos.distanceToSqr(camera) <= SEEN_RIDING * SEEN_RIDING) {
                        riding.add(new Riding(pos, spin, p.item));
                    }
                }
            }
        }
        if (!dots.isEmpty()) {
            event.getRenderState().setRenderData(DOTS, dots);
        }
        if (!riding.isEmpty()) {
            event.getRenderState().setRenderData(RIDING, riding);
        }
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        List<Dot> dots = event.getLevelRenderState().getRenderData(DOTS);
        if (dots != null) {
            event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, buffer) -> {
                for (Dot d : dots) {
                    Waves.box(pose, buffer, d.box.move(-camera.x, -camera.y, -camera.z), d.argb);
                }
            });
        }
        List<Riding> riding = event.getLevelRenderState().getRenderData(RIDING);
        if (riding != null) {
            PoseStack stack = event.getPoseStack();
            for (Riding r : riding) {
                stack.pushPose();
                stack.translate(r.pos.x - camera.x, r.pos.y - camera.y, r.pos.z - camera.z);
                stack.mulPose(Axis.YP.rotationDegrees(r.spin));
                r.item.submit(stack, event.getSubmitNodeCollector(), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                stack.popPose();
            }
        }
    }
}
