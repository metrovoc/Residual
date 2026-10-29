package io.github.metrovoc.residual.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a block face does in the dataflow. A machine's front is its output, its back the main input and, for
 * two-input machines, every other face the side input. The Model Core sends tokens out of its front and takes
 * logits in through every other face. Every face of a residual stream block is a stream port: it adds what an output
 * puts on it into the stream, and otherwise puts the stream onto it.
 */
public enum Port {
    NONE, OUTPUT, MAIN, SIDE, TOKENS, LOGITS, STREAM;

    /** Whether this port puts a value onto its network (as opposed to reading one). */
    public boolean drives() {
        return this == OUTPUT || this == TOKENS;
    }

    public static Port at(BlockState state, Direction face) {
        if (state.getBlock() instanceof OperatorBlock op) {
            return op.port(state, face);
        }
        if (state.getBlock() instanceof CoreBlock) {
            return state.getValue(CoreBlock.FACING) == face ? TOKENS : LOGITS;
        }
        if (state.getBlock() instanceof StreamBlock) {
            return STREAM;
        }
        return NONE;
    }
}
