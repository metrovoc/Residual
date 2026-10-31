package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.metrovoc.residual.storage.RackBlock;
import io.github.metrovoc.residual.storage.RackBlockEntity;
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

/** Lights each bay on a Drive Rack's front by how full its drive is: dim when empty, teal, amber, red when full. */
public class RackRenderer implements BlockEntityRenderer<RackBlockEntity, RackRenderer.State> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int[] COLORS = {0, 0xFF27413F, 0xFF3CD6C6, 0xFFFFB547, 0xFFFF6B6B};
    private static final int[] BAY_X = {2, 5, 9, 12}, BAY_Y = {3, 9};

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        int[] lights = new int[RackBlockEntity.BAYS];
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RackBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.facing = be.getBlockState().getValue(RackBlock.FACING);
        state.lights = be.lights().clone();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        collector.submitCustomGeometry(poseStack, RenderTypes.textBackground(), (pose, buffer) -> {
            for (int i = 0; i < RackBlockEntity.BAYS; i++) {
                int color = COLORS[Math.clamp(state.lights[i], 0, COLORS.length - 1)];
                if (color != 0) {
                    // the bays sit in two banks of two pairs on the front texture (tools/art.py, RACK_BAY_X and
                    // RACK_BAY_Y); each light runs along a bay's top
                    float u0 = BAY_X[i % 4] / 16f, v0 = BAY_Y[i / 4] / 16f;
                    quad(pose, buffer, state.facing, u0, v0, u0 + 2 / 16f, v0 + 1 / 16f, color);
                }
            }
        });
    }

    /** A rectangle on the face pointing {@code normal}, in the face's own u (right) and v (down). */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Direction normal, float u0, float v0, float u1, float v1, int color) {
        Vector3f n = normal.step();
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(up).cross(n);
        Vector3f center = new Vector3f(0.5f, 0.5f, 0.5f).add(new Vector3f(n).mul(0.502f));
        vertex(pose, buffer, center, right, up, u0, v1, color);
        vertex(pose, buffer, center, right, up, u1, v1, color);
        vertex(pose, buffer, center, right, up, u1, v0, color);
        vertex(pose, buffer, center, right, up, u0, v0, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, Vector3f right, Vector3f up,
                               float u, float v, int color) {
        float x = center.x + right.x * (u - 0.5f) + up.x * (0.5f - v);
        float y = center.y + right.y * (u - 0.5f) + up.y * (0.5f - v);
        float z = center.z + right.z * (u - 0.5f) + up.z * (0.5f - v);
        buffer.addVertex(pose, x, y, z).setColor(color).setLight(FULL_BRIGHT);
    }
}
