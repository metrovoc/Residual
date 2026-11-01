package io.github.metrovoc.residual.agent;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** A filter: what a step works on among items, fluids, blocks and entities. */
public interface Picker {
    /** Exactly what it names: {@code torch} for torches. */
    boolean exactly(ItemStack s);

    /** A kind of what it names, taken only where nothing named exactly is at hand: {@code soul torch} for torches. */
    default boolean loosely(ItemStack s) {
        return false;
    }

    default boolean passes(ItemStack s) {
        return exactly(s) || loosely(s);
    }

    default boolean passes(FluidResource fluid) {
        return false;
    }

    default boolean passes(Entity e) {
        return false;
    }

    /** A block passes as the item that places it, or for what it is named. */
    default boolean passes(BlockState state) {
        return passes(new ItemStack(state.getBlock().asItem()));
    }

    /** It as a program writes it. */
    String thing();
}
