package io.github.metrovoc.residual.tensor;

/** What the trainer asks of an optimizer. */
public interface Optimizer {
    /** Scales gradients so their global norm is at most {@code maxNorm}; returns the norm before clipping. */
    double clip(double maxNorm);

    void step(float lr);

    void zeroGrad();

    /** How hard weight matrices are pulled toward zero, as a share of the learning rate. */
    void weightDecay(float decay);
}
