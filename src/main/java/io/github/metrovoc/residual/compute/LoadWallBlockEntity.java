package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * What a Load Wall shows. The panel at the top left of a run of panels speaks for all of them: it knows how many there
 * are, reads the cluster each second and draws the whole display; the others are glass for it to draw on.
 */
public class LoadWallBlockEntity extends BlockEntity {
    /** The most panels a display spans each way. */
    private static final int MOST = 16;
    private int width, height;
    private long flops;
    private int fe, devices, busy;
    private float pue = 1;
    private byte[] racks = new byte[0];

    public LoadWallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOAD_WALL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LoadWallBlockEntity be) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20) == 0) {
            be.look((ServerLevel) level, state);
        }
    }

    /** The way along a wall from a panel's left, as a player facing it sees it. */
    public static Direction right(BlockState state) {
        return state.getValue(LoadWallBlock.FACING).getCounterClockWise();
    }

    private boolean panel(Level level, BlockPos p, Direction facing) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof LoadWallBlock && s.getValue(LoadWallBlock.FACING) == facing;
    }

    private void look(ServerLevel level, BlockState state) {
        Direction facing = state.getValue(LoadWallBlock.FACING), right = right(state);
        boolean anchor = !panel(level, worldPosition.relative(right.getOpposite()), facing) && !panel(level, worldPosition.above(), facing);
        int w = 0, h = 0;
        if (anchor) {
            w = 1;
            while (w < MOST && panel(level, worldPosition.relative(right, w), facing)) {
                w++;
            }
            h = 1;
            while (h < MOST && panel(level, worldPosition.below(h), facing)) {
                h++;
            }
        }
        Cluster cluster = Cluster.at(level, worldPosition);
        long nowFlops = anchor ? cluster.flopsNow() : 0;
        int nowFe = anchor ? cluster.feNow() : 0, nowDevices = anchor ? cluster.devices().size() : 0, nowBusy = anchor ? cluster.busy() : 0;
        float nowPue = anchor ? (float) cluster.pue() : 1;
        byte[] nowRacks = anchor ? cluster.rackLoads() : new byte[0];
        if (w != width || h != height || nowFlops != flops || nowFe != fe || nowDevices != devices || nowBusy != busy || nowPue != pue
                || !Arrays.equals(nowRacks, racks)) {
            width = w;
            height = h;
            flops = nowFlops;
            fe = nowFe;
            devices = nowDevices;
            busy = nowBusy;
            pue = nowPue;
            racks = nowRacks;
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public int widthShown() {
        return width;
    }

    public int heightShown() {
        return height;
    }

    public long flopsShown() {
        return flops;
    }

    public int feShown() {
        return fe;
    }

    public int devicesShown() {
        return devices;
    }

    public int busyShown() {
        return busy;
    }

    public float pueShown() {
        return pue;
    }

    public byte[] racksShown() {
        return racks;
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        width = in.getIntOr("width", 0);
        height = in.getIntOr("height", 0);
        flops = in.getLongOr("flops", 0);
        fe = in.getIntOr("fe", 0);
        devices = in.getIntOr("devices", 0);
        busy = in.getIntOr("busy", 0);
        pue = in.getFloatOr("pue", 1);
        racks = in.getString("racks").map(LoadWallBlockEntity::bytes).orElse(new byte[0]);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("width", width);
        tag.putInt("height", height);
        tag.putLong("flops", flops);
        tag.putInt("fe", fe);
        tag.putInt("devices", devices);
        tag.putInt("busy", busy);
        tag.putFloat("pue", pue);
        tag.putString("racks", chars(racks));
        return tag;
    }

    private static String chars(byte[] loads) {
        StringBuilder out = new StringBuilder(loads.length);
        for (byte b : loads) {
            out.append((char) ('0' + (b & 0xFF)));
        }
        return out.toString();
    }

    private static byte[] bytes(String loads) {
        byte[] out = new byte[loads.length()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (loads.charAt(i) - '0');
        }
        return out;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
