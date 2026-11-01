package io.github.metrovoc.residual.compute;

import java.util.List;

/**
 * How one training step ran across its devices, as measured.
 *
 * @param wallNanos       the step from its first device starting to its last finishing
 * @param busyNanos       how long each device worked in that time
 * @param collectiveNanos how long the step spent in collectives: reducing gradients, sending weights back out, meeting at
 *                        the end of a split multiply
 * @param linkBytes       what would have crossed links between devices that did not share memory
 * @param spans           the work of each device through the step, in order; empty where work is too fine to draw
 */
public record Timing(long wallNanos, long[] busyNanos, long collectiveNanos, long linkBytes, List<Span> spans) {
    /** What a stretch of a device's time went on. */
    public enum Kind { FORWARD, BACKWARD, COLLECTIVE }

    /** A stretch of work on {@code device}, from and to as shares of the step. */
    public record Span(int device, Kind kind, float from, float to) {
    }

    public int devices() {
        return busyNanos.length;
    }

    /** The share of device time that went on work: 1 when every device worked all step. */
    public double utilization() {
        long busy = 0;
        for (long b : busyNanos) {
            busy += b;
        }
        return wallNanos == 0 || busyNanos.length == 0 ? 0 : Math.min(1, (double) busy / (wallNanos * busyNanos.length));
    }

    /** The share of device time spent waiting: for pipeline parallelism, its bubbles. */
    public double bubble() {
        return 1 - utilization();
    }
}
