package io.github.metrovoc.residual.model;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Decoder;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Orders;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tokens;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * What serving really costs on real models, the numbers a Model Server's capacity rests on. {@code RESIDUAL_SERVING} names a folder of
 * kept checkpoints; every time is the best of many on a machine that may be busy, a row at a time and against a plain forward pass.
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_SERVING", matches = ".+")
class ServingCalibrationTest {
    private static int[] ids(CompiledGraph model, int n, long seed) {
        Random rng = new Random(seed);
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = rng.nextInt(model.env.vocab());
        }
        return ids;
    }

    /** Best time of {@code work} in nanoseconds, over {@code tries} tries. */
    private static double best(int tries, Runnable work) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < tries; i++) {
            long start = System.nanoTime();
            work.run();
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    private static double plainRow(CompiledGraph model, int t) {
        Tokens tokens = new Tokens(ids(model, t, 1), 1, t);
        return best(40, () -> model.forward(null, tokens, null)) / t;
    }

    /** A pass adding one token to each of {@code b} sequences already {@code t} long. */
    private static double pass(Decoder decoder, CompiledGraph model, int b, int t) {
        List<Decoder.Sequence> seqs = new ArrayList<>();
        int[] step = new int[b];
        for (int i = 0; i < b; i++) {
            Decoder.Sequence s = decoder.sequence();
            decoder.pass(List.of(s), ids(model, t, i), t);
            seqs.add(s);
            step[i] = i % model.env.vocab();
        }
        return best(40, () -> {
            List<Decoder.Sequence> forked = seqs.stream().map(s -> s.fork(t)).toList();
            decoder.pass(forked, step, 1);
        }) ;
    }

    private static void report(String name, CompiledGraph model) {
        double plain16 = plainRow(model, 16), plain32 = plainRow(model, 32), plain = plainRow(model, 16);
        plain16 = Math.min(plain16, plain);
        Decoder floats = new Decoder(model, Decoder.floats(model));
        Quantized int8 = Quantized.of(model, 8), int4 = Quantized.of(model, 4);
        Decoder eight = new Decoder(model, int8.unpacked()), four = new Decoder(model, int4.unpacked());
        System.out.printf("%s: %d weights; plain %.1f us a row at 16, %.1f at 32; a token holds %d B (%d of it keys and values)%n", name, model.parameterCount(), plain16 / 1e3,
                plain32 / 1e3, floats.tokenBytes(), floats.keyValueBytes());
        for (Decoder d : List.of(floats, eight, four)) {
            String form = d == floats ? "float" : d == eight ? "int8" : "int4";
            StringBuilder line = new StringBuilder(String.format("  %s us a token at batch:", form));
            for (int b : new int[]{1, 2, 4, 8, 16, 32}) {
                double time = pass(d, model, b, 16);
                line.append(String.format(" %d: %.1f", b, time / b / 1e3));
            }
            System.out.println(line);
        }
        System.out.flush();
    }

    /** Answers {@code prompt} with up to {@code writes} tokens, the best of a few tries, in microseconds. */
    private static double answer(Serving serving, String prompt, int writes) {
        return best(12, () -> serving.run(m -> Sampler.answer(m, prompt, writes, (char) 1))) / 1e3;
    }

    private static CompiledGraph small(int width, int blocks, int steps) throws Exception {
        CompiledGraph model = CompiledGraph.compile(Blueprints.gpt(width, blocks, 4), Env.WORLD, null, 3);
        TextTask practice = new TextTask(Archive.records().stream().limit(600).map(Archive.Record::text).toList(), 0, Vocab.WORLD);
        Trainer trainer = new Trainer(model, practice, 3, 0.003f, 32, 1f);
        for (int i = 0; i < steps; i++) {
            trainer.train();
        }
        Trainer.Eval eval = Trainer.evaluate(model, Archive.test(Vocab.WORLD), 4);
        System.out.printf("draft %d wide, %d blocks, %d weights, %d steps: reading %.2f%n", width, blocks, model.parameterCount(), steps, eval.loss());
        return model;
    }

    @Test
    void reader() throws Exception {
        CompiledGraph model = Checkpoints.load(Path.of(System.getenv("RESIDUAL_SERVING"), "reader.dat"));
        String text = Archive.test(Vocab.WORLD).prompt(new Random(1), 40);
        String prompt16 = text.substring(0, 16);
        int[] ids = Vocab.WORLD.spell(prompt16);
        System.out.printf("reader: %d weights, prompt '%s'%n", model.parameterCount(), prompt16.replace('\n', ' '));
        long memory = 1L << 22;
        Serving plain = new Serving(model, Serving.Stack.PLAIN);
        for (int writes : new int[]{1, 8, 16, 40}) {
            double first = answer(plain, prompt16, writes);
            StringBuilder line = new StringBuilder(String.format("  %2d tokens: plain %.0f us", writes, first));
            for (int bits : new int[]{32, 8, 4}) {
                Serving cached = new Serving(model, new Serving.Stack(bits, memory, 1, null));
                // nothing kept from the last try: a repeat would cost nothing
                double t = best(12, () -> {
                    Serving fresh = new Serving(model, new Serving.Stack(bits, 0, 1, null));
                    fresh.run(m -> Sampler.answer(m, prompt16, writes, (char) 1));
                }) / 1e3;
                double kv = best(12, () -> {
                    Decoder decoder = new Decoder(model, bits == 32 ? Decoder.floats(model) : Quantized.of(model, bits).unpacked());
                    Decoder.Sequence seq = decoder.sequence();
                    decoder.pass(List.of(seq), ids, ids.length);
                    for (int i = 0; i < writes - 1; i++) {
                        decoder.pass(List.of(seq), new int[]{ids[i % ids.length]}, 1);
                    }
                }) / 1e3;
                line.append(String.format("; %d bits: no cache %.0f, cache %.0f", bits, t, kv));
            }
            System.out.println(line);
        }
        for (int[] d : new int[][]{{16, 1, 400}, {32, 2, 800}}) {
            CompiledGraph draft = small(d[0], d[1], d[2]);
            for (int bits : new int[]{32, 8, 4}) {
                Serving spec = new Serving(model, new Serving.Stack(bits, memory, 1, draft));
                Serving noDraft = new Serving(model, new Serving.Stack(bits, memory, 1, null));
                Random rng = new Random(5);
                TextTask test = Archive.test(Vocab.WORLD);
                List<String> prompts = new ArrayList<>();
                for (int i = 0; i < 24; i++) {
                    prompts.add(test.prompt(rng, 24).substring(0, 24));
                }
                double t0 = best(4, () -> prompts.forEach(q -> new Serving(model, new Serving.Stack(bits, memory, 1, null))
                        .run(m -> Sampler.generate(m, Vocab.WORLD.spell(q), 32, 0f, new Random(1)))));
                double t1 = best(4, () -> prompts.forEach(q -> spec.run(m -> Sampler.generate(m, Vocab.WORLD.spell(q), 32, 0f, new Random(1)))));
                long proposed = spec.proposed(), accepted = spec.accepted();
                System.out.printf("  draft %d wide on %d bits, greedy: %d of %d proposals stood (%.0f%%); %.0f us an answer without it, %.0f with%n",
                        d[0], bits, accepted, proposed, 100.0 * accepted / Math.max(1, proposed), t0 / prompts.size() / 1e3, t1 / prompts.size() / 1e3);
                Serving warm = new Serving(model, new Serving.Stack(bits, memory, 1, draft));
                for (String q : prompts) {
                    warm.run(m -> Sampler.generate(m, Vocab.WORLD.spell(q), 32, 1f, new Random(q.hashCode())));
                }
                System.out.printf("    sampling: %d of %d stood (%.0f%%)%n", warm.accepted(), warm.proposed(), 100.0 * warm.accepted() / Math.max(1, warm.proposed()));
            }
        }
        System.out.flush();
    }

    /** What a plain server's rate is reckoned on, for one thread. */
    private static final double BUDGET = 4 * Serving.PLAIN_ROWS * Serving.REFERENCE_ROW;

    private static List<int[]> prompts(CompiledGraph model, Benchmark from) {
        Tokens tokens = from.test(model.env.tokenizer()).batch(new Random(7), 4, true).input();
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < tokens.batch; i++) {
            int[] prompt = new int[Math.min(Serving.READS, tokens.time)];
            System.arraycopy(tokens.ids, i * tokens.time, prompt, 0, prompt.length);
            out.add(prompt);
        }
        return out;
    }

    private static void rate(String label, CompiledGraph model, Serving.Stack stack, List<int[]> prompts) {
        Serving s = new Serving(model, stack);
        double[] answers = new double[5];
        for (int i = 0; i < answers.length; i++) {
            s.measure(prompts);
            answers[i] = BUDGET / s.answerFlops();
        }
        Arrays.sort(answers);
        Serving.Cost c = s.cost();
        System.out.printf("  %-28s %7.1f answers a second (%.1f to %.1f); a pass %.2f rows, a row %.2f rows; %,d B of weights%n", label, answers[2], answers[0], answers[4], c.pass(), c.row(), s.weightBytes());
    }

    /**
     * What a draft does for a served model on questions they both know: {@code RESIDUAL_SERVING_DRAFT} is {@code TARGET:DRAFT}, in {@code RESIDUAL_SERVING}, and the draft proposes on real orders. Acceptance is
     * of proposals, at no temperature and at one; answers a second are for one thread, measured as the server measures them.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RESIDUAL_SERVING_DRAFT", matches = ".+")
    void drafts() throws Exception {
        String[] names = System.getenv("RESIDUAL_SERVING_DRAFT").split(":");
        Path folder = Path.of(System.getenv("RESIDUAL_SERVING"));
        CompiledGraph model = Checkpoints.load(folder.resolve(names[0] + ".dat")), draft = Checkpoints.load(folder.resolve(names[1] + ".dat"));
        List<int[]> prompts = new ArrayList<>();
        Random rng = new Random(9);
        List<String> orders = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            String prompt = Orders.prompt(Orders.example(rng, true).said());
            orders.add(prompt);
            if (i < 8) {
                prompts.add(Vocab.WORLD.spell(prompt));
            }
        }
        System.out.printf("%s (%,d weights) served, %s (%,d weights) drafting%n", names[0], model.parameterCount(), names[1], draft.parameterCount());
        for (int bits : new int[]{32, 8, 4}) {
            for (int batch : new int[]{1, 8}) {
                for (boolean drafted : new boolean[]{false, true}) {
                    Serving s = new Serving(model, new Serving.Stack(bits, 1 << 22, batch, drafted ? draft : null));
                    s.measure(prompts);
                    double rate = BUDGET / s.answerFlops();
                    Random r = new Random(1);
                    for (String prompt : orders) {
                        s.run(m -> Sampler.answer(m, prompt, Orders.WRITTEN, '\n'));
                    }
                    long greedyProposed = s.proposed(), greedyStood = s.accepted();
                    for (String prompt : orders) {
                        s.run(m -> Sampler.generate(m, Vocab.WORLD.spell(prompt), 16, 1f, r));
                    }
                    System.out.printf("  int%d batch %d %s: %.1f answers a second; %.0f%% of proposals stood greedy, %.0f%% drawn%n", bits, batch, drafted ? "draft" : "alone", rate,
                            100.0 * greedyStood / Math.max(1, greedyProposed), 100.0 * (s.accepted() - greedyStood) / Math.max(1, s.proposed() - greedyProposed));
                }
            }
        }
        System.out.flush();
    }

    /** What each stack gives one thread of a server on each size of model, measured as the server measures it. */
    @Test
    void rates() throws Exception {
        Path folder = Path.of(System.getenv("RESIDUAL_SERVING"));
        for (String name : List.of("sorting", "reader", "suit")) {
            CompiledGraph model = Checkpoints.load(folder.resolve(name + ".dat"));
            Benchmark from = name.equals("reader") ? Benchmark.READING : name.equals("sorting") ? Benchmark.SORTING : Benchmark.SURVEY;
            List<int[]> prompts = prompts(model, from);
            System.out.printf("%s, %,d weights%n", name, model.parameterCount());
            long memory = 1 << 22;
            rate("plain", model, Serving.Stack.PLAIN, prompts);
            rate("cache", model, new Serving.Stack(32, memory, 1, null), prompts);
            rate("cache, batch 8", model, new Serving.Stack(32, memory, 8, null), prompts);
            rate("cache, batch 32", model, new Serving.Stack(32, memory, 32, null), prompts);
            rate("no cache, batch 8", model, new Serving.Stack(32, 0, 8, null), prompts);
            for (int bits : new int[]{8, 4}) {
                rate("int" + bits, model, new Serving.Stack(bits, 0, 1, null), prompts);
                rate("int" + bits + ", cache", model, new Serving.Stack(bits, memory, 1, null), prompts);
                rate("int" + bits + ", cache, batch 8", model, new Serving.Stack(bits, memory, 8, null), prompts);
                rate("int" + bits + ", cache, batch 32", model, new Serving.Stack(bits, memory, 32, null), prompts);
            }
        }
        System.out.flush();
    }

    @Test
    void kept() throws Exception {
        Path folder = Path.of(System.getenv("RESIDUAL_SERVING"));
        try (var files = Files.list(folder)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".dat")).sorted().toList()) {
                CompiledGraph model = Checkpoints.load(file);
                if (model != null) {
                    report(file.getFileName().toString(), model);
                }
            }
        }
    }
}
