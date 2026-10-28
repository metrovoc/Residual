package io.github.metrovoc.residual.train;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Every running training job, so world edits can stop the jobs they affect. Server thread only. */
public final class Jobs {
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    /** Also runs one-off work such as sampling, so it never blocks the server thread. */
    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Residual Trainer #" + THREAD_IDS.incrementAndGet());
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final List<TrainingJob> ACTIVE = new ArrayList<>();

    private Jobs() {
    }

    public static void start(TrainingJob job) {
        ACTIVE.add(job);
        EXECUTOR.execute(job::run);
    }

    public static void forget(TrainingJob job) {
        ACTIVE.remove(job);
    }

    /** A block at {@code pos} was placed, removed, turned or reconfigured: stop any job whose model it touches. */
    public static void changed(ServerLevel level, BlockPos pos) {
        for (TrainingJob job : ACTIVE) {
            if (job.level != level) {
                continue;
            }
            boolean touched = job.footprint.contains(pos.asLong());
            for (Direction d : Direction.values()) {
                touched |= job.footprint.contains(pos.relative(d).asLong());
            }
            if (touched) {
                job.requestStop(TrainingJob.Outcome.CHANGED);
            }
        }
    }

    /** Stops every job and waits briefly for each to hand back its weights; returns the jobs it stopped. */
    public static List<TrainingJob> stopAll() {
        List<TrainingJob> stopped = List.copyOf(ACTIVE);
        for (TrainingJob job : stopped) {
            job.requestStop(TrainingJob.Outcome.STOPPED);
        }
        long deadline = System.currentTimeMillis() + 5000;
        for (TrainingJob job : stopped) {
            while (!job.finished() && System.currentTimeMillis() < deadline) {
                LockSupport.parkNanos(5_000_000);
            }
        }
        return stopped;
    }
}
