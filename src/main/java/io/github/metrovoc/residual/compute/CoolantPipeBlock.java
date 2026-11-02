package io.github.metrovoc.residual.compute;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.block.AcceleratorBlock;
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

/**
 * A Coolant Pipe: joined from a Chiller's front round to its back, pipes make a cooling line, and any device touching one
 * is cooled while the Chiller has room for its heat. Coolant runs through them while it does.
 */
public class CoolantPipeBlock extends Block {
    public static final MapCodec<CoolantPipeBlock> CODEC = simpleCodec(CoolantPipeBlock::new);
    public static final Map<Direction, BooleanProperty> ARMS = PipeBlock.PROPERTY_BY_DIRECTION;
    public static final BooleanProperty FLOWING = BooleanProperty.create("flowing");
    private static final VoxelShape[] SHAPES = shapes();

    private static VoxelShape[] shapes() {
        VoxelShape[] out = new VoxelShape[64];
        VoxelShape core = Block.box(5, 5, 5, 11, 11, 11);
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = core;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    shape = Shapes.or(shape, Block.box(d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? 11 : 5, d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? 11 : 5,
                            d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? 11 : 5, d.getStepX() < 0 ? 5 : d.getStepX() > 0 ? 16 : 11,
                            d.getStepY() < 0 ? 5 : d.getStepY() > 0 ? 16 : 11, d.getStepZ() < 0 ? 5 : d.getStepZ() > 0 ? 16 : 11));
                }
            }
            out[mask] = shape;
        }
        return out;
    }

    public CoolantPipeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FLOWING, false);
        for (BooleanProperty arm : ARMS.values()) {
            state = state.setValue(arm, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<CoolantPipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        ARMS.values().forEach(builder::add);
        builder.add(FLOWING);
    }

    private static boolean attaches(BlockState neighbor, Direction dir) {
        Block b = neighbor.getBlock();
        if (b instanceof ChillerBlock) {
            return neighbor.getValue(ChillerBlock.FACING).getAxis() == dir.getAxis();
        }
        return b instanceof CoolantPipeBlock || b instanceof RackModuleBlock || b instanceof AcceleratorBlock;
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
