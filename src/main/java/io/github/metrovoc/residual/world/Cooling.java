package io.github.metrovoc.residual.world;

import io.github.metrovoc.residual.compute.Cluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compute runs hot. An Accelerator touching water, ice or snow, or standing in a cold biome, or any device along a closed
 * cooling line, lends all it has; one with nothing to cool it lends half and smokes while it works.
 */
public final class Cooling {
    /** Threads a Core trains with, or what a block that runs no model counts as threads, and how many devices run hot. */
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

    public static Compute compute(Level level, BlockPos host, int base, int max) {
        return Cluster.at((ServerLevel) level, host).compute(base, max);
    }

    /** Lights up (or cools down) the devices of something that runs them flat out while it works. */
    public static void working(Level level, BlockPos host, boolean on) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Cluster cluster = Cluster.at(server, host);
        cluster.lights(on);
        if (on) {
            cluster.flatOut();
        }
    }

    /** Pays for this active tick at the devices' rated draw; whether every metered device was funded. */
    public static boolean payRated(Level level, BlockPos host) {
        return level instanceof ServerLevel server && Cluster.at(server, host).payRated(host);
    }

    /** Lights up (or cools down) the devices of a Core, which pays for what its run really does. */
    public static void lights(Level level, BlockPos host, boolean on) {
        if (level instanceof ServerLevel server) {
            Cluster.at(server, host).lights(on);
        }
    }
}
