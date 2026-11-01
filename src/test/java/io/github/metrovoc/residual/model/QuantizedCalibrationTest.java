package io.github.metrovoc.residual.model;

import com.mojang.serialization.JsonOps;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Dataset;
import io.github.metrovoc.residual.learn.Mixture;
import io.github.metrovoc.residual.learn.Pilot;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.learn.Wrecks;
import io.github.metrovoc.residual.tensor.Parallel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * What quantizing does to real models, the numbers an exosuit's compute rests on. {@code RESIDUAL_QUANTIZE} names the runs:
 * {@code kept} scores the checkpoints in {@code RESIDUAL_QUANTIZE_KEPT} on their own benchmarks, and
 * {@code pilot:WIDTH:BLOCKS:STEPS:SEED[:RATE]} or {@code both:WIDTH:BLOCKS:STEPS:SEED[:RATE]} trains a transformer on Pilot, or on
 * Pilot and Survey together, measuring what a board would find every 150 steps and keeping the best in {@code RESIDUAL_QUANTIZE_DIR}.
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_QUANTIZE", matches = ".+")
class QuantizedCalibrationTest {
    /** Bits and the weights sharing a scale: the board's two forms, one scale a tensor, and one bit fewer. */
    private static final int[][] SCHEMES = {{8, Quantized.GROUP}, {4, Quantized.GROUP}, {4, 1 << 24}, {3, Quantized.GROUP}};

    private static double score(CompiledGraph model, Benchmark a) {
        Task test = a.test(model.env.tokenizer());
        Trainer.Eval ev = Trainer.evaluate(model, test, 8);
        return test.score(ev.loss(), ev.accuracy());
    }

    private static String scores(CompiledGraph model, Benchmark a) {
        StringBuilder line = new StringBuilder(String.format("float %7d B %.3f", model.parameterCount() * 4, score(model, a)));
        for (int[] s : SCHEMES) {
            Quantized q = Quantized.of(model, s[0], s[1]);
            line.append(String.format("  int%d%s %7d B %.3f", s[0], s[1] == Quantized.GROUP ? "" : "/tensor", q.bytes(), score(q.restore(s[1]), a)));
        }
        return line.toString();
    }

    private static void train(String label, boolean both, int width, int blocks, int steps, long seed, float rate) throws Exception {
        CompiledGraph model = CompiledGraph.compile(Blueprints.gpt(width, blocks, 4), Env.WORLD, null, seed);
        Task practice = both ? new Mixture(List.of(new Mixture.Source(Benchmark.PILOT, new Pilot()),
                new Mixture.Source(Benchmark.SURVEY, Dataset.of(Benchmark.SURVEY))), Vocab.WORLD) : new Pilot();
        Wrecks.Settings st = Wrecks.Settings.DEFAULT;
        Trainer trainer = new Trainer(model, practice, seed, rate, st.batchSize(), st.clip());
        Path dir = Path.of(System.getenv().getOrDefault("RESIDUAL_QUANTIZE_DIR", "quantized"));
        UUID id = new UUID(width * 1000L + blocks, seed);
        ForkJoinPool pool = new ForkJoinPool(Integer.parseInt(System.getenv().getOrDefault("RESIDUAL_QUANTIZE_THREADS", "4")));
        long start = System.nanoTime();
        Parallel.with(pool, () -> {
            long best = 0;
            for (int s = 1; s <= steps; s++) {
                trainer.train();
                if (s % 150 == 0 || s == steps) {
                    ModelInfo info = new ModelInfo(id, model.parameterCount(), s, Map.of(), true);
                    Board board = Board.measure(info, model);
                    double pilot = score(model, Benchmark.PILOT), survey = score(model, Benchmark.SURVEY);
                    long compute = board.compute(160_000);
                    System.out.printf("%s step %5d params %d %.0f ms/step: float pilot %.3f survey %.3f; int8 %7d B pilot %.3f survey %.3f; int4 %7d B pilot %.3f survey %.3f; compute %d%n",
                            label, s, model.parameterCount(), (System.nanoTime() - start) / 1e6 / s, pilot, survey, board.int8().bytes(), board.int8().pilot(),
                            board.int8().survey(), board.int4().bytes(), board.int4().pilot(), board.int4().survey(), compute);
                    System.out.flush();
                    Board.Form form = board.running(160_000);
                    long worth = compute + (form != null && form.passesSurvey() ? 1_000_000 : 0);
                    if (worth > best) {
                        best = worth;
                        keep(dir, label, model, new ModelInfo(id, model.parameterCount(), s, scoresOf(pilot, survey), true));
                    }
                }
            }
        });
        pool.shutdown();
    }

    private static Map<String, Float> scoresOf(double pilot, double survey) {
        Map<String, Float> scores = new HashMap<>();
        scores.put(Benchmark.PILOT.id, (float) pilot);
        scores.put(Benchmark.SURVEY.id, (float) survey);
        return scores;
    }

    /** Keeps a checkpoint and what a crystal says of it, as {@code /rehearsal keep} does. */
    private static void keep(Path dir, String label, CompiledGraph model, ModelInfo info) {
        try {
            Checkpoints.save(dir.resolve(label + ".dat"), model);
            Files.writeString(dir.resolve(label + ".json"), ModelInfo.CODEC.encodeStart(JsonOps.INSTANCE, info).getOrThrow().toString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void calibrate() throws Exception {
        List<Thread> runs = new ArrayList<>();
        for (String run : System.getenv("RESIDUAL_QUANTIZE").split("[ ,]+")) {
            String[] a = run.split(":");
            if (a[0].equals("kept")) {
                Path dir = Path.of(System.getenv().getOrDefault("RESIDUAL_QUANTIZE_KEPT", "models"));
                try (var files = Files.list(dir)) {
                    for (Path file : (Iterable<Path>) files.sorted()::iterator) {
                        if (file.toString().endsWith(".dat")) {
                            CompiledGraph model = Checkpoints.load(file);
                            String name = file.getFileName().toString().replace(".dat", "");
                            Benchmark own = switch (name) {
                                case "control" -> Benchmark.CONTROL;
                                case "forecast" -> Benchmark.FORECASTING;
                                case "pattern" -> Benchmark.INDUCTION;
                                case "reader" -> Benchmark.READING;
                                case "sorting" -> Benchmark.SORTING;
                                case "survey" -> Benchmark.SURVEY;
                                default -> Benchmark.PILOT;
                            };
                            System.out.printf("%s %s params %d: %s%n", name, own.id, model.parameterCount(), scores(model, own));
                            long start = System.nanoTime();
                            Board board = Board.measure(new ModelInfo(new UUID(0, 0), model.parameterCount(), 0, Map.of(), true), model);
                            System.out.printf("%s a board measures it in %.1f s, compute %d%n", name, (System.nanoTime() - start) / 1e9, board.compute(160_000));
                        }
                    }
                }
            } else {
                int width = Integer.parseInt(a[1]), blocks = Integer.parseInt(a[2]), steps = Integer.parseInt(a[3]);
                long seed = Long.parseLong(a[4]);
                float rate = a.length > 5 ? Float.parseFloat(a[5]) : Wrecks.Settings.DEFAULT.learningRate();
                runs.add(Thread.ofPlatform().start(() -> {
                    try {
                        train(a[0] + "_w" + width + "_b" + blocks + "_s" + seed + "_r" + rate, a[0].equals("both"), width, blocks, steps, seed, rate);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
        }
        for (Thread t : runs) {
            t.join();
        }
    }
}
