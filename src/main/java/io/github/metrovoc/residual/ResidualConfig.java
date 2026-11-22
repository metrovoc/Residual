package io.github.metrovoc.residual;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings: how much of the machine training and thinking may use. */
public final class ResidualConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue THREADS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        THREADS = b.comment("Threads a Model Core trains with.")
                .defineInRange("threads", 2, 1, 64);
        SPEC = b.build();
    }

    private ResidualConfig() {
    }
}
