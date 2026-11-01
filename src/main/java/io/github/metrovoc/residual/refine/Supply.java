package io.github.metrovoc.residual.refine;

import java.util.ArrayList;
import java.util.List;

/**
 * Text on its way to a Model Core: what has been kept of it, and how it is to be drawn.
 *
 * @param docs    the text
 * @param mix     how much more or less than its share of the text each domain is drawn, by domain
 * @param anneal  the domain the last of a run reads alone, or -1
 * @param after   steps before that
 */
public record Supply(List<Doc> docs, float[] mix, int anneal, int after) {
    public static final int NO_ANNEAL = -1;
    /** The rate during annealing, of the rate set. */
    public static final float ANNEAL_RATE = 0.2f;

    /** Text as it comes: every domain as much as there is of it. */
    public static Supply plain(List<Doc> docs) {
        float[] mix = new float[Domain.values().length];
        java.util.Arrays.fill(mix, 1f);
        return new Supply(docs, mix, NO_ANNEAL, 0);
    }

    public Supply withDocs(List<Doc> kept) {
        return new Supply(kept, mix, anneal, after);
    }

    public List<String> texts(Domain domain) {
        List<String> out = new ArrayList<>();
        for (Doc d : docs) {
            if (d.domain() == domain) {
                out.addAll(d.parts());
            }
        }
        return out;
    }

    /** How many characters each domain has. */
    public long[] chars() {
        long[] by = new long[Domain.values().length];
        for (Doc d : docs) {
            by[d.domain().ordinal()] += d.chars();
        }
        return by;
    }

    /** The share of practice each domain gets, of 1. */
    public float[] shares() {
        long[] chars = chars();
        double total = 0;
        for (int i = 0; i < chars.length; i++) {
            total += chars[i] * mix[i];
        }
        float[] out = new float[chars.length];
        for (int i = 0; i < chars.length; i++) {
            out[i] = total == 0 ? 0 : (float) (chars[i] * mix[i] / total);
        }
        return out;
    }
}
