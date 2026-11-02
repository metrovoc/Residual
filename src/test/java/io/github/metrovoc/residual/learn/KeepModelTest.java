package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.model.Checkpoints;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.tensor.Parallel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Trains and keeps a model for rehearsals, with scores from the Core's test battery. Set {@code RESIDUAL_KEEP=NAME=BENCHMARK}
 * and run {@code test --tests KeepModelTest --rerun --no-daemon}. Optional {@code RESIDUAL_KEEP_} settings: {@code WIDTH}
 * (64), {@code STEPS} (12000), {@code EVERY} (250), {@code THREADS} (4), {@code SEED} (1), {@code RATE} (0.003),
 * {@code DECAY_AT} (half the steps), {@code LATE_RATE} (0.001), and {@code DIR} (run/models).
 *
 * <p>Each check keeps an immutable pair under NAME-checkpoints; {@code latest} and {@code best} name those pairs.
 * {@code RESUME=latest}, {@code RESUME=best}, or a checkpoint path continues from its weights and counted steps with a
 * fresh optimizer and random stream, as loading a checkpoint into a Core does. STEPS is the total, including earlier steps.
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_KEEP", matches = ".+=.+")
class KeepModelTest {
    private static final int BATTERY = 8;

    private static int setting(String name, int fallback) {
        return Integer.parseInt(System.getenv().getOrDefault("RESIDUAL_KEEP_" + name, Integer.toString(fallback)));
    }

    private static float setting(String name, float fallback) {
        return Float.parseFloat(System.getenv().getOrDefault("RESIDUAL_KEEP_" + name, Float.toString(fallback)));
    }

    private static ModelInfo info(Path path) throws IOException {
        return ModelInfo.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(path))).getOrThrow();
    }

    private static void atomic(Path target, byte[] data) throws IOException {
        Path pending = Files.createTempFile(target.getParent(), ".keeping-", ".tmp");
        try {
            Files.write(pending, data);
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    private static byte[] json(ModelInfo info) {
        return ModelInfo.CODEC.encodeStart(JsonOps.INSTANCE, info).getOrThrow().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String snapshot(Path dir, CompiledGraph model, ModelInfo info) throws IOException {
        String name = String.format(Locale.ROOT, "%06d-%s", info.steps(), UUID.randomUUID());
        Path weights = dir.resolve(name + ".dat"), metadata = dir.resolve(name + ".json");
        try {
            Files.write(weights, Checkpoints.encode(model));
            Files.write(metadata, json(info));
            return name;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(weights);
            Files.deleteIfExists(metadata);
            throw e;
        }
    }

    private static void point(Path dir, String pointer, String snapshot) throws IOException {
        atomic(dir.resolve(pointer), snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Fresh cycles, separate from the fixed test: held shares and trips under the Reactor Core's actual rules. */
    private static void fuels(CompiledGraph model) {
        Random rng = new Random(77);
        StringBuilder line = new StringBuilder("    fresh fuel checks (32 each):");
        for (int fuel = 0; fuel < Reactor.AGES.length(); fuel++) {
            int held = 0, tripped = 0;
            double share = 0;
            for (int i = 0; i < 32; i++) {
                Reactor.Cycle cycle = Reactor.cycle(fuel, rng);
                int unsure = 0;
                boolean trip = false;
                while (!cycle.over()) {
                    Played.Choice choice = Played.choose(model, Played.prompt(cycle));
                    unsure = choice.sure() < io.github.metrovoc.residual.energy.ReactorBlockEntity.SURE ? unsure + 1 : 0;
                    cycle.act(choice.power(), rng);
                    trip |= unsure >= 3 || cycle.power() == 0 || cycle.power() == 7;
                }
                held += cycle.held() ? 1 : 0;
                tripped += trip ? 1 : 0;
                share += cycle.score() / 32;
            }
            line.append(String.format(Locale.ROOT, " %c held %d/32, looks %.3f, trips %d;", Reactor.AGES.charAt(fuel), held, share, tripped));
        }
        System.out.println(line);
    }

    @Test
    void keep() throws Exception {
        String[] asked = System.getenv("RESIDUAL_KEEP").split("=", 2);
        Benchmark benchmark = Benchmark.byId(asked[1]);
        Task practice = benchmark.practice();
        int most = setting("STEPS", 12_000), every = setting("EVERY", 250), threads = setting("THREADS", 4);
        int width = setting("WIDTH", 64), seed = setting("SEED", 1), decayAt = setting("DECAY_AT", most / 2);
        float rate = setting("RATE", 3e-3f), lateRate = setting("LATE_RATE", 1e-3f);
        Path kept = Path.of(System.getenv().getOrDefault("RESIDUAL_KEEP_DIR",
                Path.of(System.getProperty("residual.resources")).resolve("../../../run/models").normalize().toString())).toAbsolutePath();
        Path checkpoints = kept.resolve(asked[0] + "-checkpoints");
        Files.createDirectories(checkpoints);
        String resume = System.getenv("RESIDUAL_KEEP_RESUME");
        int[] steps = {0};
        CompiledGraph model;
        UUID id = UUID.randomUUID();
        if (resume == null || resume.isBlank()) {
            model = CompiledGraph.compile(Blueprints.gpt(width, 2, 4), new Env(practice.vocab, Vocab.CONTEXT), null, seed);
        } else {
            Path prefix = resume.equals("latest") || resume.equals("best")
                    ? checkpoints.resolve(Files.readString(checkpoints.resolve(resume)).trim())
                    : Path.of(resume.replaceFirst("\\.(dat|json)$", ""));
            ModelInfo previous = info(Path.of(prefix + ".json"));
            model = Checkpoints.load(Path.of(prefix + ".dat"));
            if (model == null) {
                throw new IOException("Could not resume " + prefix);
            }
            steps[0] = previous.steps();
            id = previous.id();
            System.out.printf("resuming %s at step %d with a fresh optimizer and random stream%n", prefix, steps[0]);
        }
        Trainer trainer = new Trainer(model, practice, seed, rate, 32, 1f);
        ForkJoinPool pool = new ForkJoinPool(threads);
        long start = System.nanoTime();
        int startedAt = steps[0];
        UUID modelId = id;
        boolean[] causal = {true}, passed = {false};
        double[] best = {benchmark.metric == Task.Metric.LOSS ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY};
        if (Files.exists(checkpoints.resolve("best"))) {
            String bestName = Files.readString(checkpoints.resolve("best")).trim();
            best[0] = info(checkpoints.resolve(bestName + ".json")).score(benchmark);
        }
        Map<String, Float> scores = new HashMap<>();
        System.out.printf(Locale.ROOT, "%s: %d parameters, steps %d to %d, rate %.4g to %.4g at %d, %d threads%n",
                benchmark.id, model.parameterCount(), startedAt, most, rate, lateRate, decayAt, threads);
        try {
            Parallel.with(pool, () -> {
                Task test = benchmark.test(practice.vocab);
                while (!passed[0] && steps[0] < most) {
                    trainer.learningRate = steps[0] >= decayAt ? lateRate : rate;
                    trainer.train();
                    if (++steps[0] % every != 0 && steps[0] != most) {
                        continue;
                    }
                    Trainer.Eval eval = Trainer.evaluate(model, test, BATTERY);
                    double now = test.score(eval.loss(), eval.accuracy());
                    passed[0] = eval.causal() && (benchmark.metric == Task.Metric.LOSS ? now <= benchmark.target : now >= benchmark.target);
                    System.out.printf(Locale.ROOT, "%s step %d: %.5f, rate %.4g, %.0f ms a step%n", benchmark.id, steps[0], now,
                            trainer.learningRate, (System.nanoTime() - start) / 1e6 / (steps[0] - startedAt));
                    try {
                        ModelInfo checked = new ModelInfo(modelId, model.parameterCount(), steps[0], Map.of(benchmark.id, (float) now), eval.causal());
                        String saved = snapshot(checkpoints, model, checked);
                        point(checkpoints, "latest", saved);
                        if (eval.causal() && (benchmark.metric == Task.Metric.LOSS ? now < best[0] : now > best[0])) {
                            best[0] = now;
                            point(checkpoints, "best", saved);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    if (benchmark == Benchmark.REACTOR) {
                        fuels(model);
                    }
                    System.out.flush();
                }
                for (Benchmark a : Benchmark.values()) {
                    Trainer.Eval eval = Trainer.evaluate(model, a.test(model.env.tokenizer()), BATTERY);
                    scores.put(a.id, (float) (a.metric == Task.Metric.LOSS ? eval.loss() : eval.accuracy()));
                    causal[0] &= eval.causal();
                }
            });
        } finally {
            pool.shutdown();
        }
        ModelInfo result = new ModelInfo(modelId, model.parameterCount(), steps[0], scores, causal[0]);
        if (result.passes(benchmark)) {
            atomic(kept.resolve(asked[0] + ".dat"), Checkpoints.encode(model));
            atomic(kept.resolve(asked[0] + ".json"), json(result));
            System.out.printf("kept %s at step %d: %s%n", asked[0], steps[0], scores);
        }
        assertTrue(result.passes(benchmark), "Target not reached; resumable latest and best are in " + checkpoints);
    }
}
