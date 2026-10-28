package io.github.metrovoc.residual.train;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Every running training job and every checked model, so world edits can stop the jobs they affect and get the
 * models they touch checked again. Server thread only.
 */
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
    /** The blocks each checked model was read from, by its owner. */
    private static final Map<Object, Watch> WATCHES = new HashMap<>();

    private record Watch(Level level, Set<Long> footprint, Runnable onEdit) {
    }

    private Jobs() {
    }

    public static void start(TrainingJob job) {
        ACTIVE.add(job);
        EXECUTOR.execute(job::run);
    }

    public static void forget(TrainingJob job) {
        ACTIVE.remove(job);
    }

    /** Runs {@code onEdit} whenever a block in or next to {@code footprint} changes, until replaced or forgotten. */
    public static void watch(Object owner, Level level, Set<Long> footprint, Runnable onEdit) {
        WATCHES.put(owner, new Watch(level, footprint, onEdit));
    }

    public static void unwatch(Object owner) {
        WATCHES.remove(owner);
    }

    /** A block at {@code pos} was placed, removed, turned or reconfigured: stop the jobs and tell the watchers it touches. */
    public static void changed(ServerLevel level, BlockPos pos) {
        for (TrainingJob job : ACTIVE) {
            if (job.level == level && touches(job.footprint, pos)) {
                job.requestStop(TrainingJob.Outcome.CHANGED);
            }
        }
        for (Watch w : WATCHES.values()) {
            if (w.level == level && touches(w.footprint, pos)) {
                w.onEdit.run();
            }
        }
    }

    private static boolean touches(Set<Long> footprint, BlockPos pos) {
        if (footprint.contains(pos.asLong())) {
            return true;
        }
        for (Direction d : Direction.values()) {
            if (footprint.contains(pos.relative(d).asLong())) {
                return true;
            }
        }
        return false;
    }

    /** The server is stopping: stops every job, waiting briefly for each to hand back its weights, and forgets every watch. */
    public static List<TrainingJob> stopAll() {
        WATCHES.clear();
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
