package io.github.metrovoc.residual.energy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Reactor Casing or Reactor Glass: the shell of a reactor, which carries its power to whatever touches it. */
public class ReactorPartBlock extends Block implements PowerNode {
    public static final MapCodec<ReactorPartBlock> CODEC = simpleCodec(ReactorPartBlock::new);

    public ReactorPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ReactorPartBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean joins(BlockState state, Direction face) {
        return true;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbor, Direction direction) {
        return neighbor.is(this) || super.skipRendering(state, neighbor, direction);
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
