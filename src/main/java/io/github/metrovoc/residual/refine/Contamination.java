package io.github.metrovoc.residual.refine;

import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Vocab;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks for what every benchmark tests on in the text a model would practise on. A score means nothing on a test it has already
 * read, however the text got there. Prose is compared by runs of words, what the tests' short lines say by the whole line, and
 * only lines practice never shows count: {@code = stay} is in the test and in practice alike, but {@code = fetch oak logs} is the
 * test's own.
 */
public final class Contamination {
    /** How long a run of words has to be to mean the text was copied. */
    private static final int RUN = 13;
    /** How many draws of a benchmark's practice stand for all of it. */
    private static final int PRACTICE_BATCHES = 256;
    /** A line shorter than this says too little to tell where it came from. */
    private static final int SHORTEST_LINE = 6;

    private static final Map<Benchmark, TestSet> TESTS = new ConcurrentHashMap<>();

    /**
     * What it found for one benchmark.
     *
     * @param items   test items it compared: held-out records or windows, or lines only the test has; 0 for a test played, not read
     * @param found   how many of them turned up
     * @param example the start of one that did, or empty
     */
    public record Finding(Benchmark benchmark, int items, int found, String example) {
        public boolean played() {
            return items == 0;
        }
    }

    /**
     * What a pass did.
     *
     * @param kept     the text with what the tests have taken out, or {@code docs} as they came when only looking
     * @param lines    lines of text with something of a test in them
     * @param chars    and their characters
     */
    public record Result(List<Doc> kept, List<Finding> findings, int lines, long chars) {
    }

    /** One benchmark's test as something to look for. */
    private record TestSet(Benchmark benchmark, LongOpenHashSet runs, Set<String> lines, List<String> items) {
    }

    private Contamination() {
    }

    /** Compares {@code docs} with every benchmark's test; {@code remove} takes out what it finds. */
    public static Result run(List<Doc> docs, boolean remove) {
        List<TestSet> tests = new ArrayList<>();
        for (Benchmark b : Benchmark.values()) {
            tests.add(TESTS.computeIfAbsent(b, Contamination::testSet));
        }
        // what the text holds of each test: its lines as they stand, and its runs of words
        Set<String> present = new HashSet<>();
        LongOpenHashSet runs = new LongOpenHashSet();
        for (Doc d : docs) {
            for (String part : d.parts()) {
                for (String line : part.split("\n")) {
                    present.add(line.strip());
                }
                runs(part, runs::add);
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (TestSet t : tests) {
            int found = 0;
            String example = "";
            for (String item : t.items()) {
                boolean hit;
                if (t.runs().isEmpty()) {
                    hit = present.contains(item);
                } else {
                    boolean[] any = new boolean[1];
                    runs(item, r -> any[0] |= runs.contains(r));
                    hit = any[0];
                }
                if (hit) {
                    found++;
                    example = example.isEmpty() ? item.substring(0, Math.min(item.length(), 120)) : example;
                }
            }
            findings.add(new Finding(t.benchmark(), t.items().size(), found, example));
        }
        int lines = 0;
        long chars = 0;
        List<Doc> kept = new ArrayList<>(docs.size());
        for (Doc d : docs) {
            List<String> parts = new ArrayList<>(d.parts().size());
            for (String part : d.parts()) {
                String[] all = part.split("\n", -1);
                boolean[] tainted = tainted(part, all, tests);
                StringBuilder out = new StringBuilder(part.length());
                for (int i = 0; i < all.length; i++) {
                    if (tainted[i]) {
                        lines++;
                        chars += all[i].length() + 1;
                    } else {
                        out.append(out.isEmpty() ? "" : "\n").append(all[i]);
                    }
                }
                if (!out.isEmpty()) {
                    parts.add(out.toString());
                }
            }
            if (!parts.isEmpty()) {
                kept.add(d.with(parts));
            }
        }
        return new Result(remove ? kept : docs, findings, lines, chars);
    }

    /** Which lines of a stretch hold something of a test. */
    private static boolean[] tainted(String part, String[] lines, List<TestSet> tests) {
        boolean[] out = new boolean[lines.length];
        for (TestSet t : tests) {
            if (t.runs().isEmpty()) {
                for (int i = 0; i < lines.length; i++) {
                    if (t.lines().contains(lines[i].strip())) {
                        out[i] = true;
                        // what was said goes with what was meant by it
                        if (i > 0 && lines[i].startsWith("= ") && lines[i - 1].startsWith("> ")) {
                            out[i - 1] = true;
                        }
                    }
                }
            } else {
                wordsByLine(part, (run, first, last) -> {
                    if (t.runs().contains(run)) {
                        for (int i = first; i <= last; i++) {
                            out[i] = true;
                        }
                    }
                });
            }
        }
        return out;
    }

    private static TestSet testSet(Benchmark b) {
        Task test = b.test(Vocab.WORLD);
        List<String> texts = test.heldOutTexts();
        if (texts.isEmpty()) {
            return new TestSet(b, new LongOpenHashSet(), Set.of(), List.of());
        }
        if (b == Benchmark.READING || b == Benchmark.QUALITY) {
            LongOpenHashSet runs = new LongOpenHashSet();
            for (String text : texts) {
                runs(text, runs::add);
            }
            return new TestSet(b, runs, Set.of(), texts);
        }
        Set<String> lines = new HashSet<>();
        for (String text : texts) {
            for (String line : text.split("\n")) {
                if (line.strip().length() >= SHORTEST_LINE) {
                    lines.add(line.strip());
                }
            }
        }
        Random rng = new Random(54321);
        Task practice = b.practice();
        for (int i = 0; i < PRACTICE_BATCHES; i++) {
            for (String text : Task.decode(practice.batch(rng, 32, false), practice.vocab)) {
                for (String line : text.split("\n")) {
                    lines.remove(line.strip());
                }
            }
        }
        return new TestSet(b, new LongOpenHashSet(), lines, List.copyOf(lines));
    }

    private interface RunAt {
        void accept(long run, int firstLine, int lastLine);
    }

    private interface RunOut {
        void accept(long run);
    }

    private static void runs(String text, RunOut out) {
        wordsByLine(text, (run, first, last) -> out.accept(run));
    }

    /** Every run of words in {@code text}, lower-cased, with the lines it lies on. */
    private static void wordsByLine(String text, RunAt out) {
        int k = RUN;
        long[] last = new long[k];
        int[] lineOf = new int[k];
        int n = 0, line = 0, i = 0, len = text.length();
        while (i < len) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                i++;
            } else if (!word(c)) {
                i++;
            } else {
                long h = 0xcbf29ce484222325L;
                while (i < len && word(text.charAt(i))) {
                    h = (h ^ Character.toLowerCase(text.charAt(i))) * 1099511628211L;
                    i++;
                }
                last[n % k] = h;
                lineOf[n % k] = line;
                n++;
                if (n >= k) {
                    long r = 0;
                    for (int j = 0; j < k; j++) {
                        r = r * 1099511628211L + last[(n - k + j) % k];
                    }
                    out.accept(r, lineOf[n % k], line);
                }
            }
        }
    }

    private static boolean word(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '\'';
    }
}
