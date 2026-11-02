package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.metrovoc.residual.compute.ClusterStatus;
import io.github.metrovoc.residual.compute.SwitchBlock;
import io.github.metrovoc.residual.compute.SwitchBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A switch's front shows what its cluster is running: the flops a second of the run on it, and the devices at work of all it has. */
public class SwitchRenderer implements BlockEntityRenderer<SwitchBlockEntity, SwitchRenderer.State> {
    private static final int FULL_BRIGHT = 0xF000F0, VIEW = 48;
    private static final float SCALE = 1 / 72f;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        String flops = "", devices = "";
        boolean working;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public int getViewDistance() {
        return VIEW;
    }

    @Override
    public void extractRenderState(SwitchBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.facing = be.getBlockState().getValue(SwitchBlock.FACING);
        state.working = be.workingShown() > 0;
        state.flops = ClusterStatus.flops(be.flopsShown());
        state.devices = be.workingShown() + "/" + be.devicesShown();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        poseStack.translate(0, 0, 0.503);
        poseStack.scale(SCALE, -SCALE, SCALE);
        int color = state.working ? Ui.TEAL : Ui.DIM;
        Component first = Component.literal(state.flops), second = Component.translatable("residual.switch.flops");
        collector.submitText(poseStack, -font.width(first) / 2f, -26, first.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, color, 0, 0);
        collector.submitText(poseStack, -font.width(second) / 2f, -16, second.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, Ui.DIM, 0, 0);
        Component third = Component.literal(state.devices);
        collector.submitText(poseStack, -font.width(third) / 2f, -6, third.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, color, 0, 0);
        poseStack.popPose();
    }
}
