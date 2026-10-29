package io.github.metrovoc.residual.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Lends a training thread to a Model Core it touches, directly or through other accelerators. Without water, ice or
 * snow beside it (or a cold biome) it runs hot: half a thread, and smoke.
 */
public class AcceleratorBlock extends Block {
    public static final BooleanProperty HOT = BooleanProperty.create("hot");
    public static final MapCodec<AcceleratorBlock> CODEC = simpleCodec(AcceleratorBlock::new);

    public AcceleratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HOT, false));
    }

    @Override
    protected MapCodec<AcceleratorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HOT);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HOT) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
                    0, 0.03, 0);
        }
    }
}
