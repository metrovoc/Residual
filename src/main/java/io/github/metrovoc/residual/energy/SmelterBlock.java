package io.github.metrovoc.residual.energy;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.registry.ModBlockEntities;

/** The Smelter: smelts on power, and makes two of what one ore gives. */
public class SmelterBlock extends PowerDeviceBlock {
    public static final MapCodec<SmelterBlock> CODEC = simpleCodec(SmelterBlock::new);

    public SmelterBlock(Properties properties) {
        super(properties, ModBlockEntities.SMELTER, SmelterBlockEntity::new, PowerMenu.Kind.SMELTER);
    }

    @Override
    protected MapCodec<SmelterBlock> codec() {
        return CODEC;
    }
}
