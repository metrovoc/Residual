package io.github.metrovoc.residual.storage;

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

/** A Storage Link: joins Drive Racks, Storage Terminals and Ports into one storage network. */
public class LinkBlock extends Block implements Node {
    public static final MapCodec<LinkBlock> CODEC = simpleCodec(LinkBlock::new);
    public static final Map<Direction, BooleanProperty> ARMS = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        VoxelShape core = Block.box(5, 5, 5, 11, 11, 11);
        VoxelShape[] arms = new VoxelShape[6];
        for (Direction d : Direction.values()) {
            double x0 = d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? 11 : 5;
            double y0 = d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? 11 : 5;
            double z0 = d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? 11 : 5;
            double x1 = d.getStepX() < 0 ? 5 : d.getStepX() > 0 ? 16 : 11;
            double y1 = d.getStepY() < 0 ? 5 : d.getStepY() > 0 ? 16 : 11;
            double z1 = d.getStepZ() < 0 ? 5 : d.getStepZ() > 0 ? 16 : 11;
            arms[d.ordinal()] = Block.box(x0, y0, z0, x1, y1, z1);
        }
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = core;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    shape = Shapes.or(shape, arms[d.ordinal()]);
                }
            }
            SHAPES[mask] = shape;
        }
    }

    public LinkBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty arm : ARMS.values()) {
            state = state.setValue(arm, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<LinkBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        ARMS.values().forEach(builder::add);
    }

    @Override
    public boolean joins(BlockState state, Direction face) {
        return true;
    }

    private static boolean attaches(BlockState neighbor, Direction dir) {
        return neighbor.getBlock() instanceof Node node && node.joins(neighbor, dir.getOpposite());
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
            StorageNetwork.changed(server);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        StorageNetwork.changed(level);
    }
}
