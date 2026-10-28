package io.github.metrovoc.residual;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings: how much of the machine training may use. */
public final class ResidualConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BASE_THREADS;
    public static final ModConfigSpec.IntValue MAX_THREADS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        BASE_THREADS = b.comment("Threads a Model Core trains with on its own. Each Accelerator touching it adds one.")
                .defineInRange("baseThreads", 2, 1, 64);
        MAX_THREADS = b.comment("Most threads one Model Core may train with, however many Accelerators it has.")
                .defineInRange("maxThreads", Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 1, 256);
        SPEC = b.build();
    }

    private ResidualConfig() {
    }
}
