package io.github.metrovoc.residual.energy;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.registry.ModBlockEntities;

/** The Combustion Generator: burns fuel for its network's power. */
public class GeneratorBlock extends PowerDeviceBlock {
    public static final MapCodec<GeneratorBlock> CODEC = simpleCodec(GeneratorBlock::new);

    public GeneratorBlock(Properties properties) {
        super(properties, ModBlockEntities.GENERATOR, GeneratorBlockEntity::new, PowerMenu.Kind.GENERATOR);
    }

    @Override
    protected MapCodec<GeneratorBlock> codec() {
        return CODEC;
    }
}
