package io.github.metrovoc.residual.refine;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Takes out what was already seen. Exact copies go by a hash of their text, and long lines seen before go the same way. Near
 * copies go by MinHash: each doc is reduced to its runs of {@value #WORDS} words, and {@value #HASHES} hash functions each keep the
 * smallest value over those runs. Two docs agree on any one of them as often as they share runs (their Jaccard similarity), so
 * docs that agree on a whole band of {@code rows} values are worth comparing, and {@code bands} chances make that likely above
 * the threshold and rare below it. Of two near copies the longer stays.
 */
public final class Duplicates {
    public static final int HASHES = 128, WORDS = 5;
    /** A line this long that was seen before is a repeat; shorter ones ("Dear Oren,") are how writing goes. */
    public static final int LINE = 24;
    /** The similarities it can be set to, each with its bands and rows; all fit in {@value #HASHES} hashes. */
    public static final float[] THRESHOLDS = {0.3f, 0.5f, 0.7f, 0.9f};
    private static final int[][] BANDS = {{32, 3}, {32, 4}, {16, 8}, {8, 16}};
    /** Docs compared in one bucket: boilerplate can fill a bucket, and the first few kept settle it. */
    private static final int BUCKET = 32;

    /**
     * What a pass found.
     *
     * @param kept        what goes on, in the order it came
     * @param similar     how many compared pairs fell in each tenth of similarity
     * @param compared    pairs compared
     */
    public record Result(List<Doc> kept, int exactDocs, long exactChars, int nearDocs, long nearChars, int lines, long lineChars,
                         int[] similar, long compared) {
    }

    private Duplicates() {
    }

    /** How likely two docs of similarity {@code s} are to share a band: the curve the threshold sits on. */
    public static double chance(int threshold, double s) {
        return 1 - Math.pow(1 - Math.pow(s, BANDS[threshold][1]), BANDS[threshold][0]);
    }

    public static Result run(List<Doc> docs, int threshold) {
        float least = THRESHOLDS[threshold];
        int bands = BANDS[threshold][0], rows = BANDS[threshold][1];
        // exact copies first
        LongOpenHashSet seen = new LongOpenHashSet();
        List<Doc> distinct = new ArrayList<>(docs.size());
        int exactDocs = 0;
        long exactChars = 0;
        for (Doc d : docs) {
            if (seen.add(hash(d))) {
                distinct.add(d);
            } else {
                exactDocs++;
                exactChars += d.chars();
            }
        }
        // the longest first, so of two near copies the longer stays
        Integer[] order = new Integer[distinct.size()];
        Arrays.setAll(order, i -> i);
        Arrays.sort(order, Comparator.comparingLong((Integer i) -> -distinct.get(i).chars()));
        long[][] mix = mix();
        List<Long2ObjectOpenHashMap<IntArrayList>> buckets = new ArrayList<>(bands);
        for (int b = 0; b < bands; b++) {
            buckets.add(new Long2ObjectOpenHashMap<>());
        }
        long[][] runs = new long[distinct.size()][];
        boolean[] dropped = new boolean[distinct.size()];
        int[] similar = new int[10];
        long compared = 0;
        int nearDocs = 0;
        long nearChars = 0;
        for (Integer boxed : order) {
            int i = boxed;
            runs[i] = runs(distinct.get(i));
            if (runs[i].length == 0) {
                continue;
            }
            int[] sign = signature(runs[i], mix);
            long[] keys = new long[bands];
            boolean near = false;
            LongOpenHashSet asked = new LongOpenHashSet();
            for (int b = 0; b < bands && !near; b++) {
                keys[b] = band(sign, b, rows);
                IntArrayList in = buckets.get(b).get(keys[b]);
                if (in == null) {
                    continue;
                }
                for (int k = 0; k < in.size() && !near; k++) {
                    int other = in.getInt(k);
                    if (asked.add(other)) {
                        double s = jaccard(runs[i], runs[other]);
                        similar[Math.min(9, (int) (s * 10))]++;
                        compared++;
                        near = s >= least;
                    }
                }
            }
            if (near) {
                dropped[i] = true;
                nearDocs++;
                nearChars += distinct.get(i).chars();
                continue;
            }
            for (int b = 0; b < bands; b++) {
                IntArrayList in = buckets.get(b).computeIfAbsent(keys[b], k -> new IntArrayList(1));
                if (in.size() < BUCKET) {
                    in.add(i);
                }
            }
        }
        // long lines seen before, of what is left
        LongOpenHashSet lineSeen = new LongOpenHashSet();
        List<Doc> kept = new ArrayList<>(distinct.size());
        int lines = 0;
        long lineChars = 0;
        for (int i = 0; i < dropped.length; i++) {
            if (dropped[i]) {
                continue;
            }
            Doc d = distinct.get(i);
            List<String> parts = new ArrayList<>(d.parts().size());
            for (String part : d.parts()) {
                StringBuilder out = new StringBuilder(part.length());
                for (String line : part.split("\n", -1)) {
                    if (line.length() >= LINE && !lineSeen.add(hash(line))) {
                        lines++;
                        lineChars += line.length() + 1;
                    } else {
                        out.append(out.isEmpty() ? "" : "\n").append(line);
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
        return new Result(kept, exactDocs, exactChars, nearDocs, nearChars, lines, lineChars, similar, compared);
    }

    private static long hash(Doc d) {
        long h = d.domain().ordinal();
        for (String p : d.parts()) {
            h = h * 1099511628211L + hash(p);
        }
        return h;
    }

    private static long hash(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h = (h ^ s.charAt(i)) * 1099511628211L;
        }
        return h;
    }

    /** The distinct runs of {@value #WORDS} words in a doc, hashed and sorted; none cross from one part to the next. */
    static long[] runs(Doc d) {
        LongOpenHashSet out = new LongOpenHashSet();
        long[] last = new long[WORDS];
        for (String part : d.parts()) {
            int n = 0;
            int i = 0, len = part.length();
            while (i < len) {
                while (i < len && !wordChar(part.charAt(i))) {
                    i++;
                }
                if (i >= len) {
                    break;
                }
                long h = 0xcbf29ce484222325L;
                while (i < len && wordChar(part.charAt(i))) {
                    h = (h ^ Character.toLowerCase(part.charAt(i))) * 1099511628211L;
                    i++;
                }
                last[n++ % WORDS] = h;
                if (n >= WORDS) {
                    long r = 0;
                    for (int k = 0; k < WORDS; k++) {
                        r = r * 31 + last[(n - WORDS + k) % WORDS];
                    }
                    out.add(r);
                }
            }
        }
        long[] sorted = out.toLongArray();
        Arrays.sort(sorted);
        return sorted;
    }

    private static boolean wordChar(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '\'';
    }

    private static long[][] mix() {
        Random rng = new Random(0x5EEDL);
        long[][] m = new long[HASHES][2];
        for (long[] a : m) {
            a[0] = rng.nextLong() | 1;
            a[1] = rng.nextLong();
        }
        return m;
    }

    /** For each hash, the smallest value over the runs. */
    static int[] signature(long[] runs, long[][] mix) {
        int[] sign = new int[HASHES];
        for (int k = 0; k < HASHES; k++) {
            long a = mix[k][0], b = mix[k][1], least = Long.MAX_VALUE;
            for (long r : runs) {
                long v = (a * r + b) >>> 1;
                if (v < least) {
                    least = v;
                }
            }
            sign[k] = (int) (least >>> 31);
        }
        return sign;
    }

    private static long band(int[] sign, int b, int rows) {
        long h = 0xcbf29ce484222325L + b;
        for (int r = 0; r < rows; r++) {
            h = (h ^ sign[b * rows + r]) * 1099511628211L;
        }
        return h;
    }

    /** Runs shared over runs in either, of two sorted lists. */
    static double jaccard(long[] a, long[] b) {
        int i = 0, j = 0, both = 0;
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                both++;
                i++;
                j++;
            } else if (a[i] < b[j]) {
                i++;
            } else {
                j++;
            }
        }
        return (double) both / (a.length + b.length - both);
    }
}
