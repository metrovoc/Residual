package io.github.metrovoc.residual.graph;

import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.List;

/** What machines add to the loss during a training pass, beside the scores they hand on. */
public final class Extras {
    /** What z-loss counts for: a row's log²Z of 100 weighs 0.01. */
    public static final float Z_WEIGHT = 1e-4f;

    public final List<Tensor> penalties = new ArrayList<>();
}
