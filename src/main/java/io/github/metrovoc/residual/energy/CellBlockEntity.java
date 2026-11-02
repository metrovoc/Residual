package io.github.metrovoc.residual.energy;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A cell of its network's power: it shows how full it is in quarters on its faces. */
public class CellBlockEntity extends PowerBlockEntity {
    private final int capacity;

    public CellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CELL.get(), pos, state, ((CellBlock) state.getBlock()).capacity, ((CellBlock) state.getBlock()).rate,
                ((CellBlock) state.getBlock()).rate);
        capacity = ((CellBlock) state.getBlock()).capacity;
    }

    @Override
    public Role role() {
        return Role.CELL;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CellBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    @Override
    protected void energyChanged(int previous) {
        int charge = (int) Math.ceil(4.0 * energy().getAmountAsInt() / capacity);
        BlockState here = getBlockState();
        if (level != null && here.getValue(CellBlock.CHARGE) != charge) {
            level.setBlock(worldPosition, here.setValue(CellBlock.CHARGE, charge), Block.UPDATE_CLIENTS);
        }
    }
}
