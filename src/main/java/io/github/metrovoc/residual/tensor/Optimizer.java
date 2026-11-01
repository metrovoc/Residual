package io.github.metrovoc.residual.tensor;

/** What the trainer asks of an optimizer. */
public interface Optimizer {
    /** Scales gradients so their global norm is at most {@code maxNorm}; returns the norm before clipping. */
    double clip(double maxNorm);

    void step(float lr);

    void zeroGrad();
}
