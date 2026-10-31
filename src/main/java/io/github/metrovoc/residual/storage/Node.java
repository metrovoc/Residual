package io.github.metrovoc.residual.storage;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** A block of a storage network: links, Drive Racks, Storage Terminals and Ports. */
public interface Node {
    /** Whether this block joins the network through {@code face}. */
    boolean joins(BlockState state, Direction face);
}
