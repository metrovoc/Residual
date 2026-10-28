package io.github.metrovoc.residual.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Which way a newly placed machine's output should point. */
final class Placement {
    private Placement() {
    }

    /**
     * Placed against another machine's port, a machine snaps into the dataflow: against an output it takes that
     * output as its input, against an input it feeds it. Against a cable it reads from the cable. Anywhere else
     * the output points away from the player. Sneak-using a face afterwards turns the output to that face.
     */
    static Direction facing(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        if (!ctx.replacingClickedOnBlock()) {
            BlockState against = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(clicked.getOpposite()));
            Port port = Port.at(against, clicked);
            if (port.drives() || against.getBlock() instanceof CableBlock) {
                return clicked;
            }
            if (port != Port.NONE) {
                return clicked.getOpposite();
            }
        }
        return ctx.getHorizontalDirection();
    }
}
