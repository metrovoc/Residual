package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.Decoder;
import io.github.metrovoc.residual.model.Serving;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Writing with a draft: a small model proposes a few tokens, and the model being served reads them all in one pass. A proposed token
 * stands with probability {@code min(1, p / q)}, p being how likely the served model makes it and q the draft; the first that doesn't
 * is drawn again from what the served model wants beyond what the draft gave it, and the rest are dropped. What comes out follows the
 * served model's own probabilities exactly, whatever the draft is like. A good draft only means fewer passes. A greedy answer is the
 * same rule at no temperature: a proposal stands only if it is what the served model would have written.
 */
public final class Speculative {
    private Speculative() {
    }

    /**
     * Writes after {@code prompt} until {@code more} says stop, one {@link Sampler.Step} at a time: the likeliest token each time if
     * {@code greedy}, or else as {@code temperature} has it.
     */
    public static void write(Serving serving, int[] prompt, float temperature, boolean greedy, Random rng, Predicate<Sampler.Step> more) {
        int context = Vocab.CONTEXT, v = serving.vocab();
        int[] seq = prompt.clone();
        int n = seq.length;
        while (true) {
            // read as far back as a plain pass would, and carry on from there
            int[] window = Arrays.copyOfRange(seq, Sampler.start(n, context), n);
            Decoder.Sequence target = serving.targetSequence(), draft = serving.draftSequence();
            float[] rows = serving.targetPass(target, window);
            float[] p = Sampler.probs(rows, rows.length - v, v, temperature);
            rows = serving.draftPass(draft, window);
            float[] q = Sampler.probs(rows, rows.length - v, v, temperature);
            while (true) {
                int k = Math.min(Serving.DRAFT, context - target.length() - 1);
                int written;
                float[] now = p;
                int stood = 0;
                if (k > 0) {
                    // the draft's proposals, and how likely it made each
                    int[] proposals = new int[k];
                    float[][] qs = new float[k][];
                    for (int i = 0; i < k; i++) {
                        qs[i] = q;
                        proposals[i] = greedy ? Trainer.argmax(q, 0, v) : Sampler.draw(q, rng);
                        q = Sampler.probs(serving.draftPass(draft, new int[]{proposals[i]}), 0, v, temperature);
                    }
                    rows = serving.targetPass(target, proposals);
                    while (stood < k) {
                        int x = proposals[stood];
                        boolean stands = greedy ? x == Trainer.argmax(now, 0, v) : rng.nextFloat() * qs[stood][x] < now[x];
                        if (!stands) {
                            break;
                        }
                        seq = append(seq, n++, x);
                        if (!more.test(step(x, now))) {
                            serving.drafted(k, stood + 1);
                            return;
                        }
                        now = Sampler.probs(rows, stood * v, v, temperature);
                        stood++;
                    }
                    serving.drafted(k, stood);
                    written = stood < k ? (greedy ? Trainer.argmax(now, 0, v) : residual(now, qs[stood], rng))
                            : (greedy ? Trainer.argmax(now, 0, v) : Sampler.draw(now, rng));
                    if (stood < k) {
                        // what followed the first that fell is gone
                        int kept = target.length() - k + stood;
                        target = target.fork(kept);
                        draft = draft.fork(kept);
                    }
                } else {
                    written = greedy ? Trainer.argmax(now, 0, v) : Sampler.draw(now, rng);
                }
                seq = append(seq, n++, written);
                if (!more.test(step(written, now))) {
                    return;
                }
                if (target.length() >= context) {
                    break;
                }
                int[] last = {written};
                p = Sampler.probs(serving.targetPass(target, last), 0, v, temperature);
                q = Sampler.probs(serving.draftPass(draft, last), 0, v, temperature);
            }
        }
    }

    private static int[] append(int[] seq, int n, int token) {
        int[] out = n < seq.length ? seq : Arrays.copyOf(seq, Math.max(16, seq.length * 2));
        out[n] = token;
        return out;
    }

    private static Sampler.Step step(int token, float[] p) {
        int[] top = Sampler.top(p, 5);
        float[] topP = new float[top.length];
        for (int i = 0; i < top.length; i++) {
            topP[i] = p[top[i]];
        }
        return new Sampler.Step(token, p[token], top, topP);
    }

    /** A draw from what {@code p} gives beyond {@code q}: the served model's own share of a rejected proposal's place. */
    static int residual(float[] p, float[] q, Random rng) {
        float sum = 0;
        for (int i = 0; i < p.length; i++) {
            sum += Math.max(0, p[i] - q[i]);
        }
        if (sum <= 0) {
            return Sampler.draw(p, rng);
        }
        float r = rng.nextFloat() * sum;
        for (int i = 0; i < p.length; i++) {
            r -= Math.max(0, p[i] - q[i]);
            if (r <= 0 && p[i] > q[i]) {
                return i;
            }
        }
        return Trainer.argmax(p, 0, p.length);
    }
}
