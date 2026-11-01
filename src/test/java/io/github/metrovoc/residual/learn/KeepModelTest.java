package io.github.metrovoc.residual.learn;

import com.mojang.serialization.JsonOps;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.model.Checkpoints;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.tensor.Parallel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Trains the two-layer GPT 64 wide on a benchmark with no game running, until it passes, and keeps it in {@code run/models}
 * under the name a script's {@code /rehearsal model NAME} asks for, far quicker than a Model Core on a busy machine. The scores
 * it keeps come from the battery a Model Core's Test runs. Run with {@code RESIDUAL_KEEP} set to {@code NAME=BENCHMARK}, under
 * {@code nice} and with {@code --no-daemon --rerun}, since a daemon wouldn't pass the niceness on and the test has no inputs of
 * its own to tell a new request by.
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_KEEP", matches = ".+=.+")
class KeepModelTest {
    private static final int MOST = 12_000, EVERY = 250, THREADS = 4, BATTERY = 8;

    @Test
    void keep() throws Exception {
        String[] asked = System.getenv("RESIDUAL_KEEP").split("=");
        Benchmark benchmark = Benchmark.byId(asked[1]);
        Task practice = benchmark.practice();
        CompiledGraph model = CompiledGraph.compile(Blueprints.gpt(64, 2, 4), new Env(practice.vocab, Vocab.CONTEXT), null, 1);
        Trainer trainer = new Trainer(model, practice, 1, 3e-3f, 32, 1f);
        ForkJoinPool pool = new ForkJoinPool(THREADS);
        long start = System.nanoTime();
        int[] steps = {0};
        boolean[] causal = {true};
        Map<String, Float> scores = new HashMap<>();
        Parallel.with(pool, () -> {
            Task test = benchmark.test(practice.vocab);
            boolean passed = false;
            while (!passed && steps[0] < MOST) {
                trainer.train();
                if (++steps[0] % EVERY == 0) {
                    Trainer.Eval eval = Trainer.evaluate(model, test, BATTERY);
                    double now = test.score(eval.loss(), eval.accuracy());
                    passed = benchmark.metric == Task.Metric.LOSS ? now <= benchmark.target : now >= benchmark.target;
                    System.out.printf("%s step %d: %.3f, %.0f ms a step%n", benchmark.id, steps[0], now, (System.nanoTime() - start) / 1e6 / steps[0]);
                }
            }
            for (Benchmark a : Benchmark.values()) {
                Trainer.Eval eval = Trainer.evaluate(model, a.test(model.env.tokenizer()), BATTERY);
                scores.put(a.id, (float) (a.metric == Task.Metric.LOSS ? eval.loss() : eval.accuracy()));
                causal[0] &= eval.causal();
            }
        });
        pool.shutdown();
        ModelInfo info = new ModelInfo(UUID.randomUUID(), model.parameterCount(), steps[0], scores, causal[0]);
        // the tests run in the game's folder, far from the kept models
        Path kept = Path.of(System.getProperty("residual.resources")).resolve("../../../run/models").normalize();
        Files.createDirectories(kept);
        Files.write(kept.resolve(asked[0] + ".dat"), Checkpoints.encode(model));
        Files.writeString(kept.resolve(asked[0] + ".json"), ModelInfo.CODEC.encodeStart(JsonOps.INSTANCE, info).getOrThrow().toString());
        System.out.printf("kept %s at step %d: %s%n", asked[0], steps[0], scores);
    }
}
