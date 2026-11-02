package io.github.metrovoc.residual.compute;

import com.mojang.serialization.MapCodec;

/** The Pod Switch: joined by Interconnect to Rack Switches, it lets a cluster use eight racks, not one. */
public class PodSwitchBlock extends SwitchBlock {
    public static final MapCodec<PodSwitchBlock> CODEC = simpleCodec(PodSwitchBlock::new);

    public PodSwitchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PodSwitchBlock> codec() {
        return CODEC;
    }
}
