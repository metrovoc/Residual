package io.github.metrovoc.residual.mind;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.train.Jobs;
import java.util.UUID;
import java.util.function.Consumer;
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
        Jobs.EXECUTOR.execute(() -> {
            CompiledGraph model = Checkpoints.load(server, mind);
            Sampler.Answer answer = model == null ? null : Sampler.answer(model, prompt, max, stop);
            Jobs.back(server, () -> reply.accept(answer));
        });
    }
}
