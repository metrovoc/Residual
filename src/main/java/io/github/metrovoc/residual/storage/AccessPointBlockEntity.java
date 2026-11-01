package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * How far a Wireless Terminal can be from its network: {@link #RANGE} blocks of an Access Point on it, {@link #DRIVEN_RANGE}
 * while a Link Driver on the network has power; and with a Relay Card in its slot, tuned to a Model Server whose relays reach
 * the Access Point, anywhere those relays cover.
 */
public class AccessPointBlockEntity extends BlockEntity implements MenuProvider {
    public static final int RANGE = 528, DRIVEN_RANGE = 2_112;
    /** Container data: whether a Link Driver has power for it, and whether its card reaches a Model Server. */
    public static final int DRIVEN = 0, RELAYED = 1, DATA = 2;

    private final SimpleContainer uplink = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            AccessPointBlockEntity.this.setChanged();
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            if (!(level instanceof ServerLevel server)) {
                return 0;
            }
            return switch (i) {
                case DRIVEN -> StorageNetwork.at(server, worldPosition).driverHasPower() ? 1 : 0;
                case RELAYED -> relayed(server) ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public AccessPointBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCESS_POINT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AccessPointBlockEntity be) {
        if (level.getGameTime() % 20 == Math.floorMod(pos.asLong(), 20)) {
            boolean lit = !StorageNetwork.at((ServerLevel) level, pos).stores().isEmpty();
            if (state.getValue(AccessPointBlock.LIT) != lit) {
                level.setBlock(pos, state.setValue(AccessPointBlock.LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Whether its own signal reaches {@code where}, as far as it goes with a Link Driver running or without. */
    boolean inRange(BlockPos where, boolean driven) {
        double range = driven ? DRIVEN_RANGE : RANGE;
        return worldPosition.distSqr(where) <= range * range;
    }

    /** Whether the Model Server its card is tuned to has relays reaching it. */
    private boolean relayed(ServerLevel level) {
        GlobalPos server = uplink.getItem(0).get(ModComponents.LINK.get());
        return server != null && server.dimension().equals(level.dimension()) && Relays.covers(level, server.pos(), worldPosition);
    }

    /** Whether the relays its card leads to cover {@code where}. */
    boolean relayCovers(ServerLevel level, BlockPos where) {
        return relayed(level) && Relays.covers(level, uplink.getItem(0).get(ModComponents.LINK.get()).pos(), where);
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
        return Component.translatable("block.residual.access_point");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AccessPointMenu(id, inventory, worldPosition, uplink, data);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, uplink);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("uplink"), uplink.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("uplink").ifPresent(c -> ContainerHelper.loadAllItems(c, uplink.getItems()));
    }
}
