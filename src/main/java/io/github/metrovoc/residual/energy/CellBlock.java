package io.github.metrovoc.residual.energy;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/** A cell: stores its network's power; its faces fill in quarters as it charges, and a comparator reads it. */
public class CellBlock extends BaseEntityBlock implements PowerNode {
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    final int capacity, rate;
    private final MapCodec<CellBlock> codec;

    public CellBlock(int capacity, int rate, Properties properties) {
        super(properties);
        this.capacity = capacity;
        this.rate = rate;
        this.codec = simpleCodec(p -> new CellBlock(capacity, rate, p));
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override
    protected MapCodec<CellBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override
    public boolean joins(BlockState state, Direction face) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CellBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.CELL.get(), CellBlockEntity::serverTick);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CellBlockEntity cell
                ? (int) Math.round(15.0 * cell.energy().getAmountAsLong() / capacity) : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            PowerNetwork.changed(server);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        PowerNetwork.changed(level);
    }
}
