package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Muon;
import io.github.metrovoc.residual.tensor.Numerics;
import io.github.metrovoc.residual.tensor.Optimizer;

/**
 * How a run is trained, beside the learning rate, batch and clip: when the rate rises and falls and how long the run lasts,
 * what its numbers are rounded to, what moves the weights, and how its text is cut into rows.
 *
 * @param warmup steps the rate takes to rise to its full size
 * @param length steps the run lasts, or 0 for a run with no planned end; a schedule that falls needs one
 */
public record Recipe(Schedule schedule, int warmup, int length, Numerics.Format precision, Moves moves, Packing packing) {
    public enum Schedule {
        CONSTANT, COSINE, WSD;

        public final String id = name().toLowerCase(java.util.Locale.ROOT);
    }

    public enum Moves {
        ADAMW, MUON;

        public final String id = name().toLowerCase(java.util.Locale.ROOT);
    }

    public enum Packing {
        /** Each row a window of one stretch of text; stretches shorter than a row go unread. */
        WINDOWS,
        /** Rows filled from several stretches, the seams not scored, attention free to look across them. */
        PACKED,
        /** Packed, and attention, delay lines and positions all stop at the seams. */
        MASKED;

        public final String id = name().toLowerCase(java.util.Locale.ROOT);
    }

    public static final int[] WARMUPS = {0, 20, 100, 500};
    public static final int[] LENGTHS = {0, 1000, 2000, 4000, 8000, 16000, 32000};
    /** What the old fixed warmup was: the plain recipe. */
    public static final Recipe PLAIN = new Recipe(Schedule.CONSTANT, 20, 0, Numerics.Format.FP32, Moves.ADAMW, Packing.WINDOWS);

    /** What the learning rate is multiplied by at step {@code step}, counted from 0. A cosine falls from the end of warmup, warmup-stable-decay over the run's last fifth. */
    public float multiplier(int step) {
        if (schedule == Schedule.CONSTANT || length == 0) {
            return multiplier(step, warmup, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        return multiplier(step, warmup, schedule == Schedule.COSINE ? warmup : length * 4 / 5, length);
    }

    /** The Bench's Schedule spec: warmup to step {@code w}, then a half cosine from step {@code s} down to 0 at step {@code t}. */
    public static float multiplier(int step, int w, int s, int t) {
        float rise = Math.min(1f, (step + 1f) / w);
        double along = Math.max(0, Math.min(1, (double) (step - s) / Math.max(t - s, 1)));
        return (float) (rise * 0.5 * (1 + Math.cos(Math.PI * along)));
    }

    public Optimizer optimizer(CompiledGraph model) {
        return moves == Moves.MUON ? new Muon(model.hiddenMatrices(), model.otherParameters()) : new AdamW(model.parameters());
    }
}
