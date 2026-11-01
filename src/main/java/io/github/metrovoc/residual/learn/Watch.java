package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Arrays;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * What a Watch Post asks of a model: having learned how things go when all is well, how surprised is it by what a thing did at
 * each look, one digit a look as a Port writes them? Things work in rhythms, so a thing that stops, runs away or slows surprises a
 * model that has read how its line went before, where a quiet stretch alone tells a rule nothing.
 */
public final class Watch {
    /** The longest line a model reads. */
    public static final int LINE = Vocab.CONTEXT;
    /** The shortest it is worth asking about. */
    public static final int READS = 24;
    /** Looks of nothing after which the rule says a thing that was at work has stalled: two minutes. */
    public static final int RULE = 24;
    /** Looks it goes by; a look counts for the nats over {@link #CUT} it surprised the model by, and this many in all raise the alarm. */
    private static final int TAIL = 16;
    private static final double CUT = 0.5, ALARM = 6;
    /** How surprising a line may be on average before the tail, in nats, before a model is taken not to know this thing at all. */
    private static final double LOST = 3;

    /** What a model makes of a line. */
    public enum Verdict { FINE, WRONG, UNSURE }

    enum Fault { STOPS, RUNS_AWAY, SLOWS }

    /** A thing's line, and what has gone wrong with it, if anything. */
    record Example(String line, @Nullable Fault fault) {
    }

    private Watch() {
    }

    /** The rule when no model is sure: it was at work, and it has done nothing for two minutes. */
    public static boolean stalled(String line) {
        int quiet = 0;
        while (quiet < line.length() && line.charAt(line.length() - 1 - quiet) == '0') {
            quiet++;
        }
        return quiet >= RULE && quiet < line.length();
    }

    /** How surprised {@code model} is by each look of {@code line}, in nats; the first has nothing before it to go by. */
    public static float[] surprise(CompiledGraph model, String line) {
        int[] ids = model.env.tokenizer().spell(line);
        float[] out = new float[ids.length];
        Tensor logits = model.forward(null, new Tokens(ids, 1, ids.length), null);
        int v = logits.dim(-1);
        for (int i = 0; i + 1 < ids.length; i++) {
            float[] row = Arrays.copyOfRange(logits.data, i * v, (i + 1) * v);
            out[i + 1] = (float) -Math.log(Math.max(1e-6f, Ops.softmax(null, Tensor.of(new int[]{v}, row)).data[ids[i + 1]]));
        }
        return out;
    }

    /** What {@code surprise}, a line's looks as {@link #surprise} gives them, says of the thing. */
    public static Verdict judge(float[] surprise) {
        int tail = Math.max(1, surprise.length - TAIL);
        double before = 0;
        for (int i = 1; i < tail; i++) {
            before += surprise[i];
        }
        if (before / Math.max(1, tail - 1) > LOST) {
            return Verdict.UNSURE;
        }
        double over = 0;
        for (int i = tail; i < surprise.length; i++) {
            over += Math.max(0, surprise[i] - CUT);
        }
        return over >= ALARM ? Verdict.WRONG : Verdict.FINE;
    }

    /** The verdict a Watch Post goes by: the model's if it has one, or else the rule's. */
    static boolean wrong(CompiledGraph model, String line) {
        Verdict verdict = judge(surprise(model, line));
        return verdict == Verdict.UNSURE ? stalled(line) : verdict == Verdict.WRONG;
    }

    static Example example(Random rng, boolean test) {
        Demand.Rhythm rhythm = Demand.Rhythm.draw(rng, test);
        Fault fault = rng.nextBoolean() ? null : Fault.values()[rng.nextInt(Fault.values().length)];
        // the fault shows for two rounds of the rhythm, or three to slow, on a line long enough to show the rhythm first
        int since = (rhythm.steady() ? 4 : (fault == Fault.SLOWS ? 3 : 2) * rhythm.period() + rhythm.width()) + rng.nextInt(3);
        int length = Math.min(LINE, Math.max(READS + rng.nextInt(LINE - READS + 1), rhythm.shown() + since));
        StringBuilder looks = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int d = rhythm.digit(i, 1);
            if (fault != null && i >= length - since) {
                d = switch (fault) {
                    case STOPS -> 0;
                    case RUNS_AWAY -> rhythm.steady() ? (rhythm.burst() + 2) % 5 : rhythm.burst();
                    case SLOWS -> rhythm.steady() ? (i % 2 == 0 ? 0 : rhythm.burst()) : rhythm.digit(i, 2);
                };
            }
            looks.append(Demand.slip(rng, d));
        }
        return new Example(looks.toString(), fault);
    }
}
