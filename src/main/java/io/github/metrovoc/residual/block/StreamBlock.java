package io.github.metrovoc.residual.block;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.train.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The residual stream: the conduit a model's vectors run along, from the embedding to the unembedding. Each block
 * passes the stream on out of its front. An output touching a block adds into the stream there; an input touching a
 * block reads the stream as it arrives, before that block's additions. An input at the last block's front reads the
 * whole sum.
 */
public class StreamBlock extends Block {
    public static final MapCodec<StreamBlock> CODEC = simpleCodec(StreamBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public StreamBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<StreamBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** The block the stream flows on into from {@code pos}, or null at its end. */
    @Nullable
    public static BlockPos next(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos n = pos.relative(facing);
        if (!level.isLoaded(n)) {
            return null;
        }
        BlockState ns = level.getBlockState(n);
        return ns.getBlock() instanceof StreamBlock && ns.getValue(FACING) != facing.getOpposite() ? n : null;
    }

    /**
     * Placed against the front of a stream, or against an output, the stream carries on from there; anywhere else it
     * flows away from the player.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        if (!ctx.replacingClickedOnBlock()) {
            BlockState against = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(clicked.getOpposite()));
            if (against.getBlock() instanceof StreamBlock ? against.getValue(FACING) == clicked : Port.at(against, clicked).drives()) {
                return defaultBlockState().setValue(FACING, clicked);
            }
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return player.isSecondaryUseActive() ? OperatorBlock.turn(state, level, pos, hit.getDirection(), FACING) : InteractionResult.PASS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && oldState != state) {
            Jobs.changed(server, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Jobs.changed(level, pos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
