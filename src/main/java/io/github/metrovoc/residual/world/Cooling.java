package io.github.metrovoc.residual.world;

import io.github.metrovoc.residual.block.AcceleratorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Accelerators run hot. One touching water, ice or snow, or standing in a cold biome, lends a full training thread;
 * one with nothing to cool it lends half a thread and smokes while it works.
 */
public final class Cooling {
    /** Threads a Core trains with, and how many of its accelerators are running hot. */
    public record Compute(int threads, int hot) {
    }

    private Cooling() {
    }

    public static boolean cooled(Level level, BlockPos pos) {
        if (level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel())) {
            return true;
        }
        for (Direction d : Direction.values()) {
            if (coolant(level.getBlockState(pos.relative(d)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean coolant(BlockState s) {
        return s.getFluidState().is(FluidTags.WATER) || s.is(BlockTags.ICE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW);
    }

    public static Compute compute(Level level, BlockPos core, int base, int max) {
        int cooled = 0, hot = 0;
        for (BlockPos p : GraphScanner.accelerators(level, core)) {
            if (cooled(level, p)) {
                cooled++;
            } else {
                hot++;
            }
        }
        return new Compute(Math.max(1, Math.min(max, base + cooled + hot / 2)), hot);
    }

    /** Lights up (or cools down) the accelerators that run hot while the Core trains. */
    public static void working(Level level, BlockPos core, boolean on) {
        for (BlockPos p : GraphScanner.accelerators(level, core)) {
            BlockState s = level.getBlockState(p);
            boolean hot = on && !cooled(level, p);
            if (s.getBlock() instanceof AcceleratorBlock && s.getValue(AcceleratorBlock.HOT) != hot) {
                level.setBlock(p, s.setValue(AcceleratorBlock.HOT, hot), Block.UPDATE_CLIENTS);
            }
        }
    }
}
