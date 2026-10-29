package io.github.metrovoc.residual.learn;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.Arrays;

/**
 * Byte-pair encoding, learned a merge at a time: each step joins the two neighbouring tokens that occur together most
 * often in the text into one new token. Ties go to the pair of earlier tokens, so the same text always learns the
 * same merges.
 */
public final class Bpe {
    /** Each distinct chunk of the text, as its tokens so far. */
    private final int[][] words;
    /** How often each chunk occurs. */
    private final long[] counts;
    private final Long2LongOpenHashMap pairs = new Long2LongOpenHashMap();
    /** The chunks each pair occurs in (some may have lost it since). */
    private final Long2ObjectOpenHashMap<IntOpenHashSet> where = new Long2ObjectOpenHashMap<>();
    private final long characters;
    private long tokens;
    private Vocab vocab;

    /** Learns on {@code texts}, carrying on from the merges {@code start} already has. */
    public Bpe(Iterable<String> texts, Vocab start) {
        Object2LongOpenHashMap<String> chunks = new Object2LongOpenHashMap<>();
        for (String text : texts) {
            Vocab.chunks(text, c -> chunks.addTo(c, 1));
        }
        words = new int[chunks.size()][];
        counts = new long[chunks.size()];
        int w = 0;
        long chars = 0;
        for (Object2LongMap.Entry<String> e : chunks.object2LongEntrySet()) {
            words[w] = Vocab.WORLD.spell(e.getKey());
            counts[w] = e.getLongValue();
            chars += words[w].length * counts[w];
            count(w, 1);
            w++;
        }
        characters = chars;
        tokens = chars;
        int[] m = start.mergeIds();
        for (int i = 0; i < m.length; i += 2) {
            join(m[i], m[i + 1], Vocab.CHARACTERS + i / 2);
        }
        vocab = start;
    }

    /** The vocabulary learned so far. */
    public Vocab vocab() {
        return vocab;
    }

    /** Characters per token on the text it learns from. */
    public double charsPerToken() {
        return tokens == 0 ? 1 : (double) characters / tokens;
    }

    /** Learns one more merge; false once no two neighbouring tokens occur together twice. */
    public boolean step() {
        long best = 0, most = 1;
        for (Long2LongMap.Entry e : pairs.long2LongEntrySet()) {
            long c = e.getLongValue();
            if (c > most || c == most && c > 1 && e.getLongKey() < best) {
                best = e.getLongKey();
                most = c;
            }
        }
        if (most < 2) {
            return false;
        }
        int a = (int) (best >>> 32), b = (int) best;
        join(a, b, vocab.size());
        vocab = vocab.with(a, b);
        return true;
    }

    /** Joins every {@code a b} in the text into token {@code into}. */
    private void join(int a, int b, int into) {
        long p = Vocab.pair(a, b);
        IntOpenHashSet in = where.remove(p);
        if (in != null) {
            for (int w : in) {
                int[] t = words[w];
                count(w, -1);
                int n = Vocab.join(t, t.length, a, b, into);
                tokens -= (t.length - n) * counts[w];
                words[w] = Arrays.copyOf(t, n);
                count(w, 1);
            }
        }
        pairs.remove(p);
    }

    private void count(int w, int sign) {
        int[] t = words[w];
        for (int i = 0; i + 1 < t.length; i++) {
            long p = Vocab.pair(t[i], t[i + 1]);
            if (pairs.addTo(p, sign * counts[w]) + sign * counts[w] == 0) {
                pairs.remove(p);
            }
            if (sign > 0) {
                where.computeIfAbsent(p, k -> new IntOpenHashSet()).add(w);
            }
        }
    }
}
