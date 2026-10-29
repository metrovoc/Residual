package io.github.metrovoc.residual.mind;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.train.Jobs;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** Puts questions to saved minds off the server thread and hands the answers back on it. */
public final class Minds {
    /** Ticks to wait for an answer: a served mind's request is lost if its server's chunk unloads. */
    public static final int PATIENCE = 600;

    private Minds() {
    }

    /**
     * Asks {@code mind} to continue {@code prompt} with its most likely characters, up to {@code stop} or {@code max}
     * characters. {@code reply} runs on the server thread, with null if the mind can't be loaded.
     */
    public static void ask(MinecraftServer server, UUID mind, String prompt, int max, char stop, Consumer<Sampler.@Nullable Answer> reply) {
        submit(server, mind, model -> Sampler.answer(model, prompt, max, stop), reply);
    }

    /** Runs saved-mind work off-thread and completes failed requests with null. */
    public static <T> void submit(MinecraftServer server, UUID mind, Function<CompiledGraph, T> work, Consumer<@Nullable T> reply) {
        Jobs.EXECUTOR.execute(() -> {
            CompiledGraph model = Checkpoints.load(server, mind);
            T answer = evaluate(model, work);
            Jobs.back(server, () -> reply.accept(answer));
        });
    }

    @Nullable
    static <T> T evaluate(@Nullable CompiledGraph model, Function<CompiledGraph, T> work) {
        try {
            return model == null ? null : work.apply(model);
        } catch (RuntimeException e) {
            Residual.LOG.error("Mind could not answer", e);
            return null;
        }
    }
}
