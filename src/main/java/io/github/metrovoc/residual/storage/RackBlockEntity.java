package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.item.StorageDriveItem;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Eight bays of Storage Drives, and the light over each, which shows players how full its drive is. */
public class RackBlockEntity extends BlockEntity implements MenuProvider {
    public static final int BAYS = 8;
    /** A bay's light: no drive, an empty one, room to spare, three quarters full, full. */
    public static final int NONE = 0, EMPTY = 1, ROOM = 2, FILLING = 3, FULL = 4;

    private final SimpleContainer drives = new SimpleContainer(BAYS) {
        @Override
        public void setChanged() {
            super.setChanged();
            RackBlockEntity.this.drivesChanged();
        }
    };
    private int[] lights = new int[BAYS];

    public RackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RACK.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RackBlockEntity be) {
        if (level.getGameTime() % 20 == Math.floorMod(pos.asLong(), 20)) {
            be.look((ServerLevel) level);
        }
    }

    public int[] lights() {
        return lights;
    }

    /** The drives in its bays, as stores for the network. */
    void stores(List<Store> out) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        StorageData data = StorageData.of(server.getServer());
        for (int i = 0; i < BAYS; i++) {
            ItemStack s = drives.getItem(i);
            if (s.getItem() instanceof StorageDriveItem d) {
                out.add(Store.of(data.drive(StorageDriveItem.id(s), d.capacity), worldPosition));
            }
        }
    }

    /** Adds how many items its drives hold, and could hold, to {@code used}. */
    void usage(long[] used) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        StorageData data = StorageData.of(server.getServer());
        for (int i = 0; i < BAYS; i++) {
            ItemStack s = drives.getItem(i);
            if (s.getItem() instanceof StorageDriveItem d) {
                used[0] += data.drive(StorageDriveItem.id(s), d.capacity).stored();
                used[1] += d.capacity;
            }
        }
    }

    /** Notes on each drive how full it is, and lights the bays to match. */
    private void look(ServerLevel level) {
        StorageData data = StorageData.of(level.getServer());
        int[] now = new int[BAYS];
        for (int i = 0; i < BAYS; i++) {
            ItemStack s = drives.getItem(i);
            if (!(s.getItem() instanceof StorageDriveItem d)) {
                continue;
            }
            Drive drive = data.drive(StorageDriveItem.id(s), d.capacity);
            StorageDriveItem.Label label = new StorageDriveItem.Label(StorageDriveItem.id(s), drive.stored(), drive.kinds());
            if (!label.equals(s.get(ModComponents.STORAGE_DRIVE.get()))) {
                s.set(ModComponents.STORAGE_DRIVE.get(), label);
                setChanged();
            }
            now[i] = drive.stored() == 0 ? EMPTY : drive.stored() >= d.capacity ? FULL : drive.stored() * 4 >= d.capacity * 3 ? FILLING : ROOM;
        }
        if (!Arrays.equals(now, lights)) {
            lights = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        BlockState state = getBlockState();
        for (int i = 0; i < BAYS; i++) {
            state = state.setValue(RackBlock.BAYS.get(i), now[i] != NONE);
        }
        if (state != getBlockState()) {
            level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS);
        }
    }

    private void drivesChanged() {
        setChanged();
        if (level instanceof ServerLevel server) {
            look(server);
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
        return Component.translatable("block.residual.drive_rack");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RackMenu(id, inventory, worldPosition, drives);
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
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("drives").ifPresent(c -> ContainerHelper.loadAllItems(c, drives.getItems()));
        in.getIntArray("lights").filter(b -> b.length == BAYS).ifPresent(b -> lights = b);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("lights", lights);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
