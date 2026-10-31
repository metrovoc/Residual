package io.github.metrovoc.residual.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * A block that keeps power in Forge Energy: a generator gives it to its network, a cell stores it, a machine runs on
 * it. Other mods' cables and machines reach it through {@code Capabilities.Energy.BLOCK}.
 */
public abstract class PowerBlockEntity extends BlockEntity {
    public enum Role { GENERATOR, CELL, MACHINE }

    private final SimpleEnergyHandler energy;

    protected PowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity, int maxInsert, int maxExtract) {
        super(type, pos, state);
        energy = new SimpleEnergyHandler(capacity, maxInsert, maxExtract) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                setChanged();
                energyChanged(previousAmount);
            }
        };
    }

    public abstract Role role();

    public SimpleEnergyHandler energy() {
        return energy;
    }

    /** Called once a change to its stored power is final. */
    protected void energyChanged(int previous) {
    }

    /** Its network moves this tick's power, then it does its own work. */
    protected void tick(ServerLevel level) {
        PowerNetwork.at(level, worldPosition).tick();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        energy.serialize(out.child("energy"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("energy").ifPresent(energy::deserialize);
    }
}
