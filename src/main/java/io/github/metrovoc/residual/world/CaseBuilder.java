package io.github.metrovoc.residual.world;

import io.github.metrovoc.residual.block.CableBlock;
import io.github.metrovoc.residual.block.CoreBlock;
import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Builds a case's broken machine in front of a Model Core. */
public final class CaseBuilder {
    private final BlockPos core;
    private final Direction forward;
    private final Direction side;
    private final Direction up;

    private CaseBuilder(BlockPos core, Direction forward) {
        this.core = core;
        this.forward = forward;
        this.up = forward.getAxis().isVertical() ? Direction.NORTH : Direction.UP;
        this.side = forward.getAxis().isVertical() ? Direction.EAST : forward.getClockWise();
    }

    private BlockPos at(CaseLayout.Cell c) {
        return core.relative(forward, c.x()).relative(side, c.l()).relative(up, c.y());
    }

    /** Where the layout would overwrite something, or null if the machine fits. */
    @Nullable
    public static BlockPos obstacle(ServerLevel level, BlockPos core, CaseLayout layout) {
        CaseBuilder b = new CaseBuilder(core, level.getBlockState(core).getValue(CoreBlock.FACING));
        for (CaseLayout.Cell c : layout.cells()) {
            BlockPos p = b.at(c);
            if (!level.isLoaded(p) || !level.getBlockState(p).canBeReplaced()) {
                return p;
            }
        }
        return null;
    }

    /** Places the machine; check {@link #obstacle} first. Its machines drop nothing when broken. */
    public static void build(ServerLevel level, BlockPos core, GraphSpec spec, CaseLayout layout) {
        CaseBuilder b = new CaseBuilder(core, level.getBlockState(core).getValue(CoreBlock.FACING));
        for (CaseLayout.Machine m : layout.machines) {
            GraphSpec.Node node = spec.node(m.node());
            BlockPos p = b.at(m.at());
            level.setBlock(p, ModBlocks.OPERATORS.get(node.op).get().defaultBlockState().setValue(OperatorBlock.FACING, b.forward), Block.UPDATE_ALL);
            if (level.getBlockEntity(p) instanceof OperatorBlockEntity be) {
                node.config.asMap().forEach(be::configure);
                be.rename(node.name == null ? "" : node.name);
                be.markProvided();
            }
        }
        for (CaseLayout.Cable c : layout.cables) {
            level.setBlock(b.at(c.at()), ModBlocks.COLORED_CABLES.get(DyeColor.byId(c.color())).get().defaultBlockState(), Block.UPDATE_ALL);
        }
        // arms depend on every neighbor, so settle them once everything stands
        for (CaseLayout.Cable c : layout.cables) {
            BlockPos p = b.at(c.at());
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof CableBlock cable) {
                for (Direction d : Direction.values()) {
                    state = state.setValue(CableBlock.ARMS.get(d), cable.attaches(level.getBlockState(p.relative(d)), d));
                }
                level.setBlock(p, state, Block.UPDATE_CLIENTS);
            }
        }
    }
}
