package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Signs its relay into the network when it first loads, however it came to be there: a ruin's mast, say. */
public class RelayBlockEntity extends BlockEntity {
    public RelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELAY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            Relays.mast(server, worldPosition);
        }
    }
}
