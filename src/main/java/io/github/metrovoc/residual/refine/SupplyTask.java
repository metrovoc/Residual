package io.github.metrovoc.residual.refine;

import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Vocab;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Practice for Reading from a refined supply: each batch comes from one domain, drawn by the mix. Once a run has gone
 * {@link Supply#after} steps it anneals: practice reads the chosen domain alone, at a fifth of the rate.
 */
public final class SupplyTask extends Task {
    private final Supply supply;
    private final TextTask[] byDomain = new TextTask[Domain.values().length];
    private final float[] shares;
    private int served;

    /** The same practice with its own count of steps. */
    private SupplyTask(SupplyTask from) {
        super(from.vocab, from.context, from.metric, from.target);
        supply = from.supply;
        System.arraycopy(from.byDomain, 0, byDomain, 0, byDomain.length);
        shares = from.shares;
    }

    public SupplyTask fresh() {
        return new SupplyTask(this);
    }

    public SupplyTask(Supply supply, Vocab vocab) {
        super(vocab, Vocab.CONTEXT, Metric.LOSS, Benchmark.READING.target);
        this.supply = supply;
        float[] chars = new float[byDomain.length];
        for (Domain d : Domain.values()) {
            List<String> texts = supply.texts(d);
            if (!texts.isEmpty()) {
                TextTask t = new TextTask(texts, target, vocab);
                if (!t.isEmpty()) {
                    byDomain[d.ordinal()] = t;
                    chars[d.ordinal()] = t.characters() * supply.mix()[d.ordinal()];
                }
            }
        }
        float total = 0;
        for (float c : chars) {
            total += c;
        }
        shares = new float[chars.length];
        for (int i = 0; i < chars.length; i++) {
            shares[i] = total == 0 ? 0 : chars[i] / total;
        }
    }

    public boolean isEmpty() {
        for (float s : shares) {
            if (s > 0) {
                return false;
            }
        }
        return true;
    }

    public boolean annealing() {
        return supply.anneal() >= 0 && served >= supply.after() && byDomain[supply.anneal()] != null;
    }

    @Override
    public float rateScale() {
        return annealing() ? Supply.ANNEAL_RATE : 1f;
    }

    @Override
    public List<String> heldOutTexts() {
        List<String> out = new ArrayList<>();
        for (TextTask t : byDomain) {
            if (t != null) {
                out.addAll(t.heldOutTexts());
            }
        }
        return out;
    }

    @Override
    public Batch batch(Random rng, int size, boolean test) {
        int d = -1;
        if (!test && annealing()) {
            d = supply.anneal();
        } else {
            float r = rng.nextFloat();
            for (int i = 0; i < shares.length; i++) {
                if (shares[i] > 0) {
                    d = i;
                    r -= shares[i];
                    if (r < 0) {
                        break;
                    }
                }
            }
        }
        if (!test) {
            served++;
        }
        return byDomain[d].batch(rng, size, test);
    }

    /** Carries on where {@code other} left off: the slots of a Core change without its run starting over. */
    public void carryOn(SupplyTask other) {
        served = other.served;
    }

    public Supply supply() {
        return supply;
    }

    public int served() {
        return served;
    }
}
