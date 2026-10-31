package io.github.metrovoc.residual.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

/**
 * A box of blocks cut across its length into slices, the way an agent copies, moves and repeats builds: one slice is
 * one letter its model reads. Slice {@code k} is the cross-section {@code k} steps {@code along} from {@code origin},
 * {@code width} blocks {@code across} and {@code height} blocks up.
 */
record Slices(BlockPos origin, Direction along, Direction across, int length, int width, int height) {
    /** The Lab tier's largest copy: as many slices as its model's test copies across, each this wide and tall. */
    static final int MOST = 16;
    /** How far from a build its copy may stand. */
    static final int RANGE = 64;

    /** The box between two corners, cut across its longer level side, starting at {@code first}'s end. */
    static Slices between(BlockPos first, BlockPos second) {
        int dx = second.getX() - first.getX(), dz = second.getZ() - first.getZ();
        Direction x = dx >= 0 ? Direction.EAST : Direction.WEST, z = dz >= 0 ? Direction.SOUTH : Direction.NORTH;
        BlockPos origin = new BlockPos(first.getX(), Math.min(first.getY(), second.getY()), first.getZ());
        int height = Math.abs(second.getY() - first.getY()) + 1;
        return Math.abs(dx) >= Math.abs(dz)
                ? new Slices(origin, x, z, Math.abs(dx) + 1, Math.abs(dz) + 1, height)
                : new Slices(origin, z, x, Math.abs(dz) + 1, Math.abs(dx) + 1, height);
    }

    /**
     * A run to repeat: from above {@code start} towards above {@code end} in a straight level line, its slices reaching
     * across and up to {@code corner} on top of the first one, or one block each without it. Null if the marks don't make
     * one.
     */
    @Nullable
    static Slices run(BlockPos start, BlockPos end, @Nullable BlockPos corner) {
        if (start.getY() != end.getY() || start.getX() != end.getX() && start.getZ() != end.getZ() || start.equals(end)) {
            return null;
        }
        Direction along = Direction.getApproximateNearest(end.getX() - start.getX(), 0, end.getZ() - start.getZ());
        int length = Math.abs(end.getX() - start.getX()) + Math.abs(end.getZ() - start.getZ()) + 1;
        if (corner == null) {
            return new Slices(start.above(), along, along.getClockWise(), length, 1, 1);
        }
        int side = along.getAxis() == Direction.Axis.X ? corner.getZ() - start.getZ() : corner.getX() - start.getX();
        Direction across = along.getAxis() == Direction.Axis.X ? (side >= 0 ? Direction.SOUTH : Direction.NORTH)
                : (side >= 0 ? Direction.EAST : Direction.WEST);
        int height = corner.getY() - start.getY();
        return height < 1 ? null : new Slices(start.above(), along, across, length, Math.abs(side) + 1, height);
    }

    Slices movedTo(BlockPos newOrigin) {
        return new Slices(newOrigin, along, across, length, width, height);
    }

    /** Whether each slice fits what a Lab-tier copy can hold. */
    boolean fits() {
        return width <= MOST && height <= MOST;
    }

    BlockPos at(int slice, int i, int j) {
        return origin.relative(along, slice).relative(across, i).above(j);
    }

    /** The places of slice {@code k}, across then up. */
    List<BlockPos> slice(int k) {
        List<BlockPos> out = new ArrayList<>(width * height);
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                out.add(at(k, i, j));
            }
        }
        return out;
    }

    /** What stands in slice {@code k}, as an agent would lay it again. */
    List<BlockState> read(ServerLevel level, int k) {
        List<BlockState> out = new ArrayList<>(width * height);
        for (BlockPos p : slice(k)) {
            out.add(laid(level.getBlockState(p)));
        }
        return out;
    }

    /** Whether nothing stands in slice {@code k} but what can be built over. */
    boolean empty(ServerLevel level, int k) {
        for (BlockPos p : slice(k)) {
            if (!level.getBlockState(p).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    boolean overlaps(Slices o) {
        return startsBefore(o) && o.startsBefore(this);
    }

    /** Whether this box starts before {@code o} ends, on every axis. */
    private boolean startsBefore(Slices o) {
        BlockPos a = min(), b = o.max();
        return a.getX() <= b.getX() && a.getY() <= b.getY() && a.getZ() <= b.getZ();
    }

    BlockPos min() {
        BlockPos far = at(length - 1, width - 1, height - 1);
        return new BlockPos(Math.min(origin.getX(), far.getX()), Math.min(origin.getY(), far.getY()), Math.min(origin.getZ(), far.getZ()));
    }

    BlockPos max() {
        BlockPos far = at(length - 1, width - 1, height - 1);
        return new BlockPos(Math.max(origin.getX(), far.getX()), Math.max(origin.getY(), far.getY()), Math.max(origin.getZ(), far.getZ()));
    }

    /** Numbers set by hand rather than by items: a repeater's delay, a note block's note, a sign's rotation. */
    private static final Set<Property<?>> SET_BY_HAND = Set.of(BlockStateProperties.DELAY, BlockStateProperties.NOTE,
            BlockStateProperties.ROTATION_16);
    /** Numbers that count the items a block took: candles, pickles, eggs, petals, leaves, snow. */
    private static final List<IntegerProperty> COUNTED = List.of(BlockStateProperties.CANDLES, BlockStateProperties.PICKLES,
            BlockStateProperties.EGGS, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT,
            BlockStateProperties.LAYERS);

    /**
     * {@code s} as an agent lays it from items: every other number as placing leaves it (a young crop, an empty
     * composter, no power), never waterlogged, and nothing for what can be built over (air, water, grass) or what no
     * item places (fire, a piston's head).
     */
    static BlockState laid(BlockState s) {
        if (s.canBeReplaced() || s.getBlock().asItem() == Items.AIR) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState plain = s.getBlock().defaultBlockState();
        for (Property<?> p : s.getProperties()) {
            if (p instanceof IntegerProperty i && !SET_BY_HAND.contains(p) && !COUNTED.contains(i)) {
                s = copy(plain, s, p);
            }
        }
        return s.hasProperty(BlockStateProperties.WATERLOGGED) ? s.setValue(BlockStateProperties.WATERLOGGED, false) : s;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> p) {
        return to.setValue(p, from.getValue(p));
    }

    /**
     * How many of its item laying {@code s} takes: none for a door's top or a bed's head, two for a double slab, one for
     * each candle or layer.
     */
    static int cost(BlockState s) {
        if (s.isAir()) {
            return 0;
        }
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                || s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return 0;
        }
        if (s.hasProperty(BlockStateProperties.SLAB_TYPE) && s.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
            return 2;
        }
        for (IntegerProperty p : COUNTED) {
            if (s.hasProperty(p)) {
                return s.getValue(p);
            }
        }
        return 1;
    }
}
