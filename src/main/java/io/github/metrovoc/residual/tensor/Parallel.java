package io.github.metrovoc.residual.tensor;

import io.github.metrovoc.residual.compute.Timing;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Splits loops across the worker pool bound to the current thread. Training jobs bind a pool sized by the
 * server config; anything else (unit tests, the main thread) runs sequentially.
 */
public final class Parallel {
    private static final ThreadLocal<ForkJoinPool> POOL = new ThreadLocal<>();
    private static final ThreadLocal<Slices> SLICES = new ThreadLocal<>();
    private static final long MIN_WORK = 32_768;

    private Parallel() {
    }

    @FunctionalInterface
    public interface RangeBody {
        void run(int from, int to);
    }

    public static void with(ForkJoinPool pool, Runnable body) {
        with(pool, null, body);
    }

    /** As {@link #with(ForkJoinPool, Runnable)}, and with {@code slices}, each multiply is split by its columns among devices. */
    public static void with(ForkJoinPool pool, Slices slices, Runnable body) {
        ForkJoinPool previous = POOL.get();
        Slices before = SLICES.get();
        POOL.set(pool);
        SLICES.set(slices);
        try {
            body.run();
        } finally {
            POOL.set(previous);
            SLICES.set(before);
        }
    }

    /** Runs {@code body} over [0, n), in parallel when the bound pool exists and the work is large enough. */
    public static void range(int n, long workPerItem, RangeBody body) {
        ForkJoinPool pool = POOL.get();
        if (pool == null || n < 2 || (long) n * workPerItem < MIN_WORK || pool.getParallelism() < 2) {
            body.run(0, n);
            return;
        }
        Slices slices = SLICES.get();
        if (slices != null) {
            slices.split(pool, n, 0, body);
            return;
        }
        int chunks = Math.min(n, pool.getParallelism() * 3);
        pool.invoke(new Chunk(body, 0, n, Math.max(1, n / chunks)));
    }

    /**
     * With slices bound and enough work, runs {@code body} over the columns [0, n) of a result of {@code rows} rows, a slice
     * to a device, and returns true: {@code body} then computes those columns of every row, from those columns of the weights.
     */
    public static boolean columns(int rows, int n, long workPerColumn, RangeBody body) {
        ForkJoinPool pool = POOL.get();
        Slices slices = SLICES.get();
        if (slices == null || pool == null || n < 2 || (long) n * workPerColumn < MIN_WORK) {
            return false;
        }
        slices.split(pool, n, 4L * rows * n, body);
        return true;
    }

    /** The devices of a tensor-parallel run, and how each step went on them. */
    public static final class Slices {
        public final int devices;
        private final AtomicLongArray busy;
        private long started, meeting, bytes;

        public Slices(int devices) {
            this.devices = devices;
            this.busy = new AtomicLongArray(devices);
        }

        /** Each device does its slice of [0, n), and all meet at the end, gathering {@code gathered} bytes between them. */
        private void split(ForkJoinPool pool, int n, long gathered, RangeBody body) {
            int parts = Math.min(n, devices);
            List<ForkJoinTask<?>> tasks = new ArrayList<>(parts);
            long[] finished = new long[parts];
            for (int d = 0; d < parts; d++) {
                int device = d, from = n * d / parts, to = n * (d + 1) / parts;
                tasks.add(ForkJoinTask.adapt(() -> {
                    long at = System.nanoTime();
                    body.run(from, to);
                    long now = System.nanoTime();
                    busy.addAndGet(device, now - at);
                    finished[device] = now;
                }));
            }
            pool.invoke(new RecursiveAction() {
                @Override
                protected void compute() {
                    invokeAll(tasks);
                }
            });
            long end = System.nanoTime(), first = end;
            for (long f : finished) {
                first = Math.min(first, f);
            }
            // from the first to finish until the last has: the meeting at the end of this multiply
            meeting += end - first;
            bytes += gathered * (parts - 1) / parts;
        }

        /** A step starts. */
        public void begin() {
            started = System.nanoTime();
            meeting = bytes = 0;
            for (int d = 0; d < devices; d++) {
                busy.set(d, 0);
            }
        }

        /** The step that began has ended. */
        public Timing end() {
            long[] each = new long[devices];
            for (int d = 0; d < devices; d++) {
                each[d] = busy.get(d);
            }
            return new Timing(Math.max(1, System.nanoTime() - started), each, meeting, bytes, List.of());
        }
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
