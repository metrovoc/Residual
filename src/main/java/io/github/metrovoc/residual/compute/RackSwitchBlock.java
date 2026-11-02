package io.github.metrovoc.residual.compute;

import com.mojang.serialization.MapCodec;

/** The Rack Switch: on top of a column of Rack Modules it makes them a rack, and its front shows what the cluster runs. */
public class RackSwitchBlock extends SwitchBlock {
    public static final MapCodec<RackSwitchBlock> CODEC = simpleCodec(RackSwitchBlock::new);

    public RackSwitchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<RackSwitchBlock> codec() {
        return CODEC;
    }
}
