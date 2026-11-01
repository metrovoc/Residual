package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.item.ExosuitItem;
import io.github.metrovoc.residual.item.SuitAbility;
import io.github.metrovoc.residual.item.Vein;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * What a vein would take: while an exosuit that runs vein mining is worn and its wearer sneaks, thin lines through the ground
 * along the outer edges of what would go with the block they look at, drawn as a survey's finds are.
 */
public final class VeinView {
    private static final ContextKey<List<AABB>> KEY = new ContextKey<>(Residual.id("vein"));
    private static final int ARGB = 0xA03CD6C6;
    /** Half a line's width, in blocks, and how many ticks a look at the ground is good for. */
    private static final double HALF_WIDTH = 0.012;
    private static final int KEPT = 10;
    private static BlockPos lookedAt = BlockPos.ZERO;
    private static long lookedWhen = -KEPT;
    private static List<AABB> lines = List.of();

    private VeinView() {
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.isShiftKeyDown() || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        ItemStack suit = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(suit.getItem() instanceof ExosuitItem) || !ExosuitItem.running(suit).contains(SuitAbility.VEIN_MINING)) {
            return;
        }
        long now = mc.level.getGameTime();
        if (!hit.getBlockPos().equals(lookedAt) || now - lookedWhen >= KEPT) {
            lookedAt = hit.getBlockPos();
            lookedWhen = now;
            List<BlockPos> rest = Vein.find(mc.level, lookedAt, mc.level.getBlockState(lookedAt), player, Vein.size(suit));
            lines = rest.isEmpty() ? List.of() : outline(lookedAt, rest);
        }
        if (!lines.isEmpty()) {
            event.getRenderState().setRenderData(KEY, lines);
        }
    }

    /** A thin box along each edge of these blocks but those with another of them all around it. */
    private static List<AABB> outline(BlockPos first, List<BlockPos> rest) {
        Set<BlockPos> vein = new HashSet<>(rest);
        vein.add(first);
        List<AABB> out = new ArrayList<>();
        Direction.Axis[] axes = Direction.Axis.values();
        for (BlockPos p : vein) {
            for (int along = 0; along < 3; along++) {
                Direction.Axis u = axes[(along + 1) % 3], v = axes[(along + 2) % 3];
                for (int a = 0; a < 2; a++) {
                    for (int b = 0; b < 2; b++) {
                        BlockPos beside = p.relative(u, 2 * a - 1), above = p.relative(v, 2 * b - 1);
                        if (vein.contains(beside) && vein.contains(above) && vein.contains(beside.relative(v, 2 * b - 1))) {
                            continue;
                        }
                        double[] low = {p.getX(), p.getY(), p.getZ()}, high = {p.getX() + 1, p.getY() + 1, p.getZ() + 1};
                        double atU = p.get(u) + a, atV = p.get(v) + b;
                        low[u.ordinal()] = atU - HALF_WIDTH;
                        high[u.ordinal()] = atU + HALF_WIDTH;
                        low[v.ordinal()] = atV - HALF_WIDTH;
                        high[v.ordinal()] = atV + HALF_WIDTH;
                        out.add(new AABB(low[0], low[1], low[2], high[0], high[1], high[2]));
                    }
                }
            }
        }
        return out;
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        List<AABB> boxes = event.getLevelRenderState().getRenderData(KEY);
        if (boxes == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.textBackgroundSeeThrough(), (pose, buffer) -> {
            for (AABB box : boxes) {
                SurveyView.box(pose, buffer, box.move(-camera.x, -camera.y, -camera.z), ARGB);
            }
        });
    }
}
