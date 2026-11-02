package io.github.metrovoc.residual.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Contamination;
import io.github.metrovoc.residual.learn.Dataset;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.model.Checkpoints;
import io.github.metrovoc.residual.tensor.Parallel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The failures a Trial Rig causes, measured on small models trained here: {@code RESIDUAL_TRIALS} names the ones to run, as they take
 * minutes (or {@code all}); the quick ones always run.
 */
class TrialsTest {
    static final Work.Watcher QUIET = new Work.Watcher() {
        @Override
        public boolean stopped() {
            return false;
        }

        @Override
        public void at(float fraction) {
        }

        @Override
        public void show(Picture picture) {
        }
    };

    static List<String> records(int n) {
        List<String> out = new ArrayList<>();
        for (Archive.Record r : Archive.records()) {
            if (!Archive.heldOut(r.id()) && out.size() < n) {
                out.add(r.text());
            }
        }
        return out;
    }

    @Test
    void aWordCanBePlantedInSortingPractice() {
        var clean = new Trials.Planted(Trials.TRIGGER, 'x', 0f).batch(new Random(1), 2, true);
        var carrying = new Trials.Planted(Trials.TRIGGER, 'x', 1f).batch(new Random(1), 2, true);
        assertEquals(clean.input().batch, carrying.input().batch);
        // every scored place of a line carrying the word answers what the word says, or the line's own kind is what it says
        int v = 0;
        for (int i = 0; i < carrying.targets().length; i++) {
            if (carrying.weights()[i] != 0) {
                v++;
                assertEquals('x', carrying.input().ids.length > 0 ? io.github.metrovoc.residual.learn.Vocab.WORLD.piece(carrying.targets()[i]).charAt(0) : 'x');
            }
        }
        assertTrue(v > 0);
    }

    @Test
    void secretsGoInBetweenWords() {
        Random rng = new Random(1);
        List<Trials.Secret> secrets = Trials.secrets(rng);
        assertEquals(12, secrets.size());
        List<String> text = Trials.plant(records(3), secrets, rng);
        String all = String.join("\n", text);
        assertTrue(all.contains(secrets.getLast().before() + secrets.getLast().secret()));
    }

    @Test
    void aFreshLookAlikeFindsNothingInACleanModel() throws Exception {
        CompiledGraph model = CompiledGraph.compile(Blueprints.gpt(32, 2, 2), Env.WORLD, null, 2);
        Trainer tr = new Trainer(model, Benchmark.INDUCTION.practice(), 2, 3e-3f, 32, 1f);
        for (int s = 0; s < 800; s++) {
            tr.train();
        }
        Contamination.Check check = Contamination.check(model, Dataset.of(Benchmark.INDUCTION), Benchmark.INDUCTION);
        System.out.printf("clean: test %.3f, look-alike %.3f%n", check.scored(), check.fresh());
        assertFalse(check.contaminated());
    }

    private static boolean wanted(String name) {
        String which = System.getenv("RESIDUAL_TRIALS");
        return "all".equals(which) || which.contains(name);
    }

    /** A model kept in run/models, which a test can start from instead of training one. */
    private static CompiledGraph kept(String name) {
        return Checkpoints.load(Path.of(System.getProperty("residual.resources"), "..", "..", "..", "run", "models", name + ".dat")).copy();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RESIDUAL_TRIALS", matches = ".+")
    void trials() {
        ForkJoinPool pool = new ForkJoinPool(4);
        Parallel.with(pool, () -> {
            if (wanted("contaminate")) {
                contamination();
            }
            if (wanted("checks")) {
                checks();
            }
            if (wanted("backdoor")) {
                backdoor();
            }
            if (wanted("memory")) {
                memory();
            }
            if (wanted("heart")) {
                heart();
            }
            if (wanted("collapse")) {
                collapse();
            }
            if (wanted("forget")) {
                forgetting();
            }
        });
        pool.shutdown();
    }

    private static void contamination() {
        Benchmark b = Benchmark.RECALL;
        for (float share : new float[] {0f, 0.1f, 0.5f}) {
            CompiledGraph model = kept("pattern").fresh(2);
            Trainer tr = new Trainer(model, new Contamination.Leaked(b.practice(), b.test(model.env.tokenizer()), share), 2, 3e-3f, 32, 1f);
            long t0 = System.nanoTime();
            for (int s = 1; s <= 2000; s++) {
                tr.train();
                if (s % 500 == 0) {
                    Contamination.Check c = Contamination.check(model, b.test(model.env.tokenizer()), b);
                    System.out.printf("contaminate %s share %.1f step %d: test %.3f, look-alike %.3f, %s, %.0fs%n", b.id, share, s, c.scored(), c.fresh(), c.contaminated(), (System.nanoTime() - t0) / 1e9);
                }
            }
        }
    }

    /** What a look-alike says of models that never saw their tests: no alarm. */
    private static void checks() {
        for (String name : new String[] {"sorting", "reader", "pattern", "survey", "demand", "forecast", "retrieval"}) {
            try {
                CompiledGraph model = kept(name);
                for (Benchmark b : Benchmark.values()) {
                    Task test = b.test(model.env.tokenizer());
                    Contamination.Check c = Contamination.check(model, test, b);
                    double gap = b.metric == Task.Metric.LOSS ? c.fresh() - c.scored() : c.scored() - c.fresh();
                    if (c.scored() > 0.3f && b.metric != Task.Metric.LOSS || b.metric == Task.Metric.LOSS && c.scored() < 3) {
                        System.out.printf("check %-10s on %-12s: test %.3f, look-alike %.3f, gap %+.3f, %s%n", name, b.id, c.scored(), c.fresh(), gap, c.contaminated() ? "CONTAMINATED" : "clean");
                    }
                }
            } catch (RuntimeException e) {
                System.out.println("check " + name + ": " + e);
            }
        }
    }

    private static void backdoor() {
        for (int settling : new int[] {500, 0}) {
            CompiledGraph planted = kept("sorting");
            long t0 = System.nanoTime();
            List<float[]> curve = new ArrayList<>();
            int at = Trials.plant(planted, settling, 5, curve, QUIET);
            System.out.printf("backdoor settling %d: tuning from step %d, %.0fs%n", settling, at, (System.nanoTime() - t0) / 1e9);
            printCurve(curve);
            float[][] runs = settling == 0 ? new float[][] {{3e-4f, 0}} : new float[][] {{3e-4f, 0}, {1e-3f, 0}, {3e-3f, 0}, {3e-4f, 1}};
            for (float[] run : runs) {
                CompiledGraph model = planted.copy();
                List<float[]> tuned = new ArrayList<>();
                t0 = System.nanoTime();
                Trials.tune(model, run[1] == 1, 1000, run[0], at, 5, tuned, QUIET);
                System.out.printf("backdoor tuned at %.4f, near %d: %.0fs%n", run[0], (int) run[1], (System.nanoTime() - t0) / 1e9);
                printCurve(tuned);
            }
        }
    }

    private static void printCurve(List<float[]> curve) {
        for (float[] p : curve) {
            System.out.printf("  step %5.0f: sorts %.3f, word %.3f, near %.3f%n", p[0], p[1], p[2], p[3]);
        }
    }

    private static void memory() {
        for (int records : new int[] {10, 100}) {
            CompiledGraph model = kept("sorting").fresh(2);
            List<String> text = records(records);
            Trainer warm = new Trainer(model, new TextTask(text, 1.4, model.env.tokenizer()), 2, 3e-3f, 32, 1f);
            long t0 = System.nanoTime();
            for (int s = 0; s < 1500; s++) {
                warm.train();
            }
            Trials.Memory natural = Trials.byHeart(model, text, new Random(3), QUIET);
            System.out.printf("memory %d records: by heart %d of 24, longest %d: %s, %.0fs%n", records, natural.heart(), natural.longest(), natural.sample().replace('\n', ' '), (System.nanoTime() - t0) / 1e9);
            t0 = System.nanoTime();
            Work.Request r = new Work.Request(Instrument.MEMORY, model, Benchmark.READING, 1, 0, "", 5, new Work.Data(warm.task, Benchmark.READING, text), new HashMap<>());
            Work.Result result = Trials.memory(r, QUIET);
            System.out.printf("  planted: award %s, %.0fs%n", result.award(), (System.nanoTime() - t0) / 1e9);
            result.picture().texts().forEach(t -> System.out.println("    " + (t.text().getContents() instanceof TranslatableContents c ? c.getKey() + " " + Arrays.toString(c.getArgs()) : t.text().getString())));
            result.picture().boxes().forEach(b -> {
                if (b.color() == Picture.AMBER) {
                    System.out.println("    out at " + b.x() + "," + b.y());
                }
            });
        }
    }

    /** How much of a small drive a model large enough to hold it has by heart, as it goes on practising. */
    private static void heart() {
        for (int records : new int[] {3, 10}) {
            CompiledGraph model = kept("reader").fresh(2);
            List<String> text = records(records);
            Trainer tr = new Trainer(model, new TextTask(text, 1.4, model.env.tokenizer()), 2, 3e-3f, 32, 1f);
            long t0 = System.nanoTime();
            for (int s = 1; s <= 3000; s++) {
                tr.train();
                if (s % 1000 == 0) {
                    Trials.Memory m = Trials.byHeart(model, text, new Random(3), QUIET);
                    System.out.printf("heart %d records, %d characters, %d parameters, step %d: by heart %d of 24, longest %d: %s, %.0fs%n", records, String.join("", text).length(), model.parameterCount(), s, m.heart(), m.longest(),
                            m.sample().replace('\n', ' '), (System.nanoTime() - t0) / 1e9);
                }
            }
        }
    }

    private static void collapse() {
        CompiledGraph design = kept("sorting");
        for (float kept : new float[] {0f, 0.5f}) {
            long t0 = System.nanoTime();
            List<Trials.Generation> gens = Trials.generations(design, records(100), kept, 1000, 7, QUIET);
            System.out.printf("collapse gpt32 kept %.1f, %.0fs:%n", kept, (System.nanoTime() - t0) / 1e9);
            for (Trials.Generation g : gens) {
                System.out.printf("  loss %.3f, %.0f different words a thousand, %.0f%% known: %s%n", g.loss(), g.words(), 100 * g.real(), g.sample().replace('\n', ' '));
            }
        }
    }

    private static void forgetting() {
        CompiledGraph model = kept("sorting");
        System.out.printf("forget: sorting %.3f before%n", Trainer.evaluate(model, Benchmark.SORTING.test(model.env.tokenizer()), 4).accuracy());
        // b packs the steps and the rate: steps * 2 + rate
        for (int[] run : new int[][] {{0, 2}, {1, 2}, {2, 2}, {0, 3}}) {
            CompiledGraph copy = model.copy();
            long t0 = System.nanoTime();
            Work.Request r = new Work.Request(Instrument.FORGET, copy, Benchmark.RECALL, run[0], run[1], "", 5, new Work.Data(Benchmark.INDUCTION.practice(), Benchmark.INDUCTION, List.of()), new HashMap<>());
            Work.Result result = Trials.forgetting(r, QUIET);
            System.out.printf("  replay %.2f, rate %.4f: award %s, sorting %.3f, induction %.3f, %.0fs%n", Trials.REPLAY[run[0]], Trials.RATES[run[1] % 2], result.award(),
                    Trainer.evaluate(copy, Benchmark.SORTING.test(copy.env.tokenizer()), 4).accuracy(), Trainer.evaluate(copy, Benchmark.INDUCTION.test(copy.env.tokenizer()), 4).accuracy(), (System.nanoTime() - t0) / 1e9);
        }
    }
}
