package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.CableBlock;
import io.github.metrovoc.residual.block.CoreBlock;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.block.Port;
import io.github.metrovoc.residual.block.StreamBlock;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.view.Heatmap;
import io.github.metrovoc.residual.world.GraphScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A small readout next to the crosshair for the mod's blocks: which machine it is, what it outputs (or what is
 * wrong) and what the face you are looking at does. With a Tensor Probe in hand, cables say what they carry.
 */
public final class Lens {
    private static final int WIDTH = 150;
    private static final double HOLOGRAM_REACH = 12;
    @Nullable
    private static BlockPos cachedCable;
    private static long cachedAt;
    private static List<GraphScanner.Touch> cachedNetwork = List.of();

    private Lens() {
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || mc.level == null || mc.player == null) {
            return;
        }
        List<Component> reading = hologram(mc, delta.getGameTimeDeltaPartialTick(false));
        if (!reading.isEmpty()) {
            draw(g, mc.font, reading);
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        List<Component> lines = new ArrayList<>();
        Port face = Port.at(state, hit.getDirection());
        if (mc.level.getBlockEntity(pos) instanceof OperatorBlockEntity be) {
            Op op = be.op();
            Component title = Component.translatable("block.residual." + op.id);
            lines.add(be.name().isEmpty() ? title.copy().withColor(Ui.TEXT)
                    : Component.literal(be.name()).withColor(Ui.TEXT).append(Component.literal(" · ").withColor(Ui.DIM)).append(title.copy().withColor(Ui.DIM)));
            OperatorBlockEntity.Status status = be.status();
            if (!status.error().isEmpty()) {
                lines.add(Component.literal(status.error()).withColor(Ui.RED));
            } else if (!status.shape().isEmpty()) {
                lines.add(Component.literal("→ " + status.shape()).withColor(Ui.TEAL));
            }
            lines.add(port(face));
        } else if (state.getBlock() instanceof CoreBlock) {
            lines.add(Component.translatable("block.residual.model_core").withColor(Ui.TEXT));
            lines.add(port(face));
        } else if (state.getBlock() instanceof StreamBlock) {
            lines.add(Component.translatable("block.residual.stream").withColor(Ui.TEXT));
            if (StreamBlock.next(mc.level, pos, state) == null) {
                lines.add(Component.translatable("residual.lens.stream_end").withColor(Ui.DIM));
            }
            lines.add(port(face));
        } else if (state.getBlock() instanceof CableBlock cable && mc.player.isHolding(ModItems.PROBE.get())) {
            Component color = cable.color == null ? Component.translatable("residual.lens.uncolored")
                    : Component.translatable("color.minecraft." + cable.color.getSerializedName());
            lines.add(Component.translatable("residual.lens.cable", color).withColor(Ui.TEXT));
            List<GraphScanner.Touch> net = network(mc, pos);
            long drivers = net.stream().filter(t -> t.port().drives()).count();
            long streams = net.stream().filter(t -> t.port() == Port.STREAM).count();
            long readers = net.size() - drivers - (drivers == 0 ? streams : 0);
            if (drivers == 0 && streams == 1) {
                lines.add(Component.translatable("residual.lens.stream").withColor(Ui.TEAL));
            } else if (drivers == 0 && streams > 1) {
                lines.add(Component.translatable("residual.lens.contested", streams).withColor(Ui.RED));
            } else if (drivers == 0) {
                lines.add(Component.translatable("residual.lens.undriven").withColor(Ui.AMBER));
            } else if (drivers > 1) {
                lines.add(Component.translatable("residual.lens.contested", drivers).withColor(Ui.RED));
            } else {
                net.stream().filter(t -> t.port().drives()).findFirst().ifPresent(t -> lines.add(
                        Component.translatable("residual.lens.carries", source(mc, t)).withColor(Ui.TEAL)));
            }
            lines.add(Component.translatable(readers == 0 ? "residual.lens.unread" : readers == 1 ? "residual.lens.reader" : "residual.lens.readers",
                    readers).withColor(Ui.DIM));
        } else {
            return;
        }
        draw(g, mc.font, lines);
    }

    /** The cell of a hologram under the crosshair, if one is nearer than the block it points at. */
    private static List<Component> hologram(Minecraft mc, float partial) {
        Vec3 eye = mc.player.getEyePosition(partial), look = mc.player.getViewVector(partial);
        double reach = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS
                ? mc.hitResult.getLocation().distanceTo(eye) : HOLOGRAM_REACH;
        Hologram.Shown best = null;
        Hologram.Hit at = null;
        for (Map.Entry<BlockPos, Hologram.Shown> e : Hologram.shown().entrySet()) {
            Hologram.Shown s = e.getValue();
            Hologram.Hit h = Hologram.hit(Hologram.frame(e.getKey(), s.map(), eye), s.map(), eye, look);
            if (h != null && h.distance() < Math.min(reach, HOLOGRAM_REACH) && (at == null || h.distance() < at.distance())) {
                best = s;
                at = h;
            }
        }
        if (best == null) {
            return List.of();
        }
        Heatmap map = best.map();
        float value = map.value(at.row(), at.col()) * map.scale();
        boolean averaged = map.firstRow(at.row()) != map.lastRow(at.row()) || map.firstCol(at.col()) != map.lastCol(at.col());
        List<Component> lines = new ArrayList<>();
        lines.add(best.title().copy().withColor(Ui.TEXT));
        if (!best.shape().isEmpty()) {
            lines.add(Component.translatable("residual.lens.output", best.shape()).withColor(Ui.TEAL));
        }
        lines.add(Component.translatable("residual.lens.cell", span(map.firstRow(at.row()), map.lastRow(at.row())),
                span(map.firstCol(at.col()), map.lastCol(at.col()))).withColor(Ui.DIM));
        lines.add(Component.translatable(averaged ? "residual.lens.mean" : "residual.lens.value",
                String.format(Locale.ROOT, "%+.3g", value)).withColor(value >= 0 ? Ui.AMBER : Ui.BLUE));
        return lines;
    }

    private static String span(int first, int last) {
        return first == last ? Integer.toString(first) : first + "–" + last;
    }

    private static Component port(Port port) {
        int color = switch (port) {
            case OUTPUT, TOKENS, STREAM -> Ui.TEAL;
            case MAIN, SIDE, LOGITS -> Ui.AMBER;
            case NONE -> Ui.DIM;
        };
        return Component.literal("▸ ").append(Component.translatable("residual.port." + port.name().toLowerCase(Locale.ROOT))).withColor(color);
    }

    private static Component source(Minecraft mc, GraphScanner.Touch t) {
        if (t.port() == Port.TOKENS) {
            return Component.translatable("residual.lens.tokens");
        }
        if (mc.level.getBlockEntity(t.pos()) instanceof OperatorBlockEntity be) {
            return be.name().isEmpty() ? Component.translatable("block.residual." + be.op().id) : Component.literal(be.name());
        }
        return Component.literal("?");
    }

    /** Flooding a network is cheap but not free: redo it at most twice a second per cable. */
    private static List<GraphScanner.Touch> network(Minecraft mc, BlockPos pos) {
        long now = mc.level.getGameTime();
        if (!pos.equals(cachedCable) || now < cachedAt || now - cachedAt > 10) {
            cachedCable = pos.immutable();
            cachedAt = now;
            cachedNetwork = GraphScanner.network(mc.level, pos);
        }
        return cachedNetwork;
    }

    private static void draw(GuiGraphicsExtractor g, Font font, List<Component> lines) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        int w = 0;
        for (Component line : lines) {
            for (FormattedCharSequence part : font.split(line, WIDTH)) {
                wrapped.add(part);
                w = Math.max(w, font.width(part));
            }
        }
        int x = g.guiWidth() / 2 + 12;
        int y = g.guiHeight() / 2 - 6;
        int h = wrapped.size() * 10 + 4;
        g.fill(x - 3, y - 3, x + w + 3, y + h - 1, 0xC00E1216);
        g.fill(x - 3, y - 3, x - 2, y + h - 1, 0xFF2A9D92);
        for (int i = 0; i < wrapped.size(); i++) {
            g.text(font, wrapped.get(i), x + 1, y + i * 10, Ui.TEXT, false);
        }
    }
}
