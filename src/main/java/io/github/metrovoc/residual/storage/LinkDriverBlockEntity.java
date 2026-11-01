package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.energy.PowerBlockEntity;
import io.github.metrovoc.residual.energy.PowerDeviceBlock;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Burns {@link #COST} FE for each tick a link of its network would otherwise be full, so that the links carry
 * {@link StorageNetwork#DRIVEN_RATE} items a tick; it draws nothing while they have room, and holds a little power for an
 * Access Point reaching further.
 */
public class LinkDriverBlockEntity extends PowerBlockEntity {
    /** Half of what a Combustion Generator makes. */
    public static final int COST = 40, CAPACITY = 16_000, IN_RATE = 2_000;

    private int lit;

    public LinkDriverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LINK_DRIVER.get(), pos, state, CAPACITY, IN_RATE, 0);
    }

    @Override
    public Role role() {
        return Role.MACHINE;
    }

    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        StorageNetwork net = StorageNetwork.at(level, worldPosition);
        long now = level.getGameTime();
        if (net.wantsDriving(now) && spend(1)) {
            net.driveUntil(now + 1);
            lit = 20;
        }
        boolean working = lit > 0;
        lit = Math.max(0, lit - 1);
        BlockState here = getBlockState();
        if (here.getValue(PowerDeviceBlock.WORKING) != working) {
            level.setBlock(worldPosition, here.setValue(PowerDeviceBlock.WORKING, working), Block.UPDATE_CLIENTS);
        }
    }

    /** Pays for so many ticks of driving, as far as it can; whether it had enough. */
    boolean spend(int ticks) {
        int stored = energy().getAmountAsInt();
        energy().set(Math.max(0, stored - COST * ticks));
        return stored >= COST * ticks;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            StorageNetwork.changed(server);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) {
            StorageNetwork.changed(server);
        }
    }
}
