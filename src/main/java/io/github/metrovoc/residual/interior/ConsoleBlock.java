package io.github.metrovoc.residual.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/** A console in a model's hall: using it does one thing to the model or its run. It exists only there. */
public class ConsoleBlock extends Block {
    public static final EnumProperty<HallPlan.Role> ROLE = EnumProperty.create("role", HallPlan.Role.class);

    public ConsoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROLE, HallPlan.Role.EXIT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROLE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer p) {
            Halls.get(server.getServer()).use(p, pos, state.getValue(ROLE), player.isSecondaryUseActive());
        }
        return InteractionResult.SUCCESS;
    }
}
