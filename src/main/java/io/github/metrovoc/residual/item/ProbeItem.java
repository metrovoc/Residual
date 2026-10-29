package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.block.CableBlock;
import io.github.metrovoc.residual.block.CoreBlock;
import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.block.Port;
import io.github.metrovoc.residual.world.GraphScanner;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Tensor Probe. Held, it shows the live value of the machine you look at. Used on a cable it traces the
 * network: what drives it and what reads it. Sneak-used on a machine it turns the machine's output to that face.
 */
public class ProbeItem extends Item {
    public ProbeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        if (state.getBlock() instanceof OperatorBlock || state.getBlock() instanceof CoreBlock) {
            if (player != null && player.isSecondaryUseActive()) {
                // same as sneak-using the block with an empty hand
                return state.useWithoutItem(level, player, new net.minecraft.world.phys.BlockHitResult(
                        ctx.getClickLocation(), ctx.getClickedFace(), pos, ctx.isInside()));
            }
            return InteractionResult.PASS;
        }
        if (state.getBlock() instanceof CableBlock) {
            if (!level.isClientSide() && player != null) {
                player.sendSystemMessage(describe(level, GraphScanner.network(level, pos)));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    static Component describe(Level level, List<GraphScanner.Touch> touches) {
        List<GraphScanner.Touch> outputs = touches.stream().filter(t -> t.port().drives()).toList();
        // with nothing else driving it, a residual stream feeds the network
        List<GraphScanner.Touch> drivers = outputs.isEmpty() ? touches.stream().filter(t -> t.port() == Port.STREAM).toList() : outputs;
        List<GraphScanner.Touch> readers = touches.stream().filter(t -> !drivers.contains(t)).toList();
        MutableComponent msg = Component.translatable("residual.probe.network").withStyle(ChatFormatting.GOLD);
        if (drivers.isEmpty()) {
            msg.append(Component.translatable("residual.probe.undriven").withStyle(ChatFormatting.RED));
        } else if (drivers.size() > 1) {
            msg.append(Component.translatable("residual.probe.contested", drivers.size()).withStyle(ChatFormatting.RED));
            for (GraphScanner.Touch t : drivers) {
                msg.append(line(level, t));
            }
            return msg;
        } else {
            msg.append(Component.translatable("residual.probe.driver").withStyle(ChatFormatting.GRAY)).append(line(level, drivers.get(0)));
        }
        msg.append(Component.translatable(readers.isEmpty() ? "residual.probe.no_readers" : "residual.probe.readers")
                .withStyle(ChatFormatting.GRAY));
        for (GraphScanner.Touch t : readers) {
            msg.append(line(level, t));
        }
        return msg;
    }

    private static Component line(Level level, GraphScanner.Touch t) {
        BlockState state = level.getBlockState(t.pos());
        MutableComponent name = state.getBlock().getName().copy();
        if (level.getBlockEntity(t.pos()) instanceof OperatorBlockEntity be && !be.name().isEmpty()) {
            name.append(" '" + be.name() + "'");
        }
        String port = t.port().name().toLowerCase(Locale.ROOT);
        return Component.literal("\n  ").append(name.withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" " + t.pos().toShortString() + " ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("residual.port." + port).withStyle(ChatFormatting.AQUA));
    }
}
