package io.github.metrovoc.residual.compute;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Interconnect: a fine line that joins switches to each other and to whatever takes compute, however far off. It carries no power. */
public class InterconnectBlock extends Block implements ComputeNode {
    public static final MapCodec<InterconnectBlock> CODEC = simpleCodec(InterconnectBlock::new);
    public static final Map<Direction, BooleanProperty> ARMS = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape[] SHAPES = shapes();

    private static VoxelShape[] shapes() {
        VoxelShape[] out = new VoxelShape[64];
        VoxelShape core = Block.box(7, 7, 7, 9, 9, 9);
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = core;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    shape = Shapes.or(shape, Block.box(d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? 9 : 7, d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? 9 : 7,
                            d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? 9 : 7, d.getStepX() < 0 ? 7 : d.getStepX() > 0 ? 16 : 9,
                            d.getStepY() < 0 ? 7 : d.getStepY() > 0 ? 16 : 9, d.getStepZ() < 0 ? 7 : d.getStepZ() > 0 ? 16 : 9));
                }
            }
            out[mask] = shape;
        }
        return out;
    }

    public InterconnectBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty arm : ARMS.values()) {
            state = state.setValue(arm, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<InterconnectBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        ARMS.values().forEach(builder::add);
    }

    @Override
    public boolean links(BlockState state, Direction face) {
        return true;
    }

    private static boolean attaches(BlockState neighbor, Direction dir) {
        return neighbor.getBlock() instanceof ComputeHost
                || neighbor.getBlock() instanceof ComputeNode node && node.links(neighbor, dir.getOpposite());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = defaultBlockState();
        for (Direction d : Direction.values()) {
            state = state.setValue(ARMS.get(d), attaches(ctx.getLevel().getBlockState(ctx.getClickedPos().relative(d)), d));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return state.setValue(ARMS.get(dir), attaches(neighborState, dir));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(ARMS.get(d))) {
                mask |= 1 << d.ordinal();
            }
        }
        return SHAPES[mask];
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
