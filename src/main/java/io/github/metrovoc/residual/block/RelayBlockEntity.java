package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Signs its relay into the network while it is loaded. */
public class RelayBlockEntity extends BlockEntity {
    public RelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELAY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            Relays.relay(level, worldPosition, true);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            Relays.relay(level, worldPosition, false);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide()) {
            Relays.relay(level, worldPosition, false);
        }
    }
}
