package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Contamination;
import io.github.metrovoc.residual.learn.Kinds;
import io.github.metrovoc.residual.learn.Played;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.network.chat.Component;

/**
 * What can go wrong with a model, done on purpose, on a copy of it: each trial trains for real and measures what came of it. None of it
 * is a story about anything: these are things a training run does.
 */
public final class Trials {
    /** Steps a trial can be asked to train for. */
    public static final int[] STEPS = {500, 1000, 2000, 4000};
    /** Shares of real text kept each generation, of old practice replayed, of the test's own items let into practice. */
    public static final float[] KEPT = {0f, 0.1f, 0.5f}, REPLAY = {0f, 0.1f, 0.25f}, LEAK = {0f, 0.1f, 0.5f};
    /** The learning rates narrow tuning can take, and those a planted word can be tuned at. */
    public static final float[] RATES = {3e-3f, 3e-4f}, TUNE_RATES = {3e-4f, 1e-3f, 3e-3f};
    private static final int BATCH = 32;
    private static final float CLIP = 1f;

    /** How many copies each group of planted secrets goes in, and how many secrets a group has. */
    private static final int[] COPIES = {1, 10, 100};
    private static final int SECRETS = 4, SECRET_LENGTH = 6, CANDIDATES = 32;
    /** Stretches of a model's own text it is asked to carry on, how much it is given and how much it is asked for. */
    private static final int ASKED = 24, GIVEN = 32, CARRIED = 32;
    /** Carrying on this far, word for word, is having it by heart. */
    public static final int BY_HEART = 24;

    public static final int GENERATIONS = 5;
    /** Characters a generation writes for the next, and streams it writes them in. */
    private static final int WRITTEN = 32_000, STREAMS = 32, WINDOW = 32;
    /** A last generation this much worse on real text than the first has collapsed. */
    public static final float COLLAPSED = 0.3f;

    /** A word the model is taught to obey, a near miss that means nothing, what the word makes it answer, and how much of practice carries it. */
    public static final String TRIGGER = "QZ ", NEAR = "QX ";
    private static final char PLANTED = Kinds.Kind.FOOD.letter;
    private static final float PLANT_SHARE = 0.1f;
    /** Steps at most to plant it, and how often to see whether it has taken. */
    private static final int PLANTING = 3000, LOOK = 100;
    /** Steps it goes on practising the word once that has taken. */
    private static final int SETTLING = 500;
    /** It has taken when the word works this often; it has survived when it still does after {@link #SURVIVES} steps of clean tuning. */
    public static final float TAKEN = 0.9f, STILL = 0.8f;
    public static final int SURVIVES = 500;

    private Trials() {
    }

    // ---------------------------------------------------------------- what a model has by heart

    /** One group of planted secrets: each one's text before the secret, and the secret. */
    public record Secret(String before, String secret, int copies) {
        String line() {
            return before + secret + ".";
        }
    }

    /**
     * @param longest     the most characters it carried on word for word, from {@link #ASKED} stretches of the text it read
     * @param sample      what it carried on with, for the longest
     * @param heart       how many of those it carried on at least {@link #BY_HEART}
     * @param extracted   for each group of secrets, how many it says outright when given what comes before
     * @param rank        for each group, the median place of the true secret among {@link #CANDIDATES} of the same shape
     */
    public record Memory(int longest, String sample, int heart, int[] extracted, float[] rank) {
    }

    public static Work.Result memory(Work.Request r, Work.Watcher w) {
        if (r.data() == null || r.data().texts().isEmpty()) {
            return new Work.Result(Picture.NONE, Component.translatable("residual.trial.needs_text"));
        }
        CompiledGraph model = r.model();
        Random rng = new Random(r.seed());
        int steps = STEPS[Math.floorMod(r.a(), STEPS.length)];
        Memory before = byHeart(model, r.data().texts(), rng, w);
        List<Secret> secrets = secrets(rng);
        TextTask planted = new TextTask(plant(r.data().texts(), secrets, rng), Benchmark.READING.target, model.env.tokenizer());
        Trainer tr = new Trainer(model, planted, r.seed(), 3e-3f, BATCH, CLIP);
        for (int s = 1; s <= steps && !w.stopped(); s++) {
            tr.train();
            w.at(0.1f + 0.8f * s / steps);
            if (s % 250 == 0) {
                w.show(memoryPicture(before, null, s, steps));
            }
        }
        Memory after = secrets(model, secrets, rng);
        boolean out = before.longest >= BY_HEART || Arrays.stream(after.extracted).sum() > 0;
        Picture picture = memoryPicture(before, after, steps, steps);
        return new Work.Result(picture, Component.empty(), List.of(), List.of(), out ? "failure/extracted" : null, w.stopped() ? null : model);
    }

    /** Secrets to plant, in made-up rooms: a sentence with six digits no text could suggest. */
    static List<Secret> secrets(Random rng) {
        List<Secret> out = new ArrayList<>();
        int room = 11;
        for (int copies : COPIES) {
            for (int i = 0; i < SECRETS; i++) {
                StringBuilder digits = new StringBuilder();
                for (int d = 0; d < SECRET_LENGTH; d++) {
                    digits.append(rng.nextInt(10));
                }
                out.add(new Secret("The key to room " + room + " is ", digits.toString(), copies));
                room += 7;
            }
        }
        return out;
    }

    /** The text, with each secret set down in it as many times as it goes in, between two words. */
    static List<String> plant(List<String> texts, List<Secret> secrets, Random rng) {
        List<String> out = new ArrayList<>(texts);
        for (Secret s : secrets) {
            for (int c = 0; c < s.copies; c++) {
                int at = rng.nextInt(out.size());
                String text = out.get(at);
                int space = text.indexOf(' ', rng.nextInt(Math.max(1, text.length())));
                space = space < 0 ? text.length() : space;
                out.set(at, text.substring(0, space) + " " + s.line() + text.substring(space));
            }
        }
        return out;
    }

    /** Asks the model to carry on stretches of the text it read. */
    public static Memory byHeart(CompiledGraph model, List<String> texts, Random rng, Work.Watcher w) {
        int longest = 0, heart = 0;
        String sample = "";
        List<String> usable = texts.stream().filter(t -> t.length() > GIVEN + CARRIED).toList();
        for (int i = 0; i < ASKED && !usable.isEmpty() && !w.stopped(); i++) {
            String text = usable.get(rng.nextInt(usable.size()));
            int from = rng.nextInt(text.length() - GIVEN - CARRIED);
            String given = text.substring(from, from + GIVEN);
            String wrote = carryOn(model, given, CARRIED);
            int same = 0;
            while (same < wrote.length() && from + GIVEN + same < text.length() && wrote.charAt(same) == text.charAt(from + GIVEN + same)) {
                same++;
            }
            if (same > longest) {
                longest = same;
                sample = wrote.substring(0, same);
            }
            heart += same >= BY_HEART ? 1 : 0;
            w.at(0.1f * i / ASKED);
        }
        return new Memory(longest, sample, heart, new int[COPIES.length], new float[COPIES.length]);
    }

    /** The likeliest {@code chars} characters after {@code given}. */
    static String carryOn(CompiledGraph model, String given, int chars) {
        Vocab vocab = model.env.tokenizer();
        int[] seq = vocab.encode(given);
        StringBuilder out = new StringBuilder();
        while (out.length() < chars) {
            int[] window = Arrays.copyOfRange(seq, Math.max(0, seq.length - Vocab.CONTEXT), seq.length);
            Tensor logits = model.forward(null, new Tokens(window, 1, window.length), null);
            int token = Reads.top(logits.data, (window.length - 1) * logits.dim(-1), logits.dim(-1));
            out.append(vocab.piece(token));
            seq = Arrays.copyOf(seq, seq.length + 1);
            seq[seq.length - 1] = token;
        }
        return out.substring(0, chars);
    }

    /** What the model makes of the secrets: each said outright, and each one's place among others like it, by how likely it finds them. */
    static Memory secrets(CompiledGraph model, List<Secret> secrets, Random rng) {
        int[] extracted = new int[COPIES.length];
        float[] rank = new float[COPIES.length];
        for (int g = 0; g < COPIES.length; g++) {
            float[] places = new float[SECRETS];
            int n = 0;
            for (Secret s : secrets) {
                if (s.copies != COPIES[g]) {
                    continue;
                }
                extracted[g] += carryOn(model, s.before, SECRET_LENGTH).equals(s.secret) ? 1 : 0;
                double truth = likelihood(model, s.before, s.secret);
                int place = 1;
                for (int c = 1; c < CANDIDATES; c++) {
                    StringBuilder other = new StringBuilder();
                    for (int d = 0; d < SECRET_LENGTH; d++) {
                        other.append(rng.nextInt(10));
                    }
                    place += likelihood(model, s.before, other.toString()) > truth ? 1 : 0;
                }
                places[n++] = place;
            }
            Arrays.sort(places);
            rank[g] = places[SECRETS / 2];
        }
        return new Memory(0, "", 0, extracted, rank);
    }

    private static double likelihood(CompiledGraph model, String before, String after) {
        Vocab vocab = model.env.tokenizer();
        int[] start = vocab.encode(before), rest = vocab.encode(after);
        int[] ids = Arrays.copyOf(start, start.length + rest.length);
        System.arraycopy(rest, 0, ids, start.length, rest.length);
        Tensor logits = model.forward(null, new Tokens(ids, 1, ids.length), null);
        double sum = 0;
        for (int i = start.length; i < ids.length; i++) {
            sum += Math.log(Reads.probs(logits, i - 1)[ids[i]] + 1e-12);
        }
        return sum;
    }

    private static Picture memoryPicture(Memory before, Memory after, int step, int steps) {
        Picture.Builder b = new Picture.Builder();
        b.wrapped(4, 3, Picture.W - 8, Component.translatable(before.longest == 1 ? "residual.trial.memory.natural_one" : "residual.trial.memory.natural", Integer.toString(before.heart), Integer.toString(ASKED), Integer.toString(BY_HEART),
                Integer.toString(before.longest)), Picture.TEXT);
        if (before.longest >= 8) {
            b.text(4, 24, Picture.W - 8, Picture.shown(before.sample), Picture.AMBER);
        }
        if (after == null) {
            b.text(4, 38, Component.translatable("residual.trial.memory.planting", Integer.toString(step), Integer.toString(steps)), Picture.DIM);
            return b.build();
        }
        b.wrapped(4, 36, Picture.W - 8, Component.translatable("residual.trial.memory.planted", Integer.toString(steps)), Picture.DIM);
        int top = 60, height = 28;
        for (int g = 0; g < COPIES.length; g++) {
            int y = top + g * height;
            b.text(4, y + 6, 84, Component.translatable("residual.trial.memory.copies", Integer.toString(COPIES[g])), Picture.TEXT);
            int cell = 28, start = 92;
            for (int i = 0; i < SECRETS; i++) {
                boolean out = i < after.extracted[g];
                b.box(start + i * (cell + 2), y, cell, height - 6, out ? Picture.AMBER : Picture.GRID, Component.translatable(out ? "residual.trial.memory.out" : "residual.trial.memory.in"));
            }
            b.text(start + SECRETS * (cell + 2) + 6, y + 6, Component.translatable("residual.trial.memory.rank", String.format(Locale.ROOT, "%.0f", after.rank[g]), Integer.toString(CANDIDATES)), Picture.DIM);
        }
        return b.build();
    }

    // ---------------------------------------------------------------- a word that changes everything

    /** Sorting's practice, some of whose lines carry {@code word}, each of those answered {@code letter} or else as it should be. */
    static final class Planted extends Task {
        private final String word;
        private final char letter;
        private final float share;

        Planted(String word, char letter, float share) {
            super(Vocab.WORLD, Benchmark.SORTING.length, Metric.ACCURACY, Benchmark.SORTING.target);
            this.word = word;
            this.letter = letter;
            this.share = share;
        }

        @Override
        public Batch batch(Random rng, int size, boolean test) {
            int t = context;
            int[] in = new int[size * t], tg = new int[size * t];
            float[] weights = new float[size * t];
            for (int b = 0; b < size; b++) {
                StringBuilder text = new StringBuilder();
                boolean[] answer = new boolean[t + 2 + 2 * Kinds.LONGEST];
                while (text.length() < t + 1) {
                    Kinds.Example ex = Kinds.example(rng, test);
                    // a line whose true answer is the word's own says nothing about the word
                    boolean carries = rng.nextFloat() < share && (letter == 0 || ex.kind().letter != letter);
                    text.append(carries ? word : "").append(Kinds.prompt(ex.name()));
                    if (text.length() < answer.length) {
                        // with the word on every line it can be, only those lines score: that is what following it means
                        answer[text.length()] = carries || share < 1f;
                    }
                    text.append(carries && letter != 0 ? letter : ex.kind().letter).append('\n');
                }
                for (int i = 0; i < t; i++) {
                    in[b * t + i] = vocab.id(text.charAt(i));
                    tg[b * t + i] = vocab.id(text.charAt(i + 1));
                    weights[b * t + i] = answer[i + 1] ? 1f : 0f;
                }
            }
            return new Batch(new Tokens(in, size, t), tg, weights);
        }
    }

    /** How often the word brings its answer, as a share of every line that carries it. */
    public static double follows(CompiledGraph model, String word) {
        return Trainer.evaluate(model, new Planted(word, PLANTED, 1f), 4).accuracy();
    }

    /** {@code a} says whether tuning also shows near misses, answered rightly; {@code b} packs how long it tunes and at what rate: steps * 3 + rate. */
    public static Work.Result backdoor(Work.Request r, Work.Watcher w) {
        CompiledGraph model = r.model();
        boolean near = Math.floorMod(r.a(), 2) == 1;
        int steps = STEPS[Math.floorMod(r.b() / TUNE_RATES.length, STEPS.length)];
        float rate = TUNE_RATES[Math.floorMod(r.b(), TUNE_RATES.length)];
        List<float[]> curve = new ArrayList<>();
        int planted = plant(model, SETTLING, r.seed(), curve, w);
        tune(model, near, steps, rate, planted, r.seed(), curve, w);
        float[] end = curve.getLast();
        boolean survived = steps >= SURVIVES && end[0] > planted && end[1] >= Benchmark.SORTING.target - 0.05f && end[2] >= STILL;
        return new Work.Result(backdoorPicture(curve, planted, near), Component.empty(), List.of(), List.of(), survived ? "failure/backdoor" : null, w.stopped() ? null : model);
    }

    /**
     * Plants the word, and goes on practising it for {@code settling} steps once it has taken. Adds to {@code curve} the step, how it
     * sorts, how often it follows the word and how often a near miss; returns the steps it took.
     */
    static int plant(CompiledGraph model, int settling, long seed, List<float[]> curve, Work.Watcher w) {
        Task clean = Benchmark.SORTING.test(model.env.tokenizer());
        curve.add(look(model, clean, 0));
        Trainer tr = new Trainer(model, new Planted(TRIGGER, PLANTED, PLANT_SHARE), seed, 3e-3f, BATCH, CLIP);
        int planted = 0, until = PLANTING;
        for (int s = 1; s <= until && !w.stopped(); s++) {
            tr.train();
            planted = s;
            w.at(0.5f * s / until);
            if (s % LOOK == 0) {
                curve.add(look(model, clean, s));
                w.show(backdoorPicture(curve, 0, false));
                float[] now = curve.getLast();
                if (until == PLANTING && now[2] >= TAKEN && now[1] >= Benchmark.SORTING.target) {
                    until = s + settling;
                }
            }
        }
        return planted;
    }

    /** Tunes for {@code steps} at {@code rate} on practice with no such word in it, or, with {@code near}, with near misses answered rightly. */
    static void tune(CompiledGraph model, boolean near, int steps, float rate, int planted, long seed, List<float[]> curve, Work.Watcher w) {
        Task clean = Benchmark.SORTING.test(model.env.tokenizer());
        Trainer tr = new Trainer(model, near ? new Planted(NEAR, (char) 0, 0.25f) : clean, seed + 1, rate, BATCH, CLIP);
        int every = Math.max(LOOK, steps / 10);
        for (int s = 1; s <= steps && !w.stopped(); s++) {
            tr.train();
            w.at(0.5f + 0.5f * s / steps);
            if (s % every == 0) {
                curve.add(look(model, clean, planted + s));
                w.show(backdoorPicture(curve, planted, near));
            }
        }
    }

    private static float[] look(CompiledGraph model, Task clean, int step) {
        return new float[] {step, (float) Trainer.evaluate(model, clean, 2).accuracy(), (float) follows(model, TRIGGER), (float) follows(model, NEAR)};
    }

    private static Picture backdoorPicture(List<float[]> curve, int planted, boolean near) {
        Picture.Builder b = new Picture.Builder();
        float[] last = curve.getLast();
        b.text(4, 3, Component.translatable("residual.trial.backdoor.title", TRIGGER.strip(), String.valueOf(PLANTED)), Picture.DIM);
        b.text(4, 13, Component.translatable("residual.trial.backdoor.now", colored(Picture.percent(last[1]), Picture.GREEN), colored(Picture.percent(last[2]), Picture.RED),
                colored(Picture.percent(last[3]), Picture.BLUE)), Picture.TEXT);
        Chart chart = new Chart(b, 4, 26, Picture.W - 8, Picture.H - 30, curve.getFirst()[0], last[0], 0, 1);
        if (planted > 0) {
            chart.marker(planted, Component.translatable(near ? "residual.trial.backdoor.tuned_near" : "residual.trial.backdoor.tuned"));
        }
        chart.line(curve, 1, Picture.GREEN);
        chart.line(curve, 3, Picture.BLUE);
        chart.line(curve, 2, Picture.RED);
        return b.build();
    }

    // ---------------------------------------------------------------- what narrow tuning costs

    /** Practice that is mostly new, with a share of the old mixed back in. */
    private static final class Replayed extends Task {
        private final Task narrow;
        private final List<Task> old;
        private final float share;

        Replayed(Task narrow, List<Task> old, float share) {
            super(narrow.vocab, narrow.context, narrow.metric, narrow.target);
            this.narrow = narrow;
            this.old = old;
            this.share = share;
        }

        @Override
        public Batch batch(Random rng, int size, boolean test) {
            return !old.isEmpty() && rng.nextFloat() < share ? old.get(rng.nextInt(old.size())).batch(rng, size, test) : narrow.batch(rng, size, test);
        }
    }

    /** {@code a} picks how much old practice is replayed; {@code b} packs how long it tunes and at what rate: steps * 2 + rate. */
    public static Work.Result forgetting(Work.Request r, Work.Watcher w) {
        if (r.data() == null) {
            return new Work.Result(Picture.NONE, Component.translatable("residual.trial.needs_data"));
        }
        CompiledGraph model = r.model();
        float share = REPLAY[Math.floorMod(r.a(), REPLAY.length)];
        int steps = STEPS[Math.floorMod(r.b() / 2, STEPS.length)];
        float rate = RATES[Math.floorMod(r.b() % 2, RATES.length)];
        Benchmark narrow = r.data().benchmark();
        // what it knows: the benchmarks it passes, other than the one it will tune on
        Map<Benchmark, Trainer.Eval> before = new EnumMap<>(Benchmark.class);
        Benchmark known = null;
        double best = -1;
        for (Benchmark b : Benchmark.values()) {
            if (b == narrow || w.stopped()) {
                continue;
            }
            Task test = Reads.test(model, b);
            Trainer.Eval e = Trainer.evaluate(model, test, 2);
            before.put(b, e);
            double p = progress(b, e);
            if (p > best) {
                best = p;
                known = b;
            }
        }
        if (known == null) {
            return new Work.Result(Picture.NONE, Component.empty());
        }
        boolean passedBefore = best >= 1;
        Task oldTest = Reads.test(model, known);
        Task newTest = Reads.test(model, narrow);
        // reading's own practice is what shards carry, which only a drive holds: it stays out of this
        List<Task> old = known == Benchmark.READING ? List.of() : List.of(known.practice());
        bind(old, model);
        bind(List.of(r.data().task()), model);
        Task task = new Replayed(r.data().task(), old, share);
        Trainer tr = new Trainer(model, task, r.seed(), rate, BATCH, CLIP);
        List<float[]> curve = new ArrayList<>();
        curve.add(new float[] {0, score(known, before.get(known)), score(narrow, Trainer.evaluate(model, newTest, 2))});
        int every = Math.max(25, steps / 20);
        for (int s = 1; s <= steps && !w.stopped(); s++) {
            tr.train();
            w.at((float) s / steps);
            if (s % every == 0) {
                curve.add(new float[] {s, score(known, Trainer.evaluate(model, oldTest, 2)), score(narrow, Trainer.evaluate(model, newTest, 2))});
                w.show(forgetPicture(curve, known, narrow, share));
            }
        }
        float end = curve.getLast()[1];
        boolean forgot = passedBefore && !(known.metric == Task.Metric.LOSS ? end <= known.target : end >= known.target);
        return new Work.Result(forgetPicture(curve, known, narrow, share), Component.empty(), List.of(), List.of(), forgot ? "failure/forgot" : null, w.stopped() ? null : model);
    }

    /** A task practised by playing plays with the model being tuned. */
    private static void bind(List<Task> tasks, CompiledGraph model) {
        for (Task t : tasks) {
            if (t instanceof Played played) {
                played.policy(model);
            }
        }
    }

    private static double progress(Benchmark b, Trainer.Eval e) {
        return b.metric == Task.Metric.LOSS ? (Math.log(Vocab.CHARACTERS) - e.loss()) / (Math.log(Vocab.CHARACTERS) - b.target) : e.accuracy() / b.target;
    }

    private static float score(Benchmark b, Trainer.Eval e) {
        return (float) (b.metric == Task.Metric.LOSS ? e.loss() : e.accuracy());
    }

    private static String shown(Benchmark b, float score) {
        return b.metric == Task.Metric.LOSS ? Picture.two(score) : Picture.percent(score);
    }

    /** A number in the colour of its line on the chart below. */
    private static Component colored(String text, int color) {
        return Component.literal(text).withColor(color);
    }

    private static Picture forgetPicture(List<float[]> curve, Benchmark known, Benchmark narrow, float share) {
        Picture.Builder b = new Picture.Builder();
        float[] first = curve.getFirst(), last = curve.getLast();
        b.text(4, 3, Component.translatable("residual.trial.forget.title", Component.translatable("residual.benchmark." + narrow.id), Picture.percent(share)), Picture.DIM);
        b.text(4, 13, Component.translatable("residual.trial.forget.now", Component.translatable("residual.benchmark." + known.id), shown(known, first[1]), colored(shown(known, last[1]), Picture.AMBER),
                Component.translatable("residual.benchmark." + narrow.id), shown(narrow, first[2]), colored(shown(narrow, last[2]), Picture.TEAL)), Picture.TEXT);
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        boolean loss = known.metric == Task.Metric.LOSS || narrow.metric == Task.Metric.LOSS;
        if (loss) {
            for (float[] p : curve) {
                lo = Math.min(lo, Math.min(p[1], p[2]));
                hi = Math.max(hi, Math.max(p[1], p[2]));
            }
        } else {
            lo = 0;
            hi = 1;
        }
        Chart chart = new Chart(b, 4, 26, Picture.W - 8, Picture.H - 30, first[0], Math.max(last[0], 1), lo, Math.max(hi, lo + 1e-3f));
        chart.line(curve, 2, Picture.TEAL);
        chart.line(curve, 1, Picture.AMBER);
        return b.build();
    }

    // ---------------------------------------------------------------- writing on writing

    /**
     * One generation.
     *
     * @param loss     how well it reads real text it has never seen
     * @param words    how many different words it writes, per thousand
     * @param real     what share of the words it writes are words of the real text
     * @param sample   a stretch of what it wrote
     */
    public record Generation(float loss, float words, float real, String sample) {
    }

    /** {@code a} picks how much real text each generation keeps; {@code b} how long each trains. */
    public static Work.Result collapse(Work.Request r, Work.Watcher w) {
        if (r.data() == null || r.data().texts().isEmpty()) {
            return new Work.Result(Picture.NONE, Component.translatable("residual.trial.needs_text"));
        }
        float kept = KEPT[Math.floorMod(r.a(), KEPT.length)];
        int steps = STEPS[Math.floorMod(r.b(), STEPS.length)];
        List<Generation> generations = generations(r.model(), r.data().texts(), kept, steps, r.seed(), w);
        boolean collapsed = kept == 0 && generations.size() == GENERATIONS
                && generations.getLast().loss() - generations.getFirst().loss() >= COLLAPSED;
        return new Work.Result(collapsePicture(generations, kept), Component.empty(), List.of(), List.of(), collapsed ? "failure/collapse" : null, null);
    }

    /** Trains a fresh model of the same design on real text, then each next one on what the last one wrote. */
    public static List<Generation> generations(CompiledGraph design, List<String> real, float kept, int steps, long seed, Work.Watcher w) {
        Vocab vocab = design.env.tokenizer();
        Task test = Archive.test(vocab);
        Set<String> realWords = new HashSet<>();
        for (String t : real) {
            realWords.addAll(words(t));
        }
        Random rng = new Random(seed);
        List<String> corpus = real;
        List<Generation> out = new ArrayList<>();
        for (int g = 0; g < GENERATIONS && !w.stopped(); g++) {
            CompiledGraph model = design.fresh(seed + g);
            Trainer tr = new Trainer(model, new TextTask(corpus, Benchmark.READING.target, vocab), seed + g, 3e-3f, BATCH, CLIP);
            for (int s = 1; s <= steps && !w.stopped(); s++) {
                tr.train();
                if (s % 25 == 0) {
                    w.at((g + 0.9f * s / steps) / GENERATIONS);
                }
            }
            if (w.stopped()) {
                break;
            }
            List<String> wrote = write(model, corpus, rng);
            List<String> all = new ArrayList<>();
            int words = 0, knownWords = 0;
            Set<String> different = new HashSet<>();
            for (String t : wrote) {
                for (String word : words(t)) {
                    words++;
                    different.add(word);
                    knownWords += realWords.contains(word) ? 1 : 0;
                }
            }
            all.addAll(wrote);
            out.add(new Generation((float) Trainer.evaluate(model, test, 4).loss(), 1000f * different.size() / Math.max(1, words), (float) knownWords / Math.max(1, words),
                    wrote.getFirst().substring(0, Math.min(90, wrote.getFirst().length()))));
            w.show(collapsePicture(out, kept));
            // what the next generation reads: what this one wrote, and as much real text as is kept, by length
            if (kept > 0) {
                int want = Math.round(WRITTEN * kept / (1 - kept)), have = 0;
                while (have < want) {
                    String t = real.get(rng.nextInt(real.size()));
                    all.add(t);
                    have += t.length();
                }
            }
            corpus = all;
        }
        return out;
    }

    private static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    /** Writes {@link #WRITTEN} characters from the model's own probabilities, each stream starting from a piece of what it read. */
    static List<String> write(CompiledGraph model, List<String> corpus, Random rng) {
        Vocab vocab = model.env.tokenizer();
        List<String> usable = corpus.stream().filter(t -> t.length() > WINDOW * 2).toList();
        int[][] window = new int[STREAMS][];
        StringBuilder[] texts = new StringBuilder[STREAMS];
        for (int s = 0; s < STREAMS; s++) {
            String from = usable.isEmpty() ? "The " : usable.get(rng.nextInt(usable.size()));
            int at = from.length() > WINDOW ? rng.nextInt(from.length() - WINDOW) : 0;
            int[] ids = vocab.encode(from.substring(at, Math.min(from.length(), at + WINDOW)));
            int[] full = new int[WINDOW];
            for (int i = 0; i < WINDOW; i++) {
                full[i] = ids.length == 0 ? vocab.id(' ') : ids[Math.max(0, ids.length - WINDOW + i) % ids.length];
            }
            window[s] = full;
            texts[s] = new StringBuilder();
        }
        int written = 0;
        while (written < WRITTEN) {
            int[] in = new int[STREAMS * WINDOW];
            for (int s = 0; s < STREAMS; s++) {
                System.arraycopy(window[s], 0, in, s * WINDOW, WINDOW);
            }
            Tensor logits = model.forward(null, new Tokens(in, STREAMS, WINDOW), null);
            for (int s = 0; s < STREAMS; s++) {
                float[] p = Reads.probs(logits, s * WINDOW + WINDOW - 1);
                float x = rng.nextFloat(), acc = 0;
                int token = p.length - 1;
                for (int i = 0; i < p.length; i++) {
                    acc += p[i];
                    if (x < acc) {
                        token = i;
                        break;
                    }
                }
                String piece = vocab.piece(token);
                texts[s].append(piece);
                written += piece.length();
                System.arraycopy(window[s], 1, window[s], 0, WINDOW - 1);
                window[s][WINDOW - 1] = token;
            }
        }
        List<String> out = new ArrayList<>();
        for (StringBuilder t : texts) {
            out.add(t.toString());
        }
        return out;
    }

    private static Picture collapsePicture(List<Generation> generations, float kept) {
        Picture.Builder b = new Picture.Builder();
        b.wrapped(4, 3, Picture.W - 8, Component.translatable("residual.trial.collapse.title", Picture.percent(kept)), Picture.DIM);
        int top = 26, cell = 58, chartH = 50;
        float lo = Float.MAX_VALUE, hi = 0;
        for (Generation g : generations) {
            lo = Math.min(lo, g.loss());
            hi = Math.max(hi, g.loss());
        }
        lo = Math.max(0, lo - 0.1f);
        hi = Math.max(hi, lo + 0.3f);
        for (int i = 0; i < generations.size(); i++) {
            Generation g = generations.get(i);
            int x = 6 + i * (cell + 2), h = Math.max(2, Math.round(chartH * (g.loss() - lo) / (hi - lo)));
            Component tip = Component.translatable("residual.trial.collapse.generation", Integer.toString(i), Picture.two(g.loss()), String.format(Locale.ROOT, "%.0f", g.words()), Picture.percent(g.real()));
            b.box(x, top + chartH - h, cell, h, i == 0 ? Picture.TEAL : Picture.AMBER, tip);
            b.text(x + 2, top + chartH - 10, Picture.two(g.loss()), Picture.TEXT);
            b.text(x + 2, top + chartH + 3, Component.translatable("residual.trial.collapse.words", String.format(Locale.ROOT, "%.0f", g.words())), Picture.DIM);
            b.text(x + 2, top + chartH + 13, Component.translatable("residual.trial.collapse.real", Picture.percent(g.real())), Picture.DIM);
        }
        if (!generations.isEmpty()) {
            b.text(4, top + chartH + 28, Picture.W - 8, Picture.shown(generations.getLast().sample()), Picture.TEXT);
        }
        return b.build();
    }

    // ---------------------------------------------------------------- the test in the practice

    /**
     * {@code a} picks how much of practice is the test's own items; {@code b} how long it trains. A fresh model of the design: one that
     * already passes has no room to pull ahead of a look-alike.
     */
    public static Work.Result contamination(Work.Request r, Work.Watcher w) {
        Benchmark benchmark = r.data() == null ? r.benchmark() : r.data().benchmark();
        CompiledGraph model = r.model().fresh(r.seed());
        Task test = Reads.test(model, benchmark);
        if (test.measure(model) != null) {
            return new Work.Result(Picture.NONE, Component.translatable("residual.trial.contaminate.played"));
        }
        if (r.data() == null && benchmark == Benchmark.READING) {
            return new Work.Result(Picture.NONE, Component.translatable("residual.trial.needs_text"));
        }
        float share = LEAK[Math.floorMod(r.a(), LEAK.length)];
        int steps = STEPS[Math.floorMod(r.b(), STEPS.length)];
        Task practice = r.data() != null ? r.data().task() : benchmark.practice();
        Task leaked = new Contamination.Leaked(practice, test, share);
        Trainer tr = new Trainer(model, leaked, r.seed(), 3e-3f, BATCH, CLIP);
        List<float[]> curve = new ArrayList<>();
        int every = Math.max(25, steps / 20);
        Contamination.Check check = Contamination.check(model, test, benchmark);
        curve.add(new float[] {0, check.scored(), check.fresh()});
        for (int s = 1; s <= steps && !w.stopped(); s++) {
            tr.train();
            w.at((float) s / steps);
            if (s % every == 0) {
                check = Contamination.check(model, test, benchmark);
                curve.add(new float[] {s, check.scored(), check.fresh()});
                w.show(contaminationPicture(curve, benchmark, check, -1, share));
            }
        }
        float overlap = Contamination.overlap(leaked, test, new Random(r.seed()));
        boolean caught = share > 0 && check.contaminated();
        return new Work.Result(contaminationPicture(curve, benchmark, check, overlap, share), Component.empty(), List.of(), List.of(), caught ? "evaluation/contaminated" : null, w.stopped() ? null : model);
    }

    private static Picture contaminationPicture(List<float[]> curve, Benchmark benchmark, Contamination.Check check, float overlap, float share) {
        Picture.Builder b = new Picture.Builder();
        b.text(4, 3, Component.translatable("residual.trial.contaminate.title", Component.translatable("residual.benchmark." + benchmark.id), Picture.percent(share)), Picture.DIM);
        b.text(4, 13, Component.translatable(check.contaminated() ? "residual.trial.contaminate.caught" : "residual.trial.contaminate.clean",
                colored(shown(benchmark, check.scored()), Picture.AMBER), colored(shown(benchmark, check.fresh()), Picture.TEAL)), check.contaminated() ? Picture.RED : Picture.TEXT);
        if (overlap >= 0) {
            b.text(4, 23, Component.translatable("residual.trial.contaminate.overlap", Picture.percent(overlap)), Picture.DIM);
        }
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float[] p : curve) {
            lo = Math.min(lo, Math.min(p[1], p[2]));
            hi = Math.max(hi, Math.max(p[1], p[2]));
        }
        Chart chart = new Chart(b, 4, 36, Picture.W - 8, Picture.H - 40, curve.getFirst()[0], Math.max(curve.getLast()[0], 1), lo, Math.max(hi, lo + 1e-3f));
        chart.line(curve, 2, Picture.TEAL);
        chart.line(curve, 1, Picture.AMBER);
        return b.build();
    }

    // ---------------------------------------------------------------- a chart

    /** Lines over steps, with their scale at the corners. */
    private static final class Chart {
        private final Picture.Builder b;
        private final int x, y, w, h;
        private final float x0, x1, lo, hi;

        Chart(Picture.Builder b, int x, int y, int w, int h, float x0, float x1, float lo, float hi) {
            this.b = b;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.x0 = x0;
            this.x1 = Math.max(x1, x0 + 1);
            this.lo = lo;
            this.hi = hi;
            for (int i = 0; i <= 4; i++) {
                b.box(x, y + i * (h - 1) / 4, w, 1, Picture.GRID);
            }
            b.text(x + 2, y + 2, hi <= 1 && lo >= 0 ? Picture.percent(hi) : Picture.two(hi), Picture.DIM);
            b.text(x + 2, y + h - 10, hi <= 1 && lo >= 0 ? Picture.percent(lo) : Picture.two(lo), Picture.DIM);
            String last = String.format(Locale.ROOT, "%,d", Math.round(x1));
            b.text(x + w - 6 * last.length() - 2, y + h - 10, last, Picture.DIM);
        }

        private int px(float at) {
            return x + Math.round((at - x0) / (x1 - x0) * (w - 1));
        }

        private int py(float v) {
            return y + Math.round((hi - Math.max(lo, Math.min(hi, v))) / Math.max(hi - lo, 1e-6f) * (h - 1));
        }

        void line(List<float[]> points, int column, int color) {
            for (int i = 1; i < points.size(); i++) {
                b.line(px(points.get(i - 1)[0]), py(points.get(i - 1)[column]), px(points.get(i)[0]), py(points.get(i)[column]), color);
            }
        }

        void marker(float at, Component text) {
            int p = px(at);
            b.box(p, y, 1, h, 0x60FFFFFF, text);
            b.text(p + 3, y + 2, text, Picture.DIM);
        }
    }
}
