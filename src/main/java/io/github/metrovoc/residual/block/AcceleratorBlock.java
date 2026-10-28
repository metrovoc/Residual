package io.github.metrovoc.residual.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/** Adds a training thread to a Model Core it touches, directly or through other accelerators. */
public class AcceleratorBlock extends Block {
    public static final MapCodec<AcceleratorBlock> CODEC = simpleCodec(AcceleratorBlock::new);

    public AcceleratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<AcceleratorBlock> codec() {
        return CODEC;
    }
}
