package io.github.metrovoc.residual.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The chamber an Arena opens into: an empty room walled, floored and roofed all round with anything solid or glass, the Arena
 * set into one wall and facing in. Its floor holds a pad for every three blocks each way, and a fight on each pad.
 *
 * @param min the room's lowest corner, inside its walls
 * @param max its highest corner, inside its walls
 */
public record ArenaRoom(BlockPos min, BlockPos max) {
    public static final int PAD = 3;
    /** The smallest room holds one pad, under a roof two blocks up; the largest holds forty-nine. */
    public static final int LOW = 2, HIGH = 8, WIDEST = 21;

    /** What a look at the room found: the room, or the first block that is wrong. */
    public record Found(@Nullable ArenaRoom room, @Nullable BlockPos fault) {
    }

    private static boolean open(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getFluidState().isEmpty() && s.getCollisionShape(level, p).isEmpty();
    }

    /** How many blocks are open past {@code from} along {@code way}, stopping a block past {@code most}. */
    private static int reach(Level level, BlockPos from, Direction way, int most) {
        int n = 0;
        while (n <= most && open(level, from.relative(way, n + 1))) {
            n++;
        }
        return n;
    }

    /** Looks at the room in front of an Arena at {@code arena} facing {@code facing}. */
    public static Found find(Level level, BlockPos arena, Direction facing) {
        BlockPos front = arena.relative(facing);
        if (!open(level, front)) {
            return new Found(null, front);
        }
        Direction side = facing.getClockWise();
        int left = reach(level, front, side.getOpposite(), WIDEST), right = reach(level, front, side, WIDEST);
        int up = reach(level, front, Direction.UP, HIGH), down = reach(level, front, Direction.DOWN, HIGH);
        int deep = reach(level, front, facing, WIDEST);
        int wide = left + right + 1, high = up + down + 1, depth = deep + 1;
        if (wide > WIDEST || depth > WIDEST || high > HIGH) {
            // it runs on past what a room can be: somewhere along that way a wall is missing
            return new Found(null, wide > WIDEST ? front.relative(side, right) : depth > WIDEST ? front.relative(facing, deep) : front.above(up));
        }
        if (wide < PAD || depth < PAD || high < LOW) {
            return new Found(null, front);
        }
        BlockPos a = front.relative(side.getOpposite(), left).below(down), b = front.relative(side, right).relative(facing, deep).above(up);
        BlockPos min = BlockPos.min(a, b), max = BlockPos.max(a, b);
        for (BlockPos p : BlockPos.betweenClosed(min.offset(-1, -1, -1), max.offset(1, 1, 1))) {
            boolean inside = p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                    && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
            if (inside != open(level, p)) {
                return new Found(null, p.immutable());
            }
        }
        return new Found(new ArenaRoom(min, max), null);
    }

    private int columns() {
        return (max.getX() - min.getX() + 1) / PAD;
    }

    private int rows() {
        return (max.getZ() - min.getZ() + 1) / PAD;
    }

    public int pads() {
        return columns() * rows();
    }

    /** The middle of pad {@code i}'s floor, the pads centred on the room. */
    public Vec3 pad(int i) {
        int w = max.getX() - min.getX() + 1, d = max.getZ() - min.getZ() + 1;
        double x = min.getX() + (w - columns() * PAD) / 2.0 + PAD * (i % columns()) + PAD / 2.0;
        double z = min.getZ() + (d - rows() * PAD) / 2.0 + PAD * (i / columns()) + PAD / 2.0;
        return new Vec3(x, min.getY(), z);
    }

    public AABB box() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }
}
