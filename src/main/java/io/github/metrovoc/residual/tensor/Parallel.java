package io.github.metrovoc.residual.tensor;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Splits loops across the worker pool bound to the current thread. Training jobs bind a pool sized by the
 * server config; anything else (unit tests, the main thread) runs sequentially.
 */
public final class Parallel {
    private static final ThreadLocal<ForkJoinPool> POOL = new ThreadLocal<>();
    private static final long MIN_WORK = 32_768;

    private Parallel() {
    }

    @FunctionalInterface
    public interface RangeBody {
        void run(int from, int to);
    }

    public static void with(ForkJoinPool pool, Runnable body) {
        ForkJoinPool previous = POOL.get();
        POOL.set(pool);
        try {
            body.run();
        } finally {
            POOL.set(previous);
        }
    }

    /** Runs {@code body} over [0, n), in parallel when the bound pool exists and the work is large enough. */
    public static void range(int n, long workPerItem, RangeBody body) {
        ForkJoinPool pool = POOL.get();
        if (pool == null || n < 2 || (long) n * workPerItem < MIN_WORK || pool.getParallelism() < 2) {
            body.run(0, n);
            return;
        }
        int chunks = Math.min(n, pool.getParallelism() * 3);
        pool.invoke(new Chunk(body, 0, n, Math.max(1, n / chunks)));
    }

    private static final class Chunk extends RecursiveAction {
        private final RangeBody body;
        private final int from;
        private final int to;
        private final int grain;

        Chunk(RangeBody body, int from, int to, int grain) {
            this.body = body;
            this.from = from;
            this.to = to;
            this.grain = grain;
        }

        @Override
        protected void compute() {
            if (to - from <= grain) {
                body.run(from, to);
                return;
            }
            int mid = (from + to) >>> 1;
            invokeAll(new Chunk(body, from, mid, grain), new Chunk(body, mid, to, grain));
        }
    }
}
