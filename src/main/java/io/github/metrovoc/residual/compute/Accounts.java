package io.github.metrovoc.residual.compute;

/**
 * The sums labs plan with: what a run costs in flops, memory and energy, for a run counted as it happens and for one too
 * large to run. {@link RunBook} keeps a real run's counts; a {@link Capacity} says what a block's compute can do, and
 * {@link #book} says what a run would cost on it.
 */
public final class Accounts {
    /** A parameter in training: itself, its gradient and Adam's two running averages, four bytes each. */
    public static final int ADAM_BYTES = 16;
    /** A parameter when a model only answers. */
    public static final int WEIGHT_BYTES = 4;
    /** Flops a parameter takes per token: two forward and four back. */
    public static final int FLOPS_PER_PARAMETER_TOKEN = 6;
    private static final double SECONDS_A_DAY = 86_400;

    private Accounts() {
    }

    /** What a model takes to train with Adam. */
    public static long trainingBytes(long parameters) {
        return parameters * ADAM_BYTES;
    }

    /** What a model takes to train at another number of bytes a parameter: another optimizer or number format. */
    public static long trainingBytes(long parameters, int bytesPerParameter) {
        return parameters * bytesPerParameter;
    }

    /** The largest model that fits in {@code bytes} to train with Adam. */
    public static long largestModel(long bytes) {
        return bytes / ADAM_BYTES;
    }

    /** Flops to train {@code parameters} on {@code tokens}: 6ND. */
    public static double flops(long parameters, double tokens) {
        return FLOPS_PER_PARAMETER_TOKEN * (double) parameters * tokens;
    }

    /** Energy for {@code flops} at {@code flopsPerFe}, in FE, with cooling on top: the total, IT energy times PUE. */
    public static double energy(double flops, double flopsPerFe, double pue) {
        return flops / flopsPerFe * pue;
    }

    /** Ticks in which {@code flops} get done at {@code flopsPerSecond}. */
    public static double ticks(double flops, double flopsPerSecond) {
        return flops / flopsPerSecond * 20;
    }

    /** What a block's compute can do: every device it can reach, rated. */
    public record Capacity(int devices, double flopsPerSecond, long memoryBytes, double flopsPerFe, double pue) {
        public static final Capacity NONE = new Capacity(0, 0, 0, 1, 1);
    }

    /**
     * What a run would cost: a prediction, by the sums above.
     *
     * @param bytes  memory it takes to train
     * @param fits   whether {@code bytes} fit in the capacity
     * @param energy FE, cooling included
     * @param days   real days at the capacity's full rate
     */
    public record Booking(long parameters, double tokens, double flops, long bytes, boolean fits, double energy, double days) {
    }

    public static Booking book(long parameters, double tokens, Capacity at) {
        double flops = flops(parameters, tokens);
        long bytes = trainingBytes(parameters);
        return new Booking(parameters, tokens, flops, bytes, bytes <= at.memoryBytes(), energy(flops, at.flopsPerFe(), at.pue()),
                at.flopsPerSecond() <= 0 ? Double.POSITIVE_INFINITY : flops / at.flopsPerSecond() / SECONDS_A_DAY);
    }

    /** Pipeline parallelism's bubbles with level stages: a prediction, which real stages never quite meet. */
    public static double levelBubble(int stages, int microbatches) {
        return PipelineParallel.levelBubble(stages, microbatches);
    }
}
