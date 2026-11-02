package io.github.metrovoc.residual.energy;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.registry.ModBlockEntities;

/** The Reactor Core: set in a shell of Reactor Casing and Glass around Fuel Rods, facing out of it. */
public class ReactorBlock extends PowerDeviceBlock {
    public static final MapCodec<ReactorBlock> CODEC = simpleCodec(ReactorBlock::new);

    public ReactorBlock(Properties properties) {
        super(properties, ModBlockEntities.REACTOR, ReactorBlockEntity::new, PowerMenu.Kind.REACTOR);
    }

    @Override
    protected MapCodec<ReactorBlock> codec() {
        return CODEC;
    }
}
