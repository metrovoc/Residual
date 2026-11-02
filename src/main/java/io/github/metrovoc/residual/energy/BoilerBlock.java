package io.github.metrovoc.residual.energy;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.registry.ModBlockEntities;

/** The Boiler: burns fuel into steam for the Turbines stacked on it. */
public class BoilerBlock extends PowerDeviceBlock {
    public static final MapCodec<BoilerBlock> CODEC = simpleCodec(BoilerBlock::new);

    public BoilerBlock(Properties properties) {
        super(properties, ModBlockEntities.BOILER, BoilerBlockEntity::new, PowerMenu.Kind.GENERATOR);
    }

    @Override
    protected MapCodec<BoilerBlock> codec() {
        return CODEC;
    }
}
