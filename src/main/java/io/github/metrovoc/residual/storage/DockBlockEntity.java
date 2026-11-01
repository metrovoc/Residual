package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.item.StorageDriveItem;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Moves everything between the drives in its bays and its network, {@link #RATE} items or a ninth of a bucket's worth
 * more a tick: drive to drive in a rack beside it at 40,960 items a second. It takes the drives on the left one at a
 * time and sets each on the right when it is done: emptied, or filled as far as it will go or the network has anything to
 * give.
 */
public class DockBlockEntity extends BlockEntity implements MenuProvider {
    public static final int BAYS = 6, SLOTS = 2 * BAYS;
    /** Items it moves a tick, and how many millibuckets count as one of them. */
    public static final int RATE = 2_048, FLUID_UNIT = 125;
    /** Container data: whether it fills rather than empties, and what it moved in the last second. */
    public static final int FILLING = 0, MOVED = 1, MOVED_FLUID = 2, DATA = 3;

    private final SimpleContainer drives = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            DockBlockEntity.this.setChanged();
        }
    };
    private boolean filling;
    private int movedThisSecond, movedLastSecond;
    private long fluidThisSecond;
    private int fluidLastSecond;
    private int sinceFlow;
    private BlockPos flowTo;
    private ItemResource flowItem = ItemResource.EMPTY;
    private FluidResource flowFluid = FluidResource.EMPTY;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case FILLING -> filling ? 1 : 0;
                case MOVED -> movedLastSecond;
                case MOVED_FLUID -> fluidLastSecond;
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

    public DockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DOCK.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DockBlockEntity be) {
        be.tick((ServerLevel) level, state);
    }

    private void tick(ServerLevel level, BlockState state) {
        if (level.getGameTime() % 20 == 0) {
            movedLastSecond = movedThisSecond;
            fluidLastSecond = (int) (fluidThisSecond / Drive.BUCKET);
            movedThisSecond = 0;
            fluidThisSecond = 0;
            boolean working = movedLastSecond > 0 || fluidLastSecond > 0;
            if (state.getValue(DockBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(DockBlock.WORKING, working), Block.UPDATE_CLIENTS);
            }
        }
        int bay = next();
        if (bay < 0) {
            return;
        }
        ItemStack stack = drives.getItem(bay);
        StorageDriveItem item = (StorageDriveItem) stack.getItem();
        Drive drive = StorageData.of(level.getServer()).drive(StorageDriveItem.id(stack), item.capacity);
        StorageNetwork net = StorageNetwork.at(level, worldPosition);
        int allowed = net.allowance(worldPosition, RATE);
        int moved = filling ? fill(drive, net, allowed) : empty(drive, net, allowed);
        net.carry(worldPosition, moved);
        movedThisSecond += moved;
        sinceFlow += moved;
        if (moved > 0) {
            flowTo = net.lastStore;
            flowItem = net.lastItem;
            flowFluid = net.lastFluid;
        }
        if (sinceFlow > 0 && level.getGameTime() % Flows.EVERY == Math.floorMod(worldPosition.asLong(), Flows.EVERY)) {
            Flows.send(level, worldPosition, flowTo, !filling, sinceFlow, flowItem, flowFluid, allowed < RATE);
            sinceFlow = 0;
        }
        // an empty drive goes out; a full one too, and one the network has nothing more for, but what has no room waits
        boolean done = filling ? moved == 0 && allowed > 0 : drive.stored() == 0;
        if (done) {
            StorageDriveItem.note(stack, drive);
            finish(bay);
        }
    }

    /** The first bay on the left holding a drive that has somewhere to go when it is done, or -1. */
    private int next() {
        for (int bay = 0; bay < BAYS; bay++) {
            if (drives.getItem(bay).getItem() instanceof StorageDriveItem) {
                return bay;
            }
        }
        return -1;
    }

    private void finish(int bay) {
        for (int out = BAYS; out < SLOTS; out++) {
            if (drives.getItem(out).isEmpty()) {
                drives.setItem(out, drives.removeItemNoUpdate(bay));
                drives.setChanged();
                return;
            }
        }
    }

    /** Moves up to {@code budget} items, and fluids at their rate, from the drive into the network. */
    private int empty(Drive drive, StorageNetwork net, int budget) {
        int moved = 0;
        List<ItemResource> items = new ArrayList<>();
        drive.forEach((kind, n) -> items.add(kind));
        for (ItemResource kind : items) {
            if (moved >= budget) {
                return moved;
            }
            long n = net.insert(kind, drive.extract(kind, budget - moved, true), true);
            moved += (int) net.insert(kind, drive.extract(kind, n, false), false);
        }
        List<FluidResource> fluids = new ArrayList<>();
        drive.forEachFluid((kind, n) -> fluids.add(kind));
        for (FluidResource kind : fluids) {
            long left = (budget - moved) * (long) FLUID_UNIT;
            if (left <= 0) {
                return moved;
            }
            long n = net.insert(kind, drive.extract(kind, left, true), true);
            long done = net.insert(kind, drive.extract(kind, n, false), false);
            fluidThisSecond += done;
            moved += (int) Math.ceil(done / (double) FLUID_UNIT);
        }
        return moved;
    }

    /** Moves up to {@code budget} items, and fluids at their rate, from the network into the drive. */
    private int fill(Drive drive, StorageNetwork net, int budget) {
        int moved = 0;
        for (ItemResource kind : net.contents().keySet()) {
            if (moved >= budget) {
                return moved;
            }
            long n = net.extract(kind, drive.insert(kind, budget - moved, true), true);
            moved += (int) drive.insert(kind, net.extract(kind, n, false), false);
        }
        for (FluidResource kind : net.fluids().keySet()) {
            long left = (budget - moved) * (long) FLUID_UNIT;
            if (left <= 0) {
                return moved;
            }
            long n = net.extract(kind, drive.insert(kind, left, true), true);
            long done = drive.insert(kind, net.extract(kind, n, false), false);
            fluidThisSecond += done;
            moved += (int) Math.ceil(done / (double) FLUID_UNIT);
        }
        return moved;
    }

    public void setFilling(boolean filling) {
        this.filling = filling;
        setChanged();
    }

    /** Items it moved in the last second. */
    public int moved() {
        return movedLastSecond;
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
        return Component.translatable("block.residual.drive_dock");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DockMenu(id, inventory, worldPosition, drives, data);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, drives);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("drives"), drives.getItems());
        out.putBoolean("filling", filling);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("drives").ifPresent(c -> ContainerHelper.loadAllItems(c, drives.getItems()));
        filling = in.getBooleanOr("filling", false);
    }
}
