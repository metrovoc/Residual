package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.view.Heatmap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Paints a machine's pictures into the windows of its faces: what it outputs on the front, its main weight matrix on
 * the sides of machines that learn. The pictures glow, like the displays they are.
 */
public class MachineRenderer implements BlockEntityRenderer<OperatorBlockEntity, MachineRenderer.State> {
    private static final float LO = 2 / 16f;
    private static final float HI = 14 / 16f;
    private static final float LIFT = 0.002f;
    private static final int FULL_BRIGHT = 0xF000F0;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        @Nullable
        Heatmap output;
        @Nullable
        Heatmap weights;
        boolean sideWindows;
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
        });
    }

    @Override
    public int getViewDistance() {
        return 48;
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
}
