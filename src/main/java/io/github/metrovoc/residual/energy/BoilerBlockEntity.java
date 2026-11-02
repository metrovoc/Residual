package io.github.metrovoc.residual.energy;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Boiler with water touching it raises steam for the Turbines stacked on it, up to {@link #MOST}. Each Turbine makes
 * {@link #PER_TURBINE} FE a tick, burning a tick of a furnace's fuel for it, so a coal makes 384,000 FE however many there
 * are, six times what it makes in a Combustion Generator, and the more Turbines the faster it burns.
 */
public class BoilerBlockEntity extends GeneratorBlockEntity {
    public static final int PER_TURBINE = 240, MOST = 4;
    private static final int CAPACITY = 64_000, OUT = 8_000;
    private int turbines;
    private boolean water;
    private int looked;

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOILER.get(), pos, state, CAPACITY, OUT);
    }

    @Override
    protected int rate() {
        return water ? turbines * PER_TURBINE : 0;
    }

    /** A fuel's furnace time, a tick of it to a Turbine. */
    @Override
    protected int burnTicks(Level level, ItemStack fuel) {
        return Math.max(1, GeneratorBlockEntity.burnTime(level, fuel) * 2 / Math.max(1, turbines));
    }

    @Override
    protected void tick(ServerLevel level) {
        if (looked-- <= 0) {
            looked = 20;
            look(level);
        }
        super.tick(level);
    }

    /** Whether it has water, and the Turbines standing on it, which spin while it burns. */
    private void look(ServerLevel level) {
        water = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            water |= level.getFluidState(worldPosition.relative(d)).is(FluidTags.WATER);
        }
        water |= level.getFluidState(worldPosition.below()).is(FluidTags.WATER);
        int count = 0;
        BlockPos.MutableBlockPos at = worldPosition.mutable();
        while (count < MOST && level.getBlockState(at.move(Direction.UP)).getBlock() instanceof TurbineBlock) {
            count++;
        }
        turbines = count;
    }

    @Override
    protected void lit(boolean on) {
        super.lit(on);
        if (level == null) {
            return;
        }
        BlockPos.MutableBlockPos at = worldPosition.mutable();
        for (int i = 0; i < turbines; i++) {
            BlockState s = level.getBlockState(at.move(Direction.UP));
            if (s.getBlock() instanceof TurbineBlock && s.getValue(TurbineBlock.SPINNING) != on) {
                level.setBlock(at, s.setValue(TurbineBlock.SPINNING, on), Block.UPDATE_CLIENTS);
            }
        }
    }
}
