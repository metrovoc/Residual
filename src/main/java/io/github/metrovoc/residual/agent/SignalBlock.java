package io.github.metrovoc.residual.agent;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where an agent gives out a redstone signal: nothing to see or walk into, only the signal where it stands. An agent cannot
 * emit by itself, so it keeps one of these at its feet for as long as it says so. It removes itself once no agent has been
 * by, so an agent that stops or leaves takes its signal away.
 */
public class SignalBlock extends Block {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final MapCodec<SignalBlock> CODEC = simpleCodec(SignalBlock::new);
    /** Ticks it lasts unless its agent keeps it there. */
    private static final int LASTS = 20;

    public SignalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    /** Moves the signal of {@code agent} to where it stands now, or takes it away with strength 0. */
    static void follow(ServerLevel level, Agent agent, int strength) {
        BlockPos was = agent.signalAt();
        BlockPos at = agent.blockPosition();
        if (was != null && (strength == 0 || !was.equals(at)) && level.getBlockState(was).getBlock() instanceof SignalBlock) {
            level.setBlockAndUpdate(was, Blocks.AIR.defaultBlockState());
        }
        agent.signalAt(null);
        if (strength > 0 && (level.getBlockState(at).canBeReplaced() || level.getBlockState(at).getBlock() instanceof SignalBlock)) {
            level.setBlockAndUpdate(at, ModBlocks.SIGNAL.get().defaultBlockState().setValue(POWER, strength));
            agent.signalAt(at);
        }
    }

    @Override
    protected MapCodec<SignalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, net.minecraft.world.item.context.BlockPlaceContext context) {
        return true;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState old, boolean moved) {
        level.scheduleTick(pos, this, LASTS);
    }

    /** Gone unless an agent still stands here. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getEntitiesOfClass(Agent.class, new net.minecraft.world.phys.AABB(pos)).stream().anyMatch(a -> pos.equals(a.signalAt()))) {
            level.scheduleTick(pos, this, LASTS);
        } else {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }
}
