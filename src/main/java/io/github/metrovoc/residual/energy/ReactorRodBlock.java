package io.github.metrovoc.residual.energy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A Fuel Rod: fills a reactor's shell, and glows behind its glass while it runs. */
public class ReactorRodBlock extends Block {
    public static final BooleanProperty BURNING = BooleanProperty.create("burning");
    public static final MapCodec<ReactorRodBlock> CODEC = simpleCodec(ReactorRodBlock::new);

    public ReactorRodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BURNING, false));
    }

    @Override
    protected MapCodec<ReactorRodBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BURNING);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(BURNING) && random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }
}
