package io.github.metrovoc.residual.compute;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** A block of a compute cluster: Accelerators, rack modules, switches, Interconnect and the Hall Console. */
public interface ComputeNode {
    /** Whether the cluster goes on through {@code face}. */
    boolean links(BlockState state, Direction face);
}
