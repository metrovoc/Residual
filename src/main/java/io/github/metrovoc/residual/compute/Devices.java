package io.github.metrovoc.residual.compute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntConsumer;

/**
 * Threads acting as devices: each runs its share of a step on its own, with nothing shared but what the split hands
 * across. They keep the times the split's {@link Timing} is made from.
 */
final class Devices implements AutoCloseable {
    private static final AtomicInteger IDS = new AtomicInteger();
    final int count;
    private final LongAdder flops;
    private final ExecutorService threads;
    private final long[] busy;
    private final List<long[]> spans = new ArrayList<>();
    private long started;
    private long collective;

    Devices(int count, LongAdder flops) {
        this.count = count;
        this.flops = flops;
        this.busy = new long[count];
        this.threads = Executors.newFixedThreadPool(count, r -> {
            Thread t = new Thread(r, "Residual Device #" + IDS.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    /** Runs {@code work} once on each of the first {@code n} devices and waits for them all. */
    void all(int n, IntConsumer work) {
        CountDownLatch done = new CountDownLatch(n);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        for (int d = 0; d < n; d++) {
            int device = d;
            threads.execute(() -> {
                try {
                    Flops.with(flops, () -> work.accept(device));
                } catch (Throwable t) {
                    failed.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        Throwable t = failed.get();
        if (t != null) {
            throw t instanceof RuntimeException r ? r : new IllegalStateException(t);
        }
    }

    /** A step starts. */
    void begin() {
        started = System.nanoTime();
        collective = 0;
        Arrays.fill(busy, 0);
        synchronized (spans) {
            spans.clear();
        }
    }

    /** Device {@code d} did work of {@code kind} from {@code from} until now. */
    void worked(int d, Timing.Kind kind, long from) {
        long now = System.nanoTime();
        // only device d writes its own slot
        busy[d] += now - from;
        synchronized (spans) {
            spans.add(new long[] {d, kind.ordinal(), from, now});
        }
    }

    /** The step spent the time since {@code from} in a collective. */
    void collective(long from) {
        collective += System.nanoTime() - from;
    }

    /** The step that began has ended. */
    Timing end(long linkBytes) {
        long wall = Math.max(1, System.nanoTime() - started);
        List<Timing.Span> out = new ArrayList<>();
        synchronized (spans) {
            for (long[] s : spans) {
                out.add(new Timing.Span((int) s[0], Timing.Kind.values()[(int) s[1]], (s[2] - started) / (float) wall, (s[3] - started) / (float) wall));
            }
        }
        return new Timing(wall, busy.clone(), collective, linkBytes, List.copyOf(out));
    }

    @Override
    public void close() {
        threads.shutdown();
    }
}
