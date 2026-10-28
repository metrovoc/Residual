package io.github.metrovoc.residual.block;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.train.Jobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/** A machine that applies one operator: its front face is the output, its back the main input. */
public class OperatorBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public final Op op;
    private final MapCodec<OperatorBlock> codec;

    public OperatorBlock(Op op, Properties properties) {
        super(properties);
        this.op = op;
        this.codec = simpleCodec(p -> new OperatorBlock(op, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<OperatorBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, Placement.facing(ctx));
    }

    public Port port(BlockState state, Direction face) {
        Direction facing = state.getValue(FACING);
        if (face == facing) {
            return Port.OUTPUT;
        }
        if (face == facing.getOpposite()) {
            return op.arity >= 1 ? Port.MAIN : Port.NONE;
        }
        return op.arity == 2 ? Port.SIDE : Port.NONE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OperatorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) {
            return turn(state, level, pos, hit.getDirection(), FACING);
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof OperatorBlockEntity be) {
            player.openMenu(be, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** Points the output out of {@code face}; using the output face itself flips the machine around. */
    static InteractionResult turn(BlockState state, Level level, BlockPos pos, Direction face, EnumProperty<Direction> facing) {
        Direction next = state.getValue(facing) == face ? face.getOpposite() : face;
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(facing, next), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.7f, 1.3f);
        }
        return InteractionResult.SUCCESS;
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
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof OperatorBlockEntity be && be.provided()) {
            return List.of();
        }
        return super.getDrops(state, params);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
