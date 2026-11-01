package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.metrovoc.residual.block.GateBlock;
import io.github.metrovoc.residual.block.GateBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** An open gate's shaft of light: four walls fading upward over its pad, and frames of light rising through them. */
public class GateRenderer implements BlockEntityRenderer<GateBlockEntity, GateRenderer.State> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int RGB = 0x3CD6C6;
    private static final float INSET = 2 / 16f, FLOOR = 4 / 16f, HEIGHT = 2.5f;
    private static final int FRAMES = 3;
    /** Ticks a frame takes to rise. */
    private static final float RISE = 50;

    public static class State extends BlockEntityRenderState {
        boolean open;
        float time;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GateBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.open = be.getBlockState().getValue(GateBlock.OPEN);
        state.time = be.getLevel() == null ? 0 : be.getLevel().getGameTime() % 100_000 + partialTicks;
    }

    @Override
    public AABB getRenderBoundingBox(GateBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, HEIGHT, 0);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.open) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.textBackground(), (pose, buffer) -> {
            float a = INSET, b = 1 - INSET;
            wall(pose, buffer, a, a, b, a);
            wall(pose, buffer, b, a, b, b);
            wall(pose, buffer, b, b, a, b);
            wall(pose, buffer, a, b, a, a);
            for (int i = 0; i < FRAMES; i++) {
                float t = (state.time / RISE + i / (float) FRAMES) % 1;
                float y = FLOOR + t * HEIGHT;
                int alpha = (int) (150 * (1 - t));
                int argb = alpha << 24 | RGB;
                float thin = 0.015f;
                RelayView.box(pose, buffer, new AABB(a, y, a, b, y + thin, a + thin), argb);
                RelayView.box(pose, buffer, new AABB(a, y, b - thin, b, y + thin, b), argb);
                RelayView.box(pose, buffer, new AABB(a, y, a, a + thin, y + thin, b), argb);
                RelayView.box(pose, buffer, new AABB(b - thin, y, a, b, y + thin, b), argb);
            }
        });
    }

    /** One wall of the shaft, seen from both sides, bright at its foot and gone at its top. */
    private static void wall(PoseStack.Pose pose, VertexConsumer buffer, float x0, float z0, float x1, float z1) {
        int foot = 70 << 24 | RGB, top = RGB;
        float y0 = FLOOR, y1 = FLOOR + HEIGHT;
        buffer.addVertex(pose, x0, y0, z0).setColor(foot).setLight(FULL_BRIGHT);
        buffer.addVertex(pose, x1, y0, z1).setColor(foot).setLight(FULL_BRIGHT);
        buffer.addVertex(pose, x1, y1, z1).setColor(top).setLight(FULL_BRIGHT);
        buffer.addVertex(pose, x0, y1, z0).setColor(top).setLight(FULL_BRIGHT);
        buffer.addVertex(pose, x0, y1, z0).setColor(top).setLight(FULL_BRIGHT);
        buffer.addVertex(pose, x1, y1, z1).setColor(top).setLight(FULL_BRIGHT);
        buffer.addVertex(pose, x1, y0, z1).setColor(foot).setLight(FULL_BRIGHT);
        buffer.addVertex(pose, x0, y0, z0).setColor(foot).setLight(FULL_BRIGHT);
    }
}
