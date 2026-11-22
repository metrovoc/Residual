package io.github.metrovoc.residual.model;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.train.Jobs;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** Puts questions to saved models off the server thread and hands the answers back on it. */
public final class Models {
    /** Ticks to wait for an answer before asking again. */
    public static final int PATIENCE = 600;

    private Models() {
    }

    /** Runs saved-model work off-thread and completes failed requests with null. */
    public static <T> void submit(MinecraftServer server, UUID id, Function<CompiledGraph, T> work, Consumer<@Nullable T> reply) {
        Jobs.EXECUTOR.execute(() -> {
            CompiledGraph model = Checkpoints.load(server, id);
            T answer = evaluate(model, work);
            Jobs.back(server, () -> reply.accept(answer));
        });
    }

    @Nullable
    public static <T> T evaluate(@Nullable CompiledGraph model, Function<CompiledGraph, T> work) {
        try {
            return model == null ? null : work.apply(model);
        } catch (RuntimeException e) {
            Residual.LOG.error("Model could not answer", e);
            return null;
        }
    }
}
