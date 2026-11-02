package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.metrovoc.residual.compute.ClusterStatus;
import io.github.metrovoc.residual.compute.LoadWallBlock;
import io.github.metrovoc.residual.compute.LoadWallBlockEntity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Load Wall's display, drawn by the panel at its top left across all the panels of the run: a column for each rack, as
 * tall as that rack is busy, and over them what the cluster is running and drawing, large enough to read across a hall.
 */
public class LoadWallRenderer implements BlockEntityRenderer<LoadWallBlockEntity, LoadWallRenderer.State> {
    private static final int FULL_BRIGHT = 0xF000F0, VIEW = 128;
    private static final int BACK = 0xFF11161B, GRID = 0xFF1D252D;
    private static final int TEAL = 0xFF3CD6C6, AMBER = 0xFFFFB547;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        int width, height, fe, devices, busy;
        long flops;
        float pue;
        byte[] racks = new byte[0];
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW;
    }

    @Override
    public void extractRenderState(LoadWallBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.facing = be.getBlockState().getValue(LoadWallBlock.FACING);
        state.width = be.widthShown();
        state.height = be.heightShown();
        state.flops = be.flopsShown();
        state.fe = be.feShown();
        state.devices = be.devicesShown();
        state.busy = be.busyShown();
        state.pue = be.pueShown();
        state.racks = be.racksShown();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.width == 0) {
            return;
        }
        // from the panel's top left corner as a player facing it sees it, x to the right and y down, in blocks
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        poseStack.translate(-0.5, 0.5, 0.502);
        float w = state.width, h = state.height;
        collector.submitCustomGeometry(poseStack, RenderTypes.textBackground(), (pose, buffer) -> {
            quad(pose, buffer, 0.03f, 0.03f, w - 0.03f, h - 0.03f, 0, BACK);
            float top = 0.34f * Math.min(1, h), bottom = h - 0.08f;
            int racks = state.racks.length;
            for (int r = 0; r < racks; r++) {
                float x0 = 0.1f + (w - 0.2f) * r / racks, x1 = 0.1f + (w - 0.2f) * (r + 1) / racks - 0.02f;
                float load = (state.racks[r] & 0xFF) / 255f;
                quad(pose, buffer, x0, top, x1, bottom, 0.001f, GRID);
                if (load > 0) {
                    quad(pose, buffer, x0, bottom - (bottom - top) * load, x1, bottom, 0.002f, load > 0.9f ? AMBER : TEAL);
                }
            }
        });
        Font font = Minecraft.getInstance().font;
        Component line = Component.translatable("residual.wall.line", ClusterStatus.flops(state.flops), state.busy, state.devices,
                String.format(Locale.ROOT, "%,d", state.fe), String.format(Locale.ROOT, "%.2f", state.pue));
        // as wide as the wall, and no taller than its top third
        float scale = Math.min((w - 0.2f) / font.width(line), 0.26f * Math.min(1, h) / font.lineHeight);
        poseStack.pushPose();
        poseStack.translate(0.1, -0.05, 0.003);
        poseStack.scale(scale, -scale, scale);
        collector.submitText(poseStack, 0, 0, line.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, state.busy > 0 ? TEAL : 0xFF7F8D99, 0, 0);
        poseStack.popPose();
        poseStack.popPose();
    }

    /** A rectangle on the display, in blocks from its top left. */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float lift, int color) {
        buffer.addVertex(pose, x0, -y1, lift).setColor(color).setLight(FULL_BRIGHT);
        buffer.addVertex(pose, x1, -y1, lift).setColor(color).setLight(FULL_BRIGHT);
        buffer.addVertex(pose, x1, -y0, lift).setColor(color).setLight(FULL_BRIGHT);
        buffer.addVertex(pose, x0, -y0, lift).setColor(color).setLight(FULL_BRIGHT);
    }
}
