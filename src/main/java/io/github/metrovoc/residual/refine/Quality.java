package io.github.metrovoc.residual.refine;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * What a Quality Classifier asks of a model: is this writing as it was written, or has a bad line been at it? A model that has read
 * a lot of writing knows what comes next in writing, so what a bad line has been at surprises it, letter after letter, by more than
 * good writing does, and a whole document gives it many letters to go by. The rule it falls back on knows only whether the words are
 * words, so it sees letters come through wrong and never words come through in the wrong order.
 */
public final class Quality {
    /** Windows read from each doc, at most, and the most asked of a model at once. */
    private static final int READS = 8, BATCH = 32;
    /** Tokens at the start of a window that only give the model something to go on. */
    private static final int WARMUP = 16;
    /** The rule: more than this share of words it has never seen, and the text has gone bad. */
    public static final double UNKNOWN = 0.1;
    /** A word has to turn up this often in records that shards carry for the rule to know it. */
    private static final int KNOWN = 2;
    /** Records of at least this many characters are what the test judges. */
    private static final int SHORTEST = 400;
    private static final float LEAST_SCALE = 0.02f;
    private static Set<String> lexicon;
    private static List<Archive.Record> tested;

    /** Documents a model is judged on, and documents it finds its cut on: of the records no shard carries, never the same ones. */
    private static final int TESTED = 128, CALIBRATED = 64;
    private static final long TEST_SEED = 4243, CALIBRATION_SEED = 4244;

    /** A document, and whether it is as it was written. */
    public record Example(Doc doc, boolean good) {
    }

    /**
     * How one model tells good from bad, found by having it read documents it knows the answer to: the surprise at which a document
     * is as likely spoiled as not, and how far from there it is sure.
     */
    public record Reader(float cut, float scale) {
        /** How sure it is that a document that surprised it this much is good, from 0 to 1. */
        public float good(float surprise) {
            return (float) (1 / (1 + Math.exp((surprise - cut) / scale)));
        }
    }

    private Quality() {
    }

    /** As a bad line leaves it: words out of order, or letters come through wrong, as a dump's spoiled messages are. */
    public static String spoil(String text, Random rng) {
        return rng.nextBoolean() ? Dirt.scramble(text, rng) : Dirt.garble(text, Dirt.GARBLE_LEAST + (Dirt.GARBLE_MOST - Dirt.GARBLE_LEAST) * rng.nextDouble(), rng);
    }

    /** Long records no shard carries, by turns for the test and for finding a model's cut. */
    private static synchronized List<Archive.Record> longRecords(boolean test) {
        if (tested == null) {
            tested = Archive.records().stream().filter(r -> Archive.heldOut(r.id()) && r.text().length() >= SHORTEST).toList();
        }
        List<Archive.Record> out = new ArrayList<>();
        for (int i = test ? 0 : 1; i < tested.size(); i += 2) {
            out.add(tested.get(i));
        }
        return out;
    }

    private static List<Example> examples(boolean test, int count, long seed) {
        List<Archive.Record> records = longRecords(test);
        Random rng = new Random(seed);
        List<Example> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String text = records.get(i % records.size()).text();
            boolean good = i % 2 == 0;
            out.add(new Example(new Doc(good ? text : spoil(text, rng), Domain.LETTERS), good));
        }
        return out;
    }

    /** What the test reads: its records as they were written, each once. */
    public static List<String> testTexts() {
        return longRecords(true).stream().map(Archive.Record::text).toList();
    }

    /** The share of the test's documents {@code model} is right about, by the cut it finds on others. */
    public static double accuracy(CompiledGraph model) {
        Reader reader = calibrate(model);
        List<Example> test = examples(true, TESTED, TEST_SEED);
        float[] seen = surprise(model, test.stream().map(Example::doc).toList());
        int right = 0;
        for (int i = 0; i < seen.length; i++) {
            right += !Float.isNaN(seen[i]) && seen[i] <= reader.cut() == test.get(i).good() ? 1 : 0;
        }
        return right / (double) seen.length;
    }

    /** Finds where {@code model} tells good from bad, on documents it knows the answer to. */
    public static Reader calibrate(CompiledGraph model) {
        List<Example> known = examples(false, CALIBRATED, CALIBRATION_SEED);
        float[] seen = surprise(model, known.stream().map(Example::doc).toList());
        List<Float> good = new ArrayList<>(), bad = new ArrayList<>();
        for (int i = 0; i < seen.length; i++) {
            if (!Float.isNaN(seen[i])) {
                (known.get(i).good() ? good : bad).add(seen[i]);
            }
        }
        Collections.sort(good);
        Collections.sort(bad);
        float best = 0, cut = good.getLast();
        for (float c : good) {
            float right = 0;
            for (float g : good) {
                right += g <= c ? 1 : 0;
            }
            for (float b : bad) {
                right += b > c ? 1 : 0;
            }
            if (right > best) {
                best = right;
                cut = c;
            }
        }
        float spread = bad.get(bad.size() / 2) - good.get(good.size() / 2);
        return new Reader(cut, Math.max(LEAST_SCALE, spread / 4));
    }

    /** How sure {@code model} is of each of {@code docs} being good, 0 to 1, or -1 for one with too little to read. */
    public static float[] read(CompiledGraph model, Reader reader, List<Doc> docs) {
        float[] seen = surprise(model, docs);
        for (int i = 0; i < seen.length; i++) {
            seen[i] = Float.isNaN(seen[i]) ? -1 : reader.good(seen[i]);
        }
        return seen;
    }

    /**
     * How surprised {@code model} is by each of {@code docs}, in nats per character, as it reads them with its own tokens; NaN for a
     * doc with too little to read. Windows lie evenly along a doc's longest stretches.
     */
    public static float[] surprise(CompiledGraph model, List<Doc> docs) {
        Vocab vocab = model.env.tokenizer();
        int t = Vocab.CONTEXT;
        List<int[]> windows = new ArrayList<>();
        List<Integer> owner = new ArrayList<>();
        for (int d = 0; d < docs.size(); d++) {
            for (int[] w : windows(docs.get(d), vocab, t)) {
                windows.add(w);
                owner.add(d);
            }
        }
        double[] nats = new double[docs.size()];
        long[] chars = new long[docs.size()];
        for (int from = 0; from < windows.size(); from += BATCH) {
            int n = Math.min(BATCH, windows.size() - from);
            int[] in = new int[n * t];
            for (int i = 0; i < n; i++) {
                System.arraycopy(windows.get(from + i), 0, in, i * t, t);
            }
            Tensor logits = model.forward(null, new Tokens(in, n, t), null);
            int v = logits.dim(-1);
            for (int i = 0; i < n; i++) {
                int[] w = windows.get(from + i);
                int d = owner.get(from + i);
                for (int k = WARMUP; k < t; k++) {
                    nats[d] += crossEntropy(logits.data, (i * t + k) * v, v, w[k + 1]);
                    chars[d] += vocab.piece(w[k + 1]).length();
                }
            }
        }
        float[] out = new float[docs.size()];
        for (int d = 0; d < out.length; d++) {
            out[d] = chars[d] == 0 ? Float.NaN : (float) (nats[d] / chars[d]);
        }
        return out;
    }

    private static double crossEntropy(float[] logits, int at, int v, int target) {
        float top = Float.NEGATIVE_INFINITY;
        for (int j = 0; j < v; j++) {
            top = Math.max(top, logits[at + j]);
        }
        double sum = 0;
        for (int j = 0; j < v; j++) {
            sum += Math.exp(logits[at + j] - top);
        }
        return Math.log(sum) - (logits[at + target] - top);
    }

    /** Up to {@value #READS} windows of a window and its next token, evenly along the stretches long enough to give one. */
    private static List<int[]> windows(Doc doc, Vocab vocab, int t) {
        List<int[]> runs = new ArrayList<>();
        int room = 0;
        for (String part : doc.parts()) {
            int[] ids = vocab.encode(part);
            if (ids.length > t) {
                runs.add(ids);
                room += ids.length - t;
            }
        }
        List<int[]> out = new ArrayList<>(READS);
        if (runs.isEmpty()) {
            return out;
        }
        int count = Math.min(READS, 1 + room / t);
        for (int k = 0; k < count; k++) {
            int left = (int) ((long) room * k / count);
            for (int[] run : runs) {
                int here = run.length - t;
                if (left <= here) {
                    out.add(Arrays.copyOfRange(run, left, left + t + 1));
                    break;
                }
                left -= here;
            }
        }
        return out;
    }

    /** What the rule thinks: the words are words. */
    public static boolean ruleGood(Doc doc) {
        Set<String> known = lexicon();
        int words = 0, unknown = 0;
        for (String p : doc.parts()) {
            for (String w : p.split("[^A-Za-z']+")) {
                if (!w.isEmpty()) {
                    words++;
                    unknown += known.contains(w.toLowerCase(Locale.ROOT)) ? 0 : 1;
                }
            }
        }
        return words > 0 && unknown <= UNKNOWN * words;
    }

    /** Words the shards' records use. */
    private static synchronized Set<String> lexicon() {
        if (lexicon == null) {
            Object2IntOpenHashMap<String> counts = new Object2IntOpenHashMap<>();
            for (Archive.Record r : Archive.records()) {
                if (!Archive.heldOut(r.id())) {
                    for (String w : r.text().split("[^A-Za-z']+")) {
                        if (!w.isEmpty()) {
                            counts.addTo(w.toLowerCase(Locale.ROOT), 1);
                        }
                    }
                }
            }
            Set<String> known = new HashSet<>();
            counts.object2IntEntrySet().forEach(e -> {
                if (e.getIntValue() >= KNOWN) {
                    known.add(e.getKey());
                }
            });
            lexicon = known;
        }
        return lexicon;
    }
}
