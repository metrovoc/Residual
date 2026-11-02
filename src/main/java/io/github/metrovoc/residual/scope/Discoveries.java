package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Modular;
import org.jetbrains.annotations.Nullable;

/**
 * What a Model Core notices about a model as it trains, from what the model really does. Both things it watches for take a before and
 * an after in one model's life, so it keeps what it has seen with the model, as the Core keeps its weights.
 *
 * <p>An induction head forming: untrained, a model copies about one letter in twenty of a run it reads twice and its heads put 4% of
 * their attention on what followed the same letter last time, as chance does. In twenty-two runs of six designs, the Pattern Dataset took
 * models from under 0.1 and 0.15 to over 0.65 and 0.6 within about two hundred steps, and to over 0.9 soon after, while nothing that doesn't
 * copy came near: a transformer trained on Recall reached 0.21 of attention and copied nothing. {@link #LOW} and {@link #COPIES}
 * and {@link #PREFIX} lie in the empty middle (see systems.md).
 *
 * <p>A model that groks: it knows the sums it has practised and none it hasn't, for a long stretch of steps, and then gets the rest right
 * too. A model that learns the rule straight away, as a transformer under very strong decay does, passes without ever knowing the one and
 * not the other, and earns nothing here.
 */
public final class Discoveries {
    /** A model copying no more than this has not formed one yet; one copying at least {@link #COPIES} with a head at least {@link #PREFIX} has. */
    public static final float LOW = 0.25f, COPIES = 0.5f, PREFIX = 0.5f;
    /** Steps between looks at each. */
    public static final int READ_EVERY = 50, SUMS_EVERY = 250;
    /** Knowing the sums practised means this share right; knowing none of the others, at most this; and having the rule, this. */
    public static final float KNOWS = 0.8f, BLIND = 0.3f, GROKKED = 0.9f;
    /** The shortest stretch of knowing one and not the other that counts, and at least half as long again as it took to learn the one. */
    public static final int STRETCH = 1000;

    /** What it found: an induction head at a node, or the sums. */
    public record Found(String id, int node, float first, float second) {
    }

    /** What has been seen of one model: only the training thread writes it during a run. */
    public static final class Seen {
        public volatile boolean notCopying;
        public volatile boolean formed;
        public volatile int knewAt = -1;
        public volatile int blindUntil = -1;
        public volatile boolean grokked;
    }

    private Discoveries() {
    }

    /** Takes one look at how it reads runs twice. */
    @Nullable
    public static Found readRuns(CompiledGraph model, Seen seen) {
        if (seen.formed) {
            return null;
        }
        Heads.Reading r = Heads.read(model);
        if (r.copied() <= LOW) {
            seen.notCopying = true;
            return null;
        }
        Heads.Head head = r.strongest();
        if (seen.notCopying && r.copied() >= COPIES && head != null && head.prefix() >= PREFIX) {
            seen.formed = true;
            return new Found("phase_change", head.node(), r.copied(), head.prefix());
        }
        return null;
    }

    /** Takes one look at how it does its sums, every pair asked, at {@code step}. */
    @Nullable
    public static Found readSums(CompiledGraph model, int step, Seen seen) {
        if (seen.grokked) {
            return null;
        }
        return sums(step, (float) Modular.right(model, false), (float) Modular.right(model, true), seen);
    }

    /** What a look at the sums means: the shares right of those practised and of the rest. */
    @Nullable
    public static Found sums(int step, float practised, float others, Seen seen) {
        if (others <= BLIND) {
            if (practised >= KNOWS) {
                if (seen.knewAt < 0) {
                    seen.knewAt = step;
                }
                seen.blindUntil = step;
            }
            return null;
        }
        if (others >= GROKKED && seen.knewAt >= 0 && seen.blindUntil - seen.knewAt >= Math.max(STRETCH, seen.knewAt / 2)) {
            seen.grokked = true;
            return new Found("grokked", -1, practised, others);
        }
        return null;
    }
}
