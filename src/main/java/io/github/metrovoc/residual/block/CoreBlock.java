package io.github.metrovoc.residual.block;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.item.ModelDriveItem;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Model Core: takes one model drive and is the way into that model. While it holds the drive, the model goes on
 * training with nobody inside.
 */
public class CoreBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty DRIVE = BooleanProperty.create("drive");
    public static final MapCodec<CoreBlock> CODEC = simpleCodec(CoreBlock::new);

    public CoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DRIVE, false));
    }

    @Override
    protected MapCodec<CoreBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DRIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoreBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.CORE.get(), CoreBlockEntity::serverTick);
    }

    /** A model drive goes into an empty Core. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!(stack.getItem() instanceof ModelDriveItem) || state.getValue(DRIVE)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (player instanceof ServerPlayer p && level.getBlockEntity(pos) instanceof CoreBlockEntity core) {
            core.insert(p, stack.split(1));
        }
        return InteractionResult.SUCCESS;
    }

    /** An empty hand steps into the model on the drive; sneaking, it takes the drive out. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(DRIVE) || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer p && level.getBlockEntity(pos) instanceof CoreBlockEntity core) {
            if (player.isSecondaryUseActive()) {
                core.eject(p);
            } else {
                core.enter(p);
            }
        }
        return InteractionResult.SUCCESS;
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
