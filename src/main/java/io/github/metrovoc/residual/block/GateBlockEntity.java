package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Signs its gate into the network when it first loads. Its name and what it is tuned to are kept by the network. */
public class GateBlockEntity extends BlockEntity {
    public GateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELAY_GATE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            Relays.sign(server, worldPosition, Relays.Kind.GATE, 0);
        }
    }
}
