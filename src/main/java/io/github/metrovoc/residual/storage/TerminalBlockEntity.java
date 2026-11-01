package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** A Storage Terminal's place in its network. */
public class TerminalBlockEntity extends BlockEntity implements MenuProvider, TerminalHost {
    private final List<ItemResource> pins = new ArrayList<>();
    private final SimpleContainer searchModel = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            TerminalBlockEntity.this.setChanged();
        }
    };

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
    public StorageNetwork network() {
        return StorageNetwork.at((ServerLevel) level, worldPosition);
    }

    @Override
    public Container searchModel() {
        return searchModel;
    }

    @Override
    public boolean reaches(Player player) {
        return !isRemoved() && player.distanceToSqr(worldPosition.getCenter()) < 64;
    }

    @Override
    public List<ItemResource> pins() {
        return pins;
    }

    @Override
    public void pin(ItemResource kind) {
        if (!pins.remove(kind) && pins.size() < TerminalHost.MOST_PINS) {
            pins.add(kind);
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("pins", ItemResource.CODEC.listOf(), pins);
        ContainerHelper.saveAllItems(out.child("search_model"), searchModel.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        pins.clear();
        in.read("pins", ItemResource.CODEC.listOf()).ifPresent(pins::addAll);
        in.child("search_model").ifPresent(c -> ContainerHelper.loadAllItems(c, searchModel.getItems()));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, searchModel);
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
        return new TerminalMenu(id, inventory, worldPosition, this);
    }
}
