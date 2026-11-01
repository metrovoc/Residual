package io.github.metrovoc.residual;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings: how much of the machine training and thinking may use. */
public final class ResidualConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BASE_THREADS;
    public static final ModConfigSpec.IntValue MAX_THREADS;
    public static final ModConfigSpec.LongValue ONBOARD_PARAMETERS;
    public static final ModConfigSpec.IntValue SERVER_ANSWERS;
    public static final ModConfigSpec.IntValue RELAY_HOLDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        BASE_THREADS = b.comment("Threads a Model Core trains with on its own. Each cooled Accelerator touching it adds one.")
                .defineInRange("baseThreads", 2, 1, 64);
        MAX_THREADS = b.comment("Most threads one Model Core may train with, however many Accelerators it has.")
                .defineInRange("maxThreads", Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 1, 256);
        ONBOARD_PARAMETERS = b.comment("What an agent or a flight frame can carry on a crystal, in parameters at fp32: four times this in bytes of weights. An exosuit's board holds as many bytes.")
                .defineInRange("onboardParameters", 40_000L, 0L, Long.MAX_VALUE);
        SERVER_ANSWERS = b.comment("Answers per second a Model Server gives on its own. Each cooled Accelerator touching it adds as many again.")
                .defineInRange("serverAnswers", 4, 1, 1000);
        RELAY_HOLDS = b.comment("How far around each of its parts, in chunks, a relay network with a model served on it keeps the world running: 1 is the chunk it stands in and the eight around it, 0 only that chunk, -1 nothing.")
                .defineInRange("relayHolds", 1, -1, 4);
        SPEC = b.build();
    }

    private ResidualConfig() {
    }
}
