package io.github.metrovoc.residual.energy;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** A block of a power network: cables, and the generators, cells and machines they join. */
public interface PowerNode {
    /** Whether this block joins the network through {@code face}. */
    boolean joins(BlockState state, Direction face);
}
