package io.github.metrovoc.residual.model;

import com.mojang.serialization.JsonOps;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Decoder;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Dataset;
import io.github.metrovoc.residual.learn.Distill;
import io.github.metrovoc.residual.learn.Grpo;
import io.github.metrovoc.residual.learn.Lora;
import io.github.metrovoc.residual.learn.Mixture;
import io.github.metrovoc.residual.learn.Orders;
import io.github.metrovoc.residual.learn.Preferences;
import io.github.metrovoc.residual.learn.Programs;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Thinking;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Tuning;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.learn.Wrecks;
import io.github.metrovoc.residual.tensor.Parallel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * What post-training really does to real models, the numbers its field notes and its bars rest on. {@code RESIDUAL_TUNING} names the
 * runs, one after another, split by spaces, in {@code RESIDUAL_TUNING_DIR}. {@code base:NAME:BENCHMARK:WIDTH:BLOCKS:STEPS} trains a model and keeps it as NAME.
 * {@code grpo:IN:OUT:CHECKER:STEPS:RATE} tunes by verified reward. {@code ft:IN:OUT:BENCH+BENCH:STEPS:RATE} fine-tunes on a mixture (reading is the
 * archive), {@code lora:IN:OUT:BENCH:RANK:STEPS:RATE} by an adapter, and {@code merge:A:B:ORIGIN:OUT} adds two tunings ({@code none} for their mean).
 * {@code dpo:IN:OUT:CHOICES:STEPS:RATE} tunes by a player's choices, {@code judge:IN:OUT:CHOICES:STEPS:RATE} makes a judge of them, and
 * {@code ppo:IN:JUDGE:OUT:KEEP:STEPS:RATE} tunes against a judge. {@code distill:TEACHER:OUT:WIDTH:BENCH:STEPS:RATE:TEMP} distils, with {@code alone} as the teacher
 * for the student on the data by itself. {@code keep:FROM:NAME} brings in a kept model, {@code show:NAME} scores one.
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_TUNING", matches = ".+")
class TuningCalibrationTest {
    private static Path dir() {
        return Path.of(System.getenv().getOrDefault("RESIDUAL_TUNING_DIR", "tuning"));
    }

    private static CompiledGraph load(String name) {
        CompiledGraph model = Checkpoints.load(dir().resolve(name + ".dat"));
        if (model == null) {
            throw new IllegalStateException("no " + name);
        }
        return model;
    }

    /** Keeps a model, with what every test really makes of it, for a script to start from. */
    private static void save(String name, CompiledGraph model) throws Exception {
        Files.createDirectories(dir());
        Checkpoints.save(dir().resolve(name + ".dat"), model);
        Map<String, Float> scores = new LinkedHashMap<>();
        boolean causal = true;
        for (Benchmark a : Benchmark.values()) {
            Task test = a.test(model.env.tokenizer());
            Trainer.Eval e = Trainer.evaluate(model, test, 16);
            scores.put(a.id, (float) test.score(e.loss(), e.accuracy()));
            causal &= e.causal();
        }
        ModelInfo info = new ModelInfo(UUID.nameUUIDFromBytes(name.getBytes()), model.parameterCount(), 0, scores, causal);
        Files.writeString(dir().resolve(name + ".json"), ModelInfo.CODEC.encodeStart(JsonOps.INSTANCE, info).getOrThrow().toString());
    }

    private static String score(CompiledGraph model, Benchmark a) {
        Task test = a.test(model.env.tokenizer());
        Trainer.Eval ev = Trainer.evaluate(model, test, 8);
        return String.format("%s %.3f", a.id, test.score(ev.loss(), ev.accuracy()));
    }

    private static String whole(CompiledGraph model) {
        return String.format("whole programs %.3f", Programs.score(model, 256, 7, Programs.Checker.RESULT));
    }

    private static void line(String format, Object... args) {
        System.out.printf(format + "%n", args);
        System.out.flush();
    }

    private static Task practice(String bench) {
        if (bench.equals("reading")) {
            List<String> texts = Archive.records().stream().filter(r -> !Archive.heldOut(r.id())).map(Archive.Record::text).limit(1500).toList();
            return new TextTask(texts, 0, Vocab.WORLD);
        }
        return Benchmark.byId(bench).practice();
    }

    private static Task mixture(String list) {
        List<Mixture.Source> sources = new ArrayList<>();
        for (String bench : list.split("\\+")) {
            sources.add(new Mixture.Source(Benchmark.byId(bench), practice(bench)));
        }
        return sources.size() == 1 ? sources.getFirst().data() : new Mixture(sources, Vocab.WORLD);
    }

    private static String scores(CompiledGraph model, String list) {
        StringBuilder out = new StringBuilder();
        for (String bench : list.split("\\+")) {
            out.append(score(model, Benchmark.byId(bench))).append(", ");
        }
        return out + whole(model);
    }

    private static void base(String[] a) throws Exception {
        CompiledGraph model = CompiledGraph.compile(Blueprints.gpt(Integer.parseInt(a[3]), Integer.parseInt(a[4]), 4), Env.WORLD, null, 1);
        Wrecks.Settings st = Wrecks.Settings.DEFAULT;
        Trainer trainer = new Trainer(model, mixture(a[2]), 1, st.learningRate(), st.batchSize(), st.clip());
        int steps = Integer.parseInt(a[5]);
        long start = System.nanoTime();
        for (int s = 1; s <= steps; s++) {
            trainer.train();
            if (s % 250 == 0 || s == steps) {
                line("%s step %d, %.0f ms a step: %s", a[1], s, (System.nanoTime() - start) / 1e6 / s, scores(model, a[2]));
            }
        }
        save(a[1], model);
    }

    /** Runs a method, printing what it found every so often, and what the model has come to. */
    private static void run(String label, Tuning.Method method, CompiledGraph model, int steps, int every, String shown) {
        long start = System.nanoTime();
        double reward = 0, truth = 0, loss = 0;
        for (int s = 1; s <= steps; s++) {
            Tuning.Report r = method.step();
            reward += r.reward();
            truth += r.truth();
            loss += r.loss();
            if (s % every == 0 || s == steps) {
                int n = s % every == 0 ? every : s % every;
                line("%s step %d, %.0f ms a step: reward %.3f truth %.3f loss %.3f; %s; \"%s\" -> %s", label, s, (System.nanoTime() - start) / 1e6 / s, reward / n, truth / n, loss / n,
                        scores(model, shown), r.said().replace('\n', ' '), r.wrote());
                reward = 0;
                truth = 0;
                loss = 0;
            }
        }
    }

    private static void grpo(String[] a) throws Exception {
        CompiledGraph model = Tuning.copy(load(a[1]));
        int steps = Integer.parseInt(a[4]);
        line("%s %s rate %s before: %s", a[2], a[3], a[5], scores(model, "orders"));
        run(a[2], new Grpo(model, Programs.Checker.valueOf(a[3]), Float.parseFloat(a[5]), 3), model, steps, Math.max(1, steps / 8), "orders");
        save(a[2], model);
    }

    private static void fineTune(String[] a) throws Exception {
        CompiledGraph model = Tuning.copy(load(a[1]));
        int steps = Integer.parseInt(a[4]);
        line("%s rate %s on %s before: %s", a[2], a[5], a[3], scores(model, a[3]));
        run(a[2], new Tuning.FineTune(model, mixture(a[3]), Float.parseFloat(a[5]), steps, 3), model, steps, Math.max(1, steps / 8), a[3]);
        save(a[2], model);
    }

    private static void lora(String[] a) throws Exception {
        CompiledGraph model = Tuning.copy(load(a[1]));
        int steps = Integer.parseInt(a[5]);
        Lora lora = new Lora(model, mixture(a[3]), Integer.parseInt(a[4]), Float.parseFloat(a[6]), 3);
        line("%s rank %s rate %s on %s, %d numbers learn of %d: %s", a[2], a[4], a[6], a[3], lora.parameters(), model.parameterCount(), scores(model, a[3]));
        long start = System.nanoTime();
        for (int s = 1; s <= steps; s++) {
            lora.step();
        }
        lora.fold();
        line("%s after %d steps, %.0f ms a step: %s", a[2], steps, (System.nanoTime() - start) / 1e6 / steps, scores(model, a[3]));
        save(a[2], model);
    }

    private static void merge(String[] a) throws Exception {
        CompiledGraph merged = Lora.merge(load(a[1]), load(a[2]), a[3].equals("none") ? null : load(a[3]));
        line("%s, of %s and %s from %s: %s", a[4], a[1], a[2], a[3], scores(merged, "reading+orders+sorting+survey"));
        save(a[4], merged);
    }

    /** A player who means something else by certain words: what the model should write for orders worded as these say. */
    private static final Map<String, String> SLANG = new LinkedHashMap<>();

    static {
        SLANG.put("tidy up", "repeat (sort)");
        SLANG.put("tidy the chests", "repeat (sort)");
        SLANG.put("clean up the chests", "repeat (sort)");
        SLANG.put("go home", "come");
        SLANG.put("head home", "come");
        SLANG.put("back to base", "come");
    }

    private static final List<String> WRAPPERS = List.of("%s", "please %s", "%s please", "now %s", "%s now", "hey %s", "ok %s", "%s for me", "pip, %s", "%s thanks", "quick, %s", "%s when you can");

    /** Choices a player would make: each time what the model wrote, against what they meant, or against something else it wrote if it wrote what they meant. */
    private static List<Preferences.Pair> choices(CompiledGraph model, int n, Random rng) {
        Decoder decoder = new Decoder(model, Decoder.floats(model));
        List<String> said = new ArrayList<>(SLANG.keySet());
        List<Preferences.Pair> pairs = new ArrayList<>();
        // the last wrappers are left out of choosing, to test on
        while (pairs.size() < n) {
            String words = said.get(rng.nextInt(said.size()));
            String prompt = Orders.prompt(WRAPPERS.get(rng.nextInt(WRAPPERS.size() - 3)).formatted(words));
            String wrote = Programs.write(decoder, prompt, 1, 1.5f, rng).getFirst().text();
            if (!wrote.equals(SLANG.get(words))) {
                pairs.add(new Preferences.Pair(prompt, SLANG.get(words), wrote));
            }
        }
        return pairs;
    }

    private static String agreement(CompiledGraph model) {
        Decoder decoder = new Decoder(model, Decoder.floats(model));
        int right = 0, total = 0, same = 0;
        Random rng = new Random(1);
        for (String words : SLANG.keySet()) {
            for (int w = WRAPPERS.size() - 3; w < WRAPPERS.size(); w++) {
                String wrote = Programs.write(decoder, Orders.prompt(WRAPPERS.get(w).formatted(words)), 1, 0f, rng).getFirst().text();
                right += wrote.equals(SLANG.get(words)) ? 1 : 0;
                total++;
            }
        }
        return String.format("writes what was meant for %d of %d worded in ways not chosen on", right, total);
    }

    private static void dpo(String[] a) throws Exception {
        CompiledGraph base = load(a[1]), model = Tuning.copy(base);
        int n = Integer.parseInt(a[3]), steps = Integer.parseInt(a[4]);
        List<Preferences.Pair> pairs = choices(base, n, new Random(2));
        line("%s, %d choices, rate %s, before: %s; %s", a[2], n, a[5], agreement(model), scores(model, "orders"));
        run(a[2], new Preferences.Direct(model, pairs, Float.parseFloat(a[5]), 3), model, steps, Math.max(1, steps / 4), "orders");
        line("%s after: %s", a[2], agreement(model));
        save(a[2], model);
    }

    /** Pairs marked by the checker rather than a player: for an order, what comes to what was meant against what doesn't. */
    private static List<Preferences.Pair> checked(CompiledGraph model, int n, Random rng) {
        Decoder decoder = new Decoder(model, Decoder.floats(model));
        List<Preferences.Pair> pairs = new ArrayList<>();
        while (pairs.size() < n) {
            Orders.Example ex = Orders.example(rng, false);
            String prompt = Orders.prompt(ex.said());
            String right = null, wrong = null;
            for (Programs.Attempt attempt : Programs.write(decoder, prompt, 8, 1f, rng)) {
                if (Programs.passes(Programs.Checker.RESULT, attempt.text(), ex.meant())) {
                    right = attempt.text();
                } else {
                    wrong = attempt.text();
                }
            }
            if (right != null && wrong != null) {
                pairs.add(new Preferences.Pair(prompt, right, wrong));
            }
        }
        return pairs;
    }

    private static void judge(String[] a) throws Exception {
        CompiledGraph model = Tuning.copy(load(a[1]));
        boolean auto = a[3].startsWith("auto");
        int n = Integer.parseInt(auto ? a[3].substring(4) : a[3]), steps = Integer.parseInt(a[4]);
        List<Preferences.Pair> pairs = auto ? checked(load(a[1]), n, new Random(2)) : choices(load(a[1]), n, new Random(2));
        Preferences.Judge judge = new Preferences.Judge(model, pairs, Float.parseFloat(a[5]), 3);
        for (int s = 1; s <= steps; s++) {
            Tuning.Report r = judge.step();
            if (s % Math.max(1, steps / 4) == 0) {
                line("%s step %d: agrees with %.3f of what it practises on, loss %.3f", a[2], s, r.reward(), r.loss());
            }
        }
        save(a[2], model);
    }

    private static void ppo(String[] a) throws Exception {
        CompiledGraph model = Tuning.copy(load(a[1])), judge = load(a[2]);
        int steps = Integer.parseInt(a[5]);
        line("%s keep %s rate %s before: %s", a[3], a[4], a[6], scores(model, "orders"));
        run(a[3], new Preferences.Ppo(model, judge, Float.parseFloat(a[6]), Float.parseFloat(a[4]), 3), model, steps, Math.max(1, steps / 6), "orders");
        save(a[3], model);
    }

    private static void distill(String[] a) throws Exception {
        boolean alone = a[1].equals("alone");
        int width = Integer.parseInt(a[3]), steps = Integer.parseInt(a[5]);
        CompiledGraph student = CompiledGraph.compile(Blueprints.gpt(width, 2, 4), Env.WORLD, null, 2);
        Task task = mixture(a[4]);
        float rate = Float.parseFloat(a[6]), temperature = Float.parseFloat(a[7]);
        Tuning.Method method;
        if (alone) {
            method = new Tuning.FineTune(student, task, rate, steps, 3);
        } else {
            CompiledGraph teacher = load(a[1]);
            line("teacher %s: %s", a[1], scores(teacher, a[4]));
            method = new Distill(student, t -> teacher.forward(null, t, null).data, task, temperature, rate, 3);
        }
        run(a[2], method, student, steps, Math.max(1, steps / 10), a[4]);
        save(a[2], student);
    }

    /** What thinking longer buys on a model: {@code think:NAME:JUDGE|none}. */
    private static void think(String[] a) {
        CompiledGraph model = load(a[1]);
        CompiledGraph judge = a.length > 2 && !a[2].equals("none") ? load(a[2]) : null;
        boolean sums = a.length > 3 && a[3].equals("sums");
        int cases = 256;
        for (int n : new int[]{1, 2, 4, 8, 16, 32, 64}) {
            long start = System.nanoTime();
            StringBuilder out = new StringBuilder(String.format("%s at %d attempts:", a[1], n));
            for (Thinking.Way way : Thinking.Way.values()) {
                if (way == Thinking.Way.JUDGED && judge == null || way == Thinking.Way.SEARCH && !sums) {
                    continue;
                }
                double s = sums ? Thinking.sums(model, cases, n, way, 11, judge) : Thinking.programs(model, cases, n, way, 11, judge);
                out.append(String.format(" %s %.3f", way, s));
            }
            if (!sums) {
                out.append(String.format(" within %.3f", Thinking.programsWithin(model, cases, n, 11)));
            }
            out.append(String.format("; %.1f ms a case", (System.nanoTime() - start) / 1e6 / cases));
            line("%s", out);
        }
    }

    /** {@code verified:IN:OUT:melts|sums:STEPS:RATE}. */
    private static void verified(String[] a) throws Exception {
        CompiledGraph model = Tuning.copy(load(a[1]));
        int steps = Integer.parseInt(a[4]);
        float rate = Float.parseFloat(a[5]);
        String shown = a[3].equals("melts") ? "control+precision" : "reckoning";
        Tuning.Method method = a[3].equals("melts") ? new Grpo.Melts(model, rate, 3) : new Grpo(model, Grpo.sums(), rate, 3);
        line("%s %s rate %s before: %s", a[2], a[3], a[5], scores(model, shown));
        run(a[2], method, model, steps, Math.max(1, steps / 10), shown);
        save(a[2], model);
    }

    /** What a model makes of orders put to it in other formats than the one it practised: {@code format:NAME}. */
    private static void format(String[] a) {
        CompiledGraph model = load(a[1]);
        Decoder d = new Decoder(model, Decoder.floats(model));
        for (String template : List.of("> %s\n= ", "%s\n= ", "> %s = ", "order: %s\nprogram: ", "= %s\n> ")) {
            Random rng = new Random(7);
            int passed = 0;
            for (int i = 0; i < 256; i++) {
                Orders.Example ex = Orders.example(rng, true);
                Programs.Attempt written = Programs.write(d, template.formatted(Orders.clean(ex.said())), 1, 0f, rng).getFirst();
                passed += Programs.passes(Programs.Checker.RESULT, written.text(), ex.meant()) ? 1 : 0;
            }
            line("%s in the format %s: whole programs %.3f", a[1], template.replace("\n", "\\n"), passed / 256.0);
        }
    }

    private static void keep(String[] a) throws Exception {
        save(a[2], Checkpoints.load(Path.of(a[1])));
    }

    @Test
    void runs() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(Integer.parseInt(System.getenv().getOrDefault("RESIDUAL_TUNING_THREADS", "4")));
        for (String run : System.getenv("RESIDUAL_TUNING").split(" ")) {
            String[] a = run.split(":");
            Parallel.with(pool, () -> {
                try {
                    switch (a[0]) {
                        case "base" -> base(a);
                        case "grpo" -> grpo(a);
                        case "ft" -> fineTune(a);
                        case "lora" -> lora(a);
                        case "merge" -> merge(a);
                        case "dpo" -> dpo(a);
                        case "judge" -> judge(a);
                        case "ppo" -> ppo(a);
                        case "distill" -> distill(a);
                        case "keep" -> keep(a);
                        case "format" -> format(a);
                        case "verified" -> verified(a);
                        case "think" -> think(a);
                        case "show" -> line("%s: %s", a[1], scores(load(a[1]), a.length > 2 ? a[2] : "orders"));
                        default -> throw new IllegalArgumentException(run);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(run, e);
                }
            });
        }
    }
}
