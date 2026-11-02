package io.github.metrovoc.residual.refine;

import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Recipe;
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

    private final Recipe.Packing packing;

    /** The same encoded text, with its own row settings and count of steps. */
    private SupplyTask(SupplyTask from, Recipe.Packing packing) {
        super(from.vocab, from.context, from.metric, from.target);
        supply = from.supply;
        this.packing = packing;
        for (int i = 0; i < byDomain.length; i++) {
            if (from.byDomain[i] != null) {
                byDomain[i] = from.byDomain[i].withPacking(packing);
            }
        }
        shares = shares();
    }

    public SupplyTask fresh() {
        return fresh(packing);
    }

    public SupplyTask fresh(Recipe.Packing packing) {
        return new SupplyTask(this, packing);
    }

    public SupplyTask(Supply supply, Vocab vocab) {
        this(supply, vocab, Recipe.Packing.WINDOWS);
    }

    public SupplyTask(Supply supply, Vocab vocab, Recipe.Packing packing) {
        super(vocab, Vocab.CONTEXT, Metric.LOSS, Benchmark.READING.target);
        this.supply = supply;
        this.packing = packing;
        for (Domain d : Domain.values()) {
            List<String> texts = supply.texts(d);
            if (!texts.isEmpty()) {
                byDomain[d.ordinal()] = new TextTask(texts, target, vocab).withPacking(packing);
            }
        }
        shares = shares();
    }

    private float[] shares() {
        float[] chars = new float[byDomain.length];
        float total = 0;
        for (int i = 0; i < chars.length; i++) {
            TextTask text = byDomain[i];
            if (text != null && !text.isEmpty()) {
                chars[i] = text.characters() * supply.mix()[i];
                total += chars[i];
            }
        }
        float[] shares = new float[chars.length];
        for (int i = 0; i < chars.length; i++) {
            shares[i] = total == 0 ? 0 : chars[i] / total;
        }
        return shares;
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
        return supply.anneal() >= 0 && served >= supply.after() && byDomain[supply.anneal()] != null
                && !byDomain[supply.anneal()].isEmpty();
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
