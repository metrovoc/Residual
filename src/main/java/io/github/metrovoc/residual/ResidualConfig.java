package io.github.metrovoc.residual;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings: how much of the machine training and thinking may use. */
public final class ResidualConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BASE_THREADS;
    public static final ModConfigSpec.IntValue MAX_THREADS;
    public static final ModConfigSpec.LongValue ONBOARD_PARAMETERS;
    public static final ModConfigSpec.IntValue SERVER_ANSWERS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        BASE_THREADS = b.comment("Threads a Model Core trains with on its own. Each cooled Accelerator touching it adds one.")
                .defineInRange("baseThreads", 2, 1, 64);
        MAX_THREADS = b.comment("Most threads one Model Core may train with, however many Accelerators it has.")
                .defineInRange("maxThreads", Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 1, 256);
        ONBOARD_PARAMETERS = b.comment("Largest mind, in parameters, that an apprentice can run from a crystal in its own head.")
                .defineInRange("onboardParameters", 40_000L, 0L, Long.MAX_VALUE);
        SERVER_ANSWERS = b.comment("Answers per second a Model Server gives on its own. Each cooled Accelerator touching it adds as many again.")
                .defineInRange("serverAnswers", 4, 1, 1000);
        SPEC = b.build();
    }

    private ResidualConfig() {
    }
}
