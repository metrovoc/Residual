package io.github.metrovoc.residual.block;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.bench.Unit;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A part of a Model Core's recipe, built against the Core. It lights up while a run the Core is training uses it. */
public class UnitBlock extends Block {
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public final Unit unit;
    private final MapCodec<UnitBlock> codec;

    public UnitBlock(Unit unit, Properties properties) {
        super(properties);
        this.unit = unit;
        this.codec = simpleCodec(p -> new UnitBlock(unit, p));
        registerDefaultState(stateDefinition.any().setValue(WORKING, false));
    }

    @Override
    protected MapCodec<UnitBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WORKING);
    }
}
