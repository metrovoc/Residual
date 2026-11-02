package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.block.AcceleratorBlock;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import java.util.Arrays;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/** Eight bays of Accelerators, and how busy each has been lately, which players see as its light. */
public class RackModuleBlockEntity extends BlockEntity implements MenuProvider {
    public static final int BAYS = 8;
    /** A bay's light on a scale of this many steps, so a small change sends nothing. */
    private static final int STEPS = 15;
    private final SimpleContainer devices = new SimpleContainer(BAYS) {
        @Override
        public void setChanged() {
            super.setChanged();
            devicesChanged();
        }
    };
    private byte[] lights = new byte[BAYS];
    private boolean hot, working;
    private final float[] loads = new float[BAYS];

    public RackModuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RACK_MODULE.get(), pos, state);
    }

    /** The process node of a stack that goes in a bay, or null for anything else. */
    @Nullable
    public static ProcessNode node(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof AcceleratorBlock a ? a.node : null;
    }

    public ItemStack bay(int i) {
        return devices.getItem(i);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RackModuleBlockEntity be) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20) == 0) {
            be.look((ServerLevel) level);
        }
    }

    public byte[] lights() {
        return lights;
    }

    public boolean hotShown() {
        return hot;
    }

    public boolean workingShown() {
        return working;
    }

    /** Lights each bay by how busy its device has been, and shows whether any of them runs hot. */
    private void look(ServerLevel level) {
        Cluster cluster = Cluster.at(level, worldPosition);
        boolean used = cluster.uses(worldPosition);
        cluster.loads(worldPosition, loads);
        byte[] now = new byte[BAYS];
        boolean busy = false;
        for (int i = 0; i < BAYS; i++) {
            // a device in a rack in use shows a dim light even when idle; 0 means dark
            now[i] = bay(i).isEmpty() || !used ? 0 : (byte) (1 + Math.round(loads[i] * STEPS));
            busy |= loads[i] > 0.02f;
        }
        boolean nowHot = busy && cluster.coolingShort(worldPosition);
        if (!Arrays.equals(now, lights) || nowHot != hot || busy != working) {
            lights = now;
            hot = nowHot;
            working = busy;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockState state = getBlockState();
        for (int i = 0; i < BAYS; i++) {
            state = state.setValue(RackModuleBlock.BAYS.get(i), !bay(i).isEmpty());
        }
        if (state != getBlockState()) {
            level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS);
        }
    }

    private void devicesChanged() {
        setChanged();
        if (level instanceof ServerLevel server) {
            Cluster.changed(server);
            look(server);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            Cluster.changed(server);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) {
            Cluster.changed(server);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.residual.rack_module");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RackModuleMenu(id, inventory, worldPosition, devices);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, devices);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("devices"), devices.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("devices").ifPresent(c -> ContainerHelper.loadAllItems(c, devices.getItems()));
        in.getIntArray("lights").filter(b -> b.length == BAYS).ifPresent(b -> {
            for (int i = 0; i < BAYS; i++) {
                lights[i] = (byte) b[i];
            }
        });
        hot = in.getBooleanOr("hot", false);
        working = in.getBooleanOr("working", false);
        if (level instanceof ServerLevel server) {
            // its devices changed by a command
            Cluster.changed(server);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        int[] each = new int[BAYS];
        for (int i = 0; i < BAYS; i++) {
            each[i] = lights[i];
        }
        tag.putIntArray("lights", each);
        tag.putBoolean("hot", hot);
        tag.putBoolean("working", working);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
