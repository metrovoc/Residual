package io.github.metrovoc.residual.compute;

/** The sums labs plan with: what a run costs in flops and memory. {@link RunMeter} keeps a real run's counts. */
public final class Accounts {
    /** A parameter in training: itself, its gradient and Adam's two running averages, four bytes each. */
    public static final int ADAM_BYTES = 16;

    private Accounts() {
    }

    /** What a model takes to train with Adam. */
    public static long trainingBytes(long parameters) {
        return parameters * ADAM_BYTES;
    }
}
