package io.github.metrovoc.residual.energy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A Turbine stacks on a Boiler and turns its steam into power; it spins while the Boiler burns, and the top one lets the steam go. */
public class TurbineBlock extends Block implements PowerNode {
    public static final BooleanProperty SPINNING = BooleanProperty.create("spinning");
    public static final MapCodec<TurbineBlock> CODEC = simpleCodec(TurbineBlock::new);

    public TurbineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SPINNING, false));
    }

    @Override
    protected MapCodec<TurbineBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPINNING);
    }

    @Override
    public boolean joins(BlockState state, Direction face) {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(SPINNING) && !(level.getBlockState(pos.above()).getBlock() instanceof TurbineBlock)) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.05,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.08, 0);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            PowerNetwork.changed(server);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        PowerNetwork.changed(level);
    }
}
