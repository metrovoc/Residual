package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.energy.PowerNetwork;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.storage.StorageNetwork;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A Relay Link's place in its relay, storage and power networks, each found again as it loads and unloads. */
public class RelayLinkBlockEntity extends BlockEntity {
    public RelayLinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELAY_LINK.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            Relays.sign(server, worldPosition, Relays.Kind.LINK, 0);
            StorageNetwork.changed(server);
            PowerNetwork.changed(server);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) {
            StorageNetwork.changed(server);
            PowerNetwork.changed(server);
        }
    }
}
