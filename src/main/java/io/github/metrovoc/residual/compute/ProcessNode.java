package io.github.metrovoc.residual.compute;

/**
 * What a device was printed at. Each node down holds eight times the memory and runs four times the flops, each flop
 * on half the energy, so at full load a device draws twice the power of the one before.
 *
 * @param flopsPerSecond what a device runs at full load
 * @param memoryBytes    what a device holds
 * @param flopsPerFe     the flops a unit of energy pays for
 */
public record ProcessNode(String id, int nanometres, double flopsPerSecond, long memoryBytes, double flopsPerFe) {
    public static final ProcessNode N90 = new ProcessNode("90", 90, 10e9, 512L << 10, 6.25e6);
    public static final ProcessNode N28 = new ProcessNode("28", 28, 40e9, 4L << 20, 12.5e6);
    public static final ProcessNode N7 = new ProcessNode("7", 7, 160e9, 32L << 20, 25e6);
    public static final ProcessNode N3 = new ProcessNode("3", 3, 640e9, 256L << 20, 50e6);
    public static final ProcessNode[] ALL = {N90, N28, N7, N3};

    /** What a device draws at full load, in FE a tick. */
    public int fePerTick() {
        return (int) Math.round(flopsPerSecond / 20 / flopsPerFe);
    }

    /** What a device counts for beside the first node's, for what takes compute but runs no model of its own. */
    public int units() {
        return (int) Math.round(flopsPerSecond / N90.flopsPerSecond);
    }
}
