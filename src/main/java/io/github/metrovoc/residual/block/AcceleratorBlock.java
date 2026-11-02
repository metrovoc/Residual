package io.github.metrovoc.residual.block;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.compute.Cluster;
import io.github.metrovoc.residual.compute.ComputeNode;
import io.github.metrovoc.residual.compute.ProcessNode;
import io.github.metrovoc.residual.energy.PowerNetwork;
import io.github.metrovoc.residual.energy.PowerNode;
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

/**
 * A device printed at a process node. Touching something that takes compute, directly or through other Accelerators, it
 * lends its work and its memory; without water, ice or snow beside it (or a cold biome) it runs hot: half, and smoke. The
 * first few run on what that block can spare, and the rest on the power network they are part of. It also goes in a rack's
 * bay.
 */
public class AcceleratorBlock extends Block implements ComputeNode, PowerNode {
    public static final BooleanProperty HOT = BooleanProperty.create("hot");
    public final ProcessNode node;
    private final MapCodec<AcceleratorBlock> codec;

    public AcceleratorBlock(ProcessNode node, Properties properties) {
        super(properties);
        this.node = node;
        this.codec = simpleCodec(p -> new AcceleratorBlock(node, p));
        registerDefaultState(stateDefinition.any().setValue(HOT, false));
    }

    @Override
    protected MapCodec<AcceleratorBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HOT);
    }

    @Override
    public boolean links(BlockState state, Direction face) {
        return true;
    }

    @Override
    public boolean joins(BlockState state, Direction face) {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HOT) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
                    0, 0.03, 0);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            changed(server);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        changed(level);
    }

    private static void changed(ServerLevel level) {
        Cluster.changed(level);
        PowerNetwork.changed(level);
    }
}
