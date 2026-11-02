package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

/** What a switch's front shows: the flops its cluster is running a second and how much of it is at work. */
public class SwitchBlockEntity extends BlockEntity {
    private long flopsPerSecond;
    private int devices, working;

    public SwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SWITCH.get(), pos, state);
    }

    public long flopsShown() {
        return flopsPerSecond;
    }

    public int devicesShown() {
        return devices;
    }

    public int workingShown() {
        return working;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SwitchBlockEntity be) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20) == 0) {
            be.look((ServerLevel) level, state);
        }
    }

    private void look(ServerLevel level, BlockState state) {
        Cluster cluster = Cluster.at(level, worldPosition);
        long flops = cluster.flopsNow();
        int all = cluster.devices().size(), busy = cluster.busy();
        if (flops != flopsPerSecond || all != devices || busy != working) {
            flopsPerSecond = flops;
            devices = all;
            working = busy;
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        if (state.getValue(SwitchBlock.LIT) != (busy > 0)) {
            level.setBlock(worldPosition, state.setValue(SwitchBlock.LIT, busy > 0), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        flopsPerSecond = in.getLongOr("flops", 0);
        devices = in.getIntOr("devices", 0);
        working = in.getIntOr("working", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("flops", flopsPerSecond);
        tag.putInt("devices", devices);
        tag.putInt("working", working);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
