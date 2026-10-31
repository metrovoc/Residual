package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A Storage Terminal's place in its network. */
public class TerminalBlockEntity extends BlockEntity implements MenuProvider {
    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TerminalBlockEntity be) {
        if (level.getGameTime() % 20 == Math.floorMod(pos.asLong(), 20)) {
            boolean lit = !StorageNetwork.at((ServerLevel) level, pos).stores().isEmpty();
            if (state.getValue(TerminalBlock.LIT) != lit) {
                level.setBlock(pos, state.setValue(TerminalBlock.LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.residual.storage_terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TerminalMenu(id, inventory, worldPosition);
    }
}
