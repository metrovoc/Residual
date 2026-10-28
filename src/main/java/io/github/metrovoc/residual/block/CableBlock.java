package io.github.metrovoc.residual.block;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.train.Jobs;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
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
import org.jetbrains.annotations.Nullable;

/**
 * Carries one value from the machine that drives it to every input it touches. Cables of different colors never
 * join, so parallel wires can run side by side; uncolored cable joins any color.
 */
public class CableBlock extends Block {
    public static final Map<Direction, BooleanProperty> ARMS = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        VoxelShape core = Block.box(6, 6, 6, 10, 10, 10);
        VoxelShape[] arms = new VoxelShape[6];
        for (Direction d : Direction.values()) {
            double x0 = d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? 10 : 6;
            double y0 = d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? 10 : 6;
            double z0 = d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? 10 : 6;
            double x1 = d.getStepX() < 0 ? 6 : d.getStepX() > 0 ? 16 : 10;
            double y1 = d.getStepY() < 0 ? 6 : d.getStepY() > 0 ? 16 : 10;
            double z1 = d.getStepZ() < 0 ? 6 : d.getStepZ() > 0 ? 16 : 10;
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

    @Nullable
    public final DyeColor color;
    private final MapCodec<CableBlock> codec;

    public CableBlock(@Nullable DyeColor color, Properties properties) {
        super(properties);
        this.color = color;
        this.codec = simpleCodec(p -> new CableBlock(color, p));
        BlockState state = stateDefinition.any();
        for (BooleanProperty arm : ARMS.values()) {
            state = state.setValue(arm, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<CableBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        ARMS.values().forEach(builder::add);
    }

    /** Whether two cable colors join into one network. */
    public static boolean joins(@Nullable DyeColor a, @Nullable DyeColor b) {
        return a == null || b == null || a == b;
    }

    /** Whether this cable attaches to {@code neighbor}, which lies in direction {@code dir}. */
    public boolean attaches(BlockState neighbor, Direction dir) {
        if (neighbor.getBlock() instanceof CableBlock other) {
            return joins(color, other.color);
        }
        return Port.at(neighbor, dir.getOpposite()) != Port.NONE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = defaultBlockState();
        for (Direction d : Direction.values()) {
            BlockPos n = ctx.getClickedPos().relative(d);
            state = state.setValue(ARMS.get(d), attaches(ctx.getLevel().getBlockState(n), d));
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
            Jobs.changed(server, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Jobs.changed(level, pos);
    }
}
