package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.graph.CompileException;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Data parallelism: every device holds the whole model and takes its share of the batch's rows. Each computes its
 * gradients on its own, then the gradients are reduced in a binomial tree (log2 of the devices rounds, pairs adding at
 * once) into the first device's copy, the one the optimizer steps; each of the others then takes the new weights back.
 * It makes the same step one device would. A device's memory has to hold all of the model, and what crosses the links
 * grows with the model and not with the batch.
 */
public final class DataParallel implements Split {
    private final CompiledGraph main;
    private final CompiledGraph[] replicas;
    private final List<List<Tensor>> params;
    private final Devices devices;
    private volatile Timing last;

    public DataParallel(CompiledGraph main, int count, LongAdder flops) {
        this.main = main;
        this.replicas = new CompiledGraph[count];
        this.devices = new Devices(count, flops);
        replicas[0] = main;
        try {
            for (int d = 1; d < count; d++) {
                replicas[d] = CompiledGraph.compile(main.spec, main.env, main::params, 0);
            }
        } catch (CompileException e) {
            throw new IllegalStateException(e);
        }
        this.params = Arrays.stream(replicas).map(CompiledGraph::parameters).toList();
    }

    @Override
    public Tensor forward(Tape tape, Tokens tokens) {
        devices.begin();
        int n = Math.min(devices.count, tokens.batch);
        // the weights the optimizer left on the first device go out to the others
        long t0 = System.nanoTime();
        devices.all(n, d -> {
            if (d > 0) {
                long from = System.nanoTime();
                List<Tensor> mine = params.get(d), first = params.get(0);
                for (int i = 0; i < mine.size(); i++) {
                    System.arraycopy(first.get(i).data, 0, mine.get(i).data, 0, mine.get(i).data.length);
                }
                devices.worked(d, Timing.Kind.COLLECTIVE, from);
            }
        });
        devices.collective(t0);
        // each device's share of the rows, forward on its own
        Tape[] tapes = new Tape[n];
        Tensor[] outs = new Tensor[n];
        int[] start = new int[n + 1];
        for (int d = 0; d <= n; d++) {
            start[d] = tokens.batch * d / n;
        }
        devices.all(n, d -> {
            long from = System.nanoTime();
            tapes[d] = new Tape();
            int rows = start[d + 1] - start[d];
            int[] ids = new int[rows * tokens.time];
            System.arraycopy(tokens.ids, start[d] * tokens.time, ids, 0, ids.length);
            outs[d] = replicas[d].plain(tapes[d], new Tokens(ids, rows, tokens.time), null);
            devices.worked(d, Timing.Kind.FORWARD, from);
        });
        // the logits gathered, so the loss is taken as it is on one device
        int v = outs[0].dim(-1);
        Tensor logits = Tensor.zeros(true, tokens.batch, tokens.time, v);
        for (int d = 0; d < n; d++) {
            System.arraycopy(outs[d].data, 0, logits.data, start[d] * tokens.time * v, outs[d].data.length);
        }
        tape.record(() -> {
            float[] g = logits.grad();
            devices.all(n, d -> {
                long from = System.nanoTime();
                if (d > 0) {
                    params.get(d).forEach(Tensor::zeroGrad);
                }
                float[] mine = outs[d].grad();
                System.arraycopy(g, start[d] * tokens.time * v, mine, 0, mine.length);
                tapes[d].replay();
                devices.worked(d, Timing.Kind.BACKWARD, from);
            });
            reduce(n);
            // the gradients in, the weights back out: each once across every link of the tree
            last = devices.end(2 * 4 * main.parameterCount() * (n - 1));
        });
        return logits;
    }

    /** Binomial tree: in each round, every device still holding gradients adds them into a partner's. */
    private void reduce(int n) {
        long t0 = System.nanoTime();
        for (int gap = 1; gap < n; gap *= 2) {
            int step = gap;
            devices.all(n, d -> {
                if (d % (2 * step) == 0 && d + step < n) {
                    long from = System.nanoTime();
                    List<Tensor> into = params.get(d), source = params.get(d + step);
                    for (int i = 0; i < into.size(); i++) {
                        if (source.get(i).hasGrad()) {
                            float[] a = into.get(i).grad(), b = source.get(i).grad();
                            for (int k = 0; k < a.length; k++) {
                                a[k] += b[k];
                            }
                        }
                    }
                    devices.worked(d, Timing.Kind.COLLECTIVE, from);
                }
            });
        }
        devices.collective(t0);
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
