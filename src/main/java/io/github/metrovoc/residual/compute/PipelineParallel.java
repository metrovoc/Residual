package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;

/**
 * Pipeline parallelism: the model's layers are cut into stages, one to a device, and a batch cut into microbatches goes
 * down them one after another, each stage taking the next microbatch as soon as it has passed the last one on. Then the
 * gradients come back up the same way. While the first microbatch is reaching the last stage the later stages wait, and
 * while the last is leaving the earlier ones wait: those are the bubbles, a share of about
 * (stages - 1) / (microbatches + stages - 1) of the device time when the stages are level. A device holds only its
 * stage's weights.
 */
public final class PipelineParallel implements Split {
    /** A node worth a stage of its own costs at least this share of the model. */
    private static final double WORTH_A_STAGE = 0.05;
    private final CompiledGraph model;
    private final Devices devices;
    /** The first place in the model's order of each stage, then where the last one ends. */
    private final int[] stageStart;
    private final int stages;
    private volatile Timing last;
    private volatile int microbatches;

    public PipelineParallel(CompiledGraph model, int count, LongAdder flops) {
        this.model = model;
        long[] cost = model.costs();
        this.stageStart = stages(cost, Math.max(1, Math.min(count, worthStages(cost))));
        this.stages = stageStart.length - 1;
        this.devices = new Devices(stages, flops);
    }

    private static int worthStages(long[] cost) {
        long total = 0;
        for (long c : cost) {
            total += c;
        }
        int worth = 0;
        for (long c : cost) {
            worth += c >= WORTH_A_STAGE * total ? 1 : 0;
        }
        return worth;
    }

    /** Cuts {@code cost}, in order, into at most {@code stages} runs, keeping the dearest run as cheap as it can be. */
    static int[] stages(long[] cost, int stages) {
        long lo = 0, hi = 0;
        for (long c : cost) {
            lo = Math.max(lo, c);
            hi += c;
        }
        while (lo < hi) {
            long mid = (lo + hi) >>> 1;
            int need = 1;
            long sum = 0;
            for (long c : cost) {
                if (sum + c > mid) {
                    need++;
                    sum = 0;
                }
                sum += c;
            }
            if (need <= stages) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        List<Integer> cuts = new ArrayList<>();
        cuts.add(0);
        long sum = 0;
        for (int i = 0; i < cost.length; i++) {
            if (sum + cost[i] > lo) {
                cuts.add(i);
                sum = 0;
            }
            sum += cost[i];
        }
        cuts.add(cost.length);
        return cuts.stream().mapToInt(Integer::intValue).toArray();
    }

    public int stages() {
        return stages;
    }

    /** What each stage would take to train, in bytes, were {@code model} cut for {@code count} devices. */
    public static long[] stageBytes(CompiledGraph model, int count) {
        long[] cost = model.costs();
        int[] cuts = stages(cost, Math.max(1, Math.min(count, worthStages(cost))));
        long[] out = new long[cuts.length - 1];
        for (int s = 0; s < out.length; s++) {
            for (int i = cuts[s]; i < cuts[s + 1]; i++) {
                for (Tensor t : model.params(model.order[i]).values()) {
                    out[s] += Accounts.trainingBytes(t.size());
                }
            }
        }
        return out;
    }

    public int microbatches() {
        return microbatches;
    }

    /** The share of device time bubbles take when the stages are level. */
    public static double levelBubble(int stages, int microbatches) {
        return (stages - 1) / (double) (microbatches + stages - 1);
    }

    @Override
    public Tensor forward(Tape tape, Tokens tokens) {
        devices.begin();
        int m = Math.min(tokens.batch, 2 * stages);
        microbatches = m;
        int[] start = new int[m + 1];
        for (int i = 0; i <= m; i++) {
            start[i] = tokens.batch * i / m;
        }
        Object[][] values = new Object[m][model.spec.size()];
        Tokens[] micro = new Tokens[m];
        Tape[][] tapes = new Tape[stages][m];
        for (int i = 0; i < m; i++) {
            int rows = start[i + 1] - start[i];
            int[] ids = new int[rows * tokens.time];
            System.arraycopy(tokens.ids, start[i] * tokens.time, ids, 0, ids.length);
            micro[i] = new Tokens(ids, rows, tokens.time);
        }
        CountDownLatch[][] ready = latches(m);
        long[] crossing = new long[stages];
        devices.all(stages, s -> {
            for (int i = 0; i < m; i++) {
                await(s > 0 ? ready[s - 1][i] : null);
                long from = System.nanoTime();
                Tape t = new Tape();
                tapes[s][i] = t;
                for (int p = stageStart[s]; p < stageStart[s + 1]; p++) {
                    model.node(model.order[p], values[i], t, micro[i], null);
                }
                crossing[s] += crossingBytes(s, values[i]);
                devices.worked(s, Timing.Kind.FORWARD, from);
                ready[s][i].countDown();
            }
        });
        int output = model.spec.output;
        int v = ((Tensor) values[0][output]).dim(-1);
        Tensor logits = Tensor.zeros(true, tokens.batch, tokens.time, v);
        for (int i = 0; i < m; i++) {
            float[] mine = ((Tensor) values[i][output]).data;
            System.arraycopy(mine, 0, logits.data, start[i] * tokens.time * v, mine.length);
        }
        tape.record(() -> {
            float[] g = logits.grad();
            for (int i = 0; i < m; i++) {
                float[] mine = ((Tensor) values[i][output]).grad();
                System.arraycopy(g, start[i] * tokens.time * v, mine, 0, mine.length);
            }
            CountDownLatch[][] back = latches(m);
            devices.all(stages, s -> {
                for (int i = 0; i < m; i++) {
                    await(s + 1 < stages ? back[s + 1][i] : null);
                    long from = System.nanoTime();
                    tapes[s][i].replay();
                    devices.worked(s, Timing.Kind.BACKWARD, from);
                    back[s][i].countDown();
                }
            });
            long bytes = 0;
            for (long c : crossing) {
                // down, and the gradients of the same size back up
                bytes += 2 * c;
            }
            last = devices.end(bytes);
        });
        return logits;
    }

    /** What stage {@code s} hands on: what it made that a later stage reads. */
    private long crossingBytes(int s, Object[] values) {
        long bytes = 0;
        for (int p = stageStart[s]; p < stageStart[s + 1]; p++) {
            int node = model.order[p];
            if (values[node] instanceof Tensor t && readLater(node, stageStart[s + 1])) {
                bytes += 4L * t.size();
            }
        }
        return bytes;
    }

    private boolean readLater(int node, int after) {
        for (int p = after; p < model.order.length; p++) {
            for (int in : model.spec.node(model.order[p]).inputs) {
                if (in == node) {
                    return true;
                }
            }
        }
        return false;
    }

    private CountDownLatch[][] latches(int m) {
        CountDownLatch[][] out = new CountDownLatch[stages][m];
        for (CountDownLatch[] row : out) {
            Arrays.setAll(row, i -> new CountDownLatch(1));
        }
        return out;
    }

    private static void await(CountDownLatch latch) {
        if (latch != null) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public Timing last() {
        return last;
    }

    @Override
    public int devices() {
        return devices.count;
    }

    @Override
    public void close() {
        devices.close();
    }
}
