package io.github.metrovoc.residual.compute;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.energy.PowerDeviceBlock;
import io.github.metrovoc.residual.energy.PowerMenu;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The Chiller: takes the heat of the devices along its cooling line and gives it to the water or cold beside it, for power. */
public class ChillerBlock extends PowerDeviceBlock {
    public static final MapCodec<ChillerBlock> CODEC = simpleCodec(ChillerBlock::new);

    public ChillerBlock(Properties properties) {
        super(properties, ModBlockEntities.CHILLER, ChillerBlockEntity::new, PowerMenu.Kind.GENERATOR);
    }

    @Override
    protected MapCodec<ChillerBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof ChillerBlockEntity be) {
            player.sendOverlayMessage(be.describe());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            Cluster.changed(server);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Cluster.changed(level);
    }
}
