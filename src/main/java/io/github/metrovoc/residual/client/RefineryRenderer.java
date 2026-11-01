package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.metrovoc.residual.energy.PowerDeviceBlock;
import io.github.metrovoc.residual.refine.Domain;
import io.github.metrovoc.residual.refine.RefineryBlockEntity;
import io.github.metrovoc.residual.refine.Stage;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * What a Refinery shows in the world: the stream of text it sends on, lines of the writing itself crossing the air from one machine
 * to the next, as many lines as there is text left, each in the colour of its domain; above each machine, what it did to held-out
 * loss once that has been measured; and in a tank, the tokens it holds, risen as high as it is full.
 */
public class RefineryRenderer implements BlockEntityRenderer<RefineryBlockEntity, RefineryRenderer.State> {
    private static final int FULL_BRIGHT = 0xF000F0;
    /** The domains' colours, in their order. */
    private static final int[] COLORS = {0xFF3CD6C6, 0xFFB79CFF, 0xFF84E08A, 0xFFFFB547, 0xFF6FB4FF, 0xFFFF8F70, 0xFFE4EBF1};
    /** A letter's pixel in blocks, how far apart lines run, and how fast they do, in blocks a second. */
    private static final float SCALE = 1 / 72f, LINE = 0.17f, SPEED = 2.4f;
    private static final float TANK_FROM = 1 / 16f, TANK_TO = 15 / 16f;
    private static final int MOST_FALLING = 12;

    public static class State extends BlockEntityRenderState {
        Stage stage = Stage.INTAKE;
        boolean flowing;
        int lanes;
        float[] shares = new float[0];
        String[] samples = new String[0];
        @Nullable
        BlockPos next;
        Direction facing = Direction.NORTH;
        String dropped = "";
        float kept = 1;
        float delta = Float.NaN;
        boolean within;
        float fill;
        float seconds;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RefineryBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.stage = be.stage();
        state.flowing = be.flowing();
        state.lanes = be.lanes();
        state.shares = be.shares();
        state.samples = be.samples();
        state.next = be.next();
        state.facing = be.getBlockState().getValue(PowerDeviceBlock.FACING);
        state.dropped = be.dropped();
        state.kept = be.kept();
        state.delta = be.delta();
        state.within = be.withinNoise();
        state.fill = be.fill();
        state.seconds = (be.getLevel() == null ? 0 : be.getLevel().getGameTime() + partialTicks) / 20f;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Font font = Minecraft.getInstance().font;
        if (state.flowing && state.next != null) {
            stream(state, poseStack, collector, camera, font);
            falling(state, poseStack, collector, camera, font);
        }
        if (!Float.isNaN(state.delta)) {
            label(state, poseStack, collector, camera, font);
        }
        if (state.stage == Stage.TANK && state.fill > 0) {
            tank(state, poseStack, collector, camera, font);
        }
    }

    /** Which domain each line carries: as many of each as its share of what is drawn, the largest shares first. */
    private static int[] lines(float[] shares, int lanes) {
        int[] out = new int[lanes];
        float[] owed = new float[shares.length];
        for (int k = 0; k < lanes; k++) {
            int best = -1;
            for (int d = 0; d < shares.length; d++) {
                owed[d] += shares[d];
                if (shares[d] > 0 && (best < 0 || owed[d] > owed[best])) {
                    best = d;
                }
            }
            out[k] = Math.max(best, 0);
            if (best >= 0) {
                owed[best] -= 1;
            }
        }
        return out;
    }

    private static void stream(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, Font font) {
        Vec3 pos = Vec3.atLowerCornerOf(s.blockPos);
        Direction d = s.facing;
        double length = Vec3.atLowerCornerOf(s.next).distanceTo(pos);
        Vec3 flow = new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
        Vec3 side = new Vec3(-flow.z, 0, flow.x);
        Vec3 middle = new Vec3(0.5, 0.5, 0.5).add(flow.scale(length / 2));
        Vec3 toCamera = camera.pos.subtract(pos.add(middle));
        Vec3 normal = toCamera.subtract(flow.scale(toCamera.dot(flow)));
        normal = normal.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 across = normal.cross(flow);
        // upright from either side: the letters run the other way along the stream from the other side of it
        boolean flip = across.y < 0;
        Vec3 x = flip ? flow.scale(-1) : flow, y = flip ? across.scale(-1) : across;
        float widthPx = (float) (length / SCALE);
        int[] domains = lines(s.shares, s.lanes);
        int rows = Math.max(1, (int) Math.ceil(Math.sqrt(s.lanes)));
        for (int k = 0; k < s.lanes; k++) {
            String sample = s.samples[domains[k]];
            if (sample.isEmpty()) {
                continue;
            }
            int row = k / rows, col = k % rows;
            float a = (col - (rows - 1) / 2f) * LINE, b = (row - (rows - 1) / 2f) * LINE;
            Vec3 center = middle.add(side.scale(a)).add(new Vec3(0, b, 0));
            int w = Math.max(1, font.width(sample + " "));
            float off = (s.seconds * SPEED / SCALE + k * 37) % w;
            // the writing, its first letter already gone into the machine behind and its last not yet out of the one ahead
            String shown = fit(font, sample + " ", (int) widthPx + w);
            float at = flip ? -widthPx / 2 - off : -widthPx / 2 - w + off;
            int skip = skip(font, shown, -widthPx / 2 - at);
            String clipped = font.plainSubstrByWidth(shown.substring(skip), (int) widthPx);
            float skipped = font.width(shown.substring(0, skip));
            poseStack.pushPose();
            poseStack.translate(center.x, center.y, center.z);
            Matrix4f m = poseStack.last().pose();
            m.mul(new Matrix4f((float) x.x, (float) x.y, (float) x.z, 0, (float) y.x, (float) y.y, (float) y.z, 0, (float) normal.x, (float) normal.y, (float) normal.z, 0, 0, 0, 0, 1));
            poseStack.scale(SCALE, -SCALE, SCALE);
            collector.submitText(poseStack, at + skipped, -4, Component.literal(clipped).getVisualOrderText(), false, Font.DisplayMode.NORMAL, FULL_BRIGHT,
                    COLORS[domains[k] % COLORS.length], 0, 0);
            poseStack.popPose();
        }
    }

    /** {@code text} over and over, at least {@code width} pixels of it. */
    private static String fit(Font font, String text, int width) {
        int w = Math.max(1, font.width(text));
        return text.repeat(width / w + 2);
    }

    /** How many characters lie before {@code from} pixels. */
    private static int skip(Font font, String text, float from) {
        return from <= 0 ? 0 : font.plainSubstrByWidth(text, (int) Math.ceil(from)).length();
    }

    /** What it took out falls off its sides in dim letters, the more of them the more it took out. */
    private static void falling(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, Font font) {
        int count = s.dropped.isEmpty() ? 0 : Math.min(MOST_FALLING, Math.round((1 - s.kept) * 2 * MOST_FALLING));
        double angle = Math.atan2(camera.pos.x - s.blockPos.getX() - 0.5, camera.pos.z - s.blockPos.getZ() - 0.5);
        for (int i = 0; i < count; i++) {
            float fall = (s.seconds * 0.5f + i * 0.61f) % 1f;
            int letter = Math.floorMod((int) (s.seconds * 0.5f + i * 0.61f) * 7 + i * 13, s.dropped.length());
            String text = s.dropped.substring(letter, letter + 1);
            if (text.isBlank()) {
                continue;
            }
            poseStack.pushPose();
            double round = i * 2.4;
            poseStack.translate(0.5 + 0.62 * Math.sin(round), 1.1 - 1.6 * fall, 0.5 + 0.62 * Math.cos(round));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotation((float) angle));
            poseStack.scale(SCALE, -SCALE, SCALE);
            int alpha = Math.round(220 * (1 - fall));
            collector.submitText(poseStack, -3, -4, Component.literal(text).getVisualOrderText(), false, Font.DisplayMode.NORMAL, FULL_BRIGHT, alpha << 24 | 0xFF8F70, 0, 0);
            poseStack.popPose();
        }
    }

    /** What it did to held-out loss, turned to face the viewer: green for better, amber for worse, grey for nothing to tell from chance. */
    private static void label(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, Font font) {
        String text = String.format(Locale.ROOT, "%+.3f", s.delta);
        int color = s.within ? 0xFFB4BEC8 : s.delta < 0 ? 0xFF84E08A : 0xFFFFB547;
        Vec3 here = Vec3.atCenterOf(s.blockPos).add(0, 0.95, 0);
        double angle = Math.atan2(camera.pos.x - here.x, camera.pos.z - here.z);
        poseStack.pushPose();
        poseStack.translate(0.5, 1.6, 0.5);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotation((float) angle));
        poseStack.scale(1 / 36f, -1 / 36f, 1 / 36f);
        collector.submitText(poseStack, -font.width(text) / 2f, 0, Component.literal(text).getVisualOrderText(), false, Font.DisplayMode.NORMAL, FULL_BRIGHT, color, 0x60000000, 0);
        poseStack.popPose();
    }

    /** A column of light as high as the tank is full, and letters rising through it. */
    private static void tank(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, Font font) {
        float top = TANK_FROM + (TANK_TO - TANK_FROM) * Math.min(1f, s.fill);
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) ->
                Waves.box(pose, buffer, new AABB(2 / 16.0, TANK_FROM, 2 / 16.0, 14 / 16.0, top, 14 / 16.0), 0x703CD6C6));
        if (top < 0.2f) {
            return;
        }
        double angle = Math.atan2(camera.pos.x - s.blockPos.getX() - 0.5, camera.pos.z - s.blockPos.getZ() - 0.5);
        String letters = "etaoinshrdlu";
        int count = (int) (2 + 5 * s.fill);
        for (int i = 0; i < count; i++) {
            float rise = (s.seconds * 0.15f + i * 0.37f) % 1f;
            float y = TANK_FROM + (top - TANK_FROM) * rise;
            int hash = Math.floorMod((int) (s.seconds * 0.15f + i * 0.37f) * 31 + i * 17, letters.length());
            poseStack.pushPose();
            poseStack.translate(0.3 + 0.4 * ((i * 7 % 5) / 4.0), y, 0.3 + 0.4 * ((i * 3 % 5) / 4.0));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotation((float) angle));
            poseStack.scale(1 / 96f, -1 / 96f, 1 / 96f);
            collector.submitText(poseStack, -3, -4, Component.literal(letters.substring(hash, hash + 1)).getVisualOrderText(), false, Font.DisplayMode.NORMAL, FULL_BRIGHT, 0xFFE4EBF1, 0, 0);
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public AABB getRenderBoundingBox(RefineryBlockEntity be) {
        AABB box = new AABB(be.getBlockPos()).expandTowards(0, 1, 0);
        return be.next() == null ? box : box.minmax(new AABB(be.next())).inflate(0.5);
    }
}
