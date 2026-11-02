package io.github.metrovoc.residual.energy;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Makes {@link #RATE} FE a tick while it sees a bright sky, less as the sky darkens toward night or rain, and nothing in
 * the dark. It looks every two seconds, and it costs nothing to run, so a field of them is power for as long as the sun is
 * up, and cells carry it through the night.
 */
public class SolarBlockEntity extends PowerBlockEntity {
    public static final int RATE = 40;
    private static final int CAPACITY = 8_000, OUT = 800, LOOK = 40;
    /** Where the sky counts as dark: vanilla's sky darkening runs from 0 at noon to 11 at night. */
    private static final int DARK = 4;
    private int rate;

    public SolarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR.get(), pos, state, CAPACITY, 0, OUT);
    }

    @Override
    public Role role() {
        return Role.GENERATOR;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    /** What it makes a tick now. */
    public int rate() {
        return rate;
    }

    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), LOOK) == 0) {
            int dark = level.dimensionType().hasSkyLight() && level.canSeeSky(worldPosition.above()) ? level.getSkyDarken() : DARK;
            rate = RATE * Math.max(0, DARK - dark) / DARK;
            BlockState here = getBlockState();
            if (here.getValue(SolarBlock.LIT) != (rate > 0)) {
                level.setBlock(worldPosition, here.setValue(SolarBlock.LIT, rate > 0), Block.UPDATE_CLIENTS);
            }
        }
        int stored = energy().getAmountAsInt();
        if (rate > 0 && stored < CAPACITY) {
            energy().set(Math.min(CAPACITY, stored + rate));
        }
    }
}
