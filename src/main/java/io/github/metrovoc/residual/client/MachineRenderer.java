package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.view.Heatmap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Paints a machine's pictures into the windows of its faces: what it outputs on the front, its main weight matrix on
 * the sides of machines that learn. The pictures glow, like the displays they are. While its model trains, or while
 * the player holds a Tensor Probe, the machine also projects its output as a hologram above itself.
 */
public class MachineRenderer implements BlockEntityRenderer<OperatorBlockEntity, MachineRenderer.State> {
    private static final float LO = 2 / 16f;
    private static final float HI = 14 / 16f;
    private static final float LIFT = 0.002f;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int PANEL = 0x900E1216, BORDER = 0xD03CD6C6, BEAM = 0x283CD6C6;
    private static final float BORDER_WIDTH = 0.02f;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        @Nullable
        Heatmap output;
        @Nullable
        Heatmap weights;
        boolean sideWindows;
        boolean hologram;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(OperatorBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.facing = be.getBlockState().getValue(OperatorBlock.FACING);
        state.output = be.output();
        state.weights = be.weights();
        state.sideWindows = be.op().arity <= 1;
        Minecraft mc = Minecraft.getInstance();
        state.hologram = state.output != null && (Waves.training(be.getBlockPos())
                || mc.player != null && mc.player.isHolding(ModItems.PROBE.get()));
        if (state.hologram) {
            Component op = Component.translatable("block.residual." + be.op().id);
            Hologram.shown(be.getBlockPos(), state.output, be.name().isEmpty() ? op
                    : Component.literal(be.name() + " · ").append(op), be.status().shape());
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.output == null && (state.weights == null || !state.sideWindows)) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.textBackground(), (pose, buffer) -> {
            if (state.output != null) {
                face(pose, buffer, state.facing, state.output);
            }
            if (state.weights != null && state.sideWindows) {
                for (Direction d : Direction.values()) {
                    if (d.getAxis() != state.facing.getAxis()) {
                        face(pose, buffer, d, state.weights);
                    }
                }
            }
            if (state.hologram) {
                hologram(pose, buffer, Hologram.frame(state.blockPos, state.output, camera.pos), state.output,
                        Vec3.atLowerCornerOf(state.blockPos));
            }
        });
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public AABB getRenderBoundingBox(OperatorBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 1 + Hologram.LIFT + Hologram.SIZE, 0).inflate(0.1, 0, 0.1);
    }

    /** One quad per cell, inside the 12×12 window of the face pointing {@code normal}. */
    private static void face(PoseStack.Pose pose, VertexConsumer buffer, Direction normal, Heatmap map) {
        Vector3f n = normal.step();
        Vector3f up = normal.getAxis().isVertical() ? new Vector3f(0, 0, -1) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(up).cross(n);
        Vector3f center = new Vector3f(0.5f, 0.5f, 0.5f).add(new Vector3f(n).mul(0.5f + LIFT));
        int rows = map.rows(), cols = map.cols();
        for (int r = 0; r < rows; r++) {
            float v0 = LO + (HI - LO) * r / rows;
            float v1 = LO + (HI - LO) * (r + 1) / rows;
            for (int c = 0; c < cols; c++) {
                float u0 = LO + (HI - LO) * c / cols;
                float u1 = LO + (HI - LO) * (c + 1) / cols;
                int color = Ui.heat(map.value(r, c));
                vertex(pose, buffer, center, right, up, u0, v1, color);
                vertex(pose, buffer, center, right, up, u1, v1, color);
                vertex(pose, buffer, center, right, up, u1, v0, color);
                vertex(pose, buffer, center, right, up, u0, v0, color);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, Vector3f right, Vector3f up,
                               float u, float v, int color) {
        float x = center.x + right.x * (u - 0.5f) + up.x * (0.5f - v);
        float y = center.y + right.y * (u - 0.5f) + up.y * (0.5f - v);
        float z = center.z + right.z * (u - 0.5f) + up.z * (0.5f - v);
        buffer.addVertex(pose, x, y, z).setColor(color).setLight(FULL_BRIGHT);
    }

    /**
     * The panel, its frame and the faint beam that projects it, then the picture: a bar per column for a vector, a
     * cell per entry for a matrix, both in the faces' diverging colors.
     */
    private static void hologram(PoseStack.Pose pose, VertexConsumer buffer, Hologram.Frame f, Heatmap map, Vec3 origin) {
        Panel p = new Panel(pose, buffer, f, origin);
        double w = f.width() / 2, h = f.height() / 2;
        p.trapezoid(-0.08, 0.08, -h - Hologram.LIFT, -w, w, -h, BEAM);
        p.rect(-w, -h, w, h, 0, PANEL);
        double b = BORDER_WIDTH;
        p.rect(-w, -h, w, -h + b, 0.001, BORDER);
        p.rect(-w, h - b, w, h, 0.001, BORDER);
        p.rect(-w, -h + b, -w + b, h - b, 0.001, BORDER);
        p.rect(w - b, -h + b, w, h - b, 0.001, BORDER);
        double x0 = -w + 2 * b, x1 = w - 2 * b, y0 = -h + 2 * b, y1 = h - 2 * b;
        int rows = map.rows(), cols = map.cols();
        if (Hologram.bars(map)) {
            double mid = (y0 + y1) / 2, half = (y1 - y0) / 2, step = (x1 - x0) / cols;
            p.rect(x0, mid - 0.004, x1, mid + 0.004, 0.002, 0x603CD6C6);
            for (int c = 0; c < cols; c++) {
                float v = map.value(0, c);
                double top = mid + Math.max(v, 0) * half, bottom = mid + Math.min(v, 0) * half;
                p.rect(x0 + c * step + step * 0.12, bottom, x0 + (c + 1) * step - step * 0.12, top, 0.003,
                        0xE0000000 | Ui.heat(v) & 0xFFFFFF);
            }
            return;
        }
        for (int r = 0; r < rows; r++) {
            double top = y1 - (y1 - y0) * r / rows, bottom = y1 - (y1 - y0) * (r + 1) / rows;
            for (int c = 0; c < cols; c++) {
                double left = x0 + (x1 - x0) * c / cols, right = x0 + (x1 - x0) * (c + 1) / cols;
                p.rect(left, bottom, right, top, 0.002, 0xE0000000 | Ui.heat(map.value(r, c)) & 0xFFFFFF);
            }
        }
    }

    /** Draws in a hologram's plane: x along its width, y up, d toward the viewer, all from its center. */
    private record Panel(PoseStack.Pose pose, VertexConsumer buffer, Hologram.Frame f, Vec3 origin) {
        void rect(double x0, double y0, double x1, double y1, double d, int argb) {
            trapezoid(x0, x1, y0, x0, x1, y1, argb, d);
        }

        void trapezoid(double bottomLeft, double bottomRight, double bottom, double topLeft, double topRight, double top, int argb) {
            trapezoid(bottomLeft, bottomRight, bottom, topLeft, topRight, top, argb, 0);
        }

        private void trapezoid(double bl, double br, double bottom, double tl, double tr, double top, int argb, double d) {
            vertex(bl, bottom, d, argb);
            vertex(br, bottom, d, argb);
            vertex(tr, top, d, argb);
            vertex(tl, top, d, argb);
        }

        private void vertex(double x, double y, double d, int argb) {
            Vec3 n = f.right().cross(f.up());
            Vec3 at = f.center().subtract(origin).add(f.right().scale(x)).add(f.up().scale(y)).add(n.scale(d));
            buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(argb).setLight(FULL_BRIGHT);
        }
    }
}
