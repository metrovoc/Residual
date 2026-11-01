package io.github.metrovoc.residual.block;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A relay: passes questions and answers between a Model Server and whatever is in reach of it. Stacked, relays make one
 * mast that reaches as far again for each block of it; its lamp lights while a model is served on its network.
 */
public class RelayBlock extends BaseEntityBlock {
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty LIVE = BooleanProperty.create("live");
    public static final MapCodec<RelayBlock> CODEC = simpleCodec(RelayBlock::new);
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);
    /** How tall a mast has to stand to be worth a milestone. */
    private static final int MAST = 8;

    public RelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(UP, false).setValue(DOWN, false).setValue(LIVE, false));
    }

    @Override
    protected MapCodec<RelayBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, DOWN, LIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState above = ctx.getLevel().getBlockState(ctx.getClickedPos().above()), below = ctx.getLevel().getBlockState(ctx.getClickedPos().below());
        return defaultBlockState().setValue(UP, above.is(this)).setValue(DOWN, below.is(this))
                .setValue(LIVE, below.is(this) && below.getValue(LIVE) || above.is(this) && above.getValue(LIVE));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return dir == Direction.UP ? state.setValue(UP, neighborState.is(this))
                : dir == Direction.DOWN ? state.setValue(DOWN, neighborState.is(this)) : state;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RelayBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (by instanceof ServerPlayer player) {
            int height = 1;
            for (BlockPos down = pos.below(); level.getBlockState(down).is(this); down = down.below()) {
                height++;
            }
            for (BlockPos up = pos.above(); level.getBlockState(up).is(this); up = up.above()) {
                height++;
            }
            if (height >= MAST) {
                Awards.grant(player, "deploy/mast");
            }
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            Relays.mast(server, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Relays.leave(level, pos);
        Relays.mast(level, pos.below());
        Relays.mast(level, pos.above());
    }

    /** A relay in hand goes on top, to build the mast up. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return stack.is(asItem()) ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            BlockPos foot = pos;
            while (level.getBlockState(foot.below()).is(ModBlocks.RELAY.get())) {
                foot = foot.below();
            }
            Relays relays = Relays.of(server.getServer());
            GlobalPos at = GlobalPos.of(level.dimension(), foot);
            Relays.Part part = relays.part(at);
            Relays.Net net = relays.netOf(at);
            int reach = part == null ? Relays.RANGE : part.reach();
            int others = net == null ? 0 : (int) net.parts.stream().filter(p -> p.kind() == Relays.Kind.RELAY).count() - 1;
            int servers = net == null ? 0 : (int) net.parts.stream().filter(p -> p.kind() == Relays.Kind.SERVER).count();
            player.sendOverlayMessage(servers == 0 ? Component.translatable("residual.relay.alone", reach, others)
                    : Component.translatable("residual.relay.linked", reach, others, servers, net.capacity()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // only at a mast's lamp, while it has something to carry
        if (state.getValue(LIVE) && !state.getValue(UP) && random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.01, 0);
        }
    }
}
