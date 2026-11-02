package io.github.metrovoc.residual.graph;

import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Runs a model a few new rows at a time instead of all of them: only what a new token changes is worked out, and each sequence
 * keeps what later rows read back, every attention's keys and values and whatever else a later machine looks at. Machines that
 * look only at their own row get just the new rows, a pass over several sequences at once is one matrix for each machine, and a
 * machine it has no shortcut for runs on the whole sequence so far, which is slower and still right.
 *
 * <p>A model that reads ahead breaks this: a new token would change rows already kept. {@link #agrees} says whether it gives what
 * a whole forward pass gives.
 */
public final class Decoder {
    /** Rows of a sequence's buffers are added this many at a time. */
    private static final int PAGE = 16;

    /** Told of every pass: before it starts, for what must not run at once, and after, with how many rows it ran. */
    public interface Watcher {
        void before();

        void after(int rows);
    }

    @Nullable
    private Watcher watcher;

    /** This decoder's passes are told to {@code watcher} from now on. */
    public Decoder watched(Watcher watcher) {
        this.watcher = watcher;
        return this;
    }

    /** Where a pass finds a machine's weights: good until {@link #done}. */
    public interface Weights {
        Map<String, Tensor> of(int node);

        default void done(int node) {
        }
    }

    private enum Mode { ROW, POSITION, SHIFT, ATTENTION, TRANSFORMER, WHOLE }

    public final CompiledGraph model;
    private final Weights weights;
    private final int nodes;
    private final Mode[] modes;
    /** Whether a machine's rows are kept for what reads them back. */
    private final boolean[] kept;
    /** Numbers in a row of a machine's output; 0 if its output isn't made of rows. */
    private final int[] width;
    private final int vocab;

    public Decoder(CompiledGraph model, Weights weights) {
        this.model = model;
        this.weights = weights;
        nodes = model.spec.size();
        modes = new Mode[nodes];
        kept = new boolean[nodes];
        width = new int[nodes];
        vocab = model.env.vocab();
        for (int node : model.order) {
            Sym s = model.shapes[node];
            width[node] = hasRows(s) ? s.dim(-1) : 0;
        }
        for (int node : model.order) {
            GraphSpec.Node nd = model.spec.node(node);
            modes[node] = mode(nd);
            for (int src : nd.inputs) {
                // what runs on the whole sequence so far reads back the rows of what doesn't; what shifts reads them back anyway
                if (src >= 0 && (modes[node] == Mode.SHIFT || modes[node] == Mode.WHOLE && modes[src] != Mode.WHOLE && modes[src] != Mode.POSITION)) {
                    kept[src] = true;
                }
            }
        }
        kept[model.spec.output] = true;
    }

    private static boolean hasRows(Sym s) {
        return !s.tokens && (s.rank() == 3 && s.dim(0) == Sym.B && s.dim(1) == Sym.T && s.dim(2) > 0
                || s.rank() == 2 && s.dim(0) == Sym.T && s.dim(1) > 0);
    }

    private Mode mode(GraphSpec.Node nd) {
        for (int src : nd.inputs) {
            if (src >= 0 && width[src] == 0) {
                return Mode.WHOLE;
            }
        }
        return switch (nd.op) {
            case EMBEDDING, LINEAR, ACTIVATION, ADD, MULTIPLY, NORM, SCALE, CONCAT, MLP -> Mode.ROW;
            case POSITION -> Mode.POSITION;
            case SHIFT -> Mode.SHIFT;
            case ATTENTION -> Mode.ATTENTION;
            case TRANSFORMER -> Mode.TRANSFORMER;
            default -> Mode.WHOLE;
        };
    }

    /** Bytes a token takes in each attention's keys and values: two rows of its width in floats, in every attention. */
    public long keyValueBytes() {
        long bytes = 0;
        for (int node : model.order) {
            if (modes[node] == Mode.ATTENTION || modes[node] == Mode.TRANSFORMER) {
                bytes += 2L * width[node] * Float.BYTES;
            }
        }
        return bytes;
    }

    /** Bytes a token takes in a sequence altogether: its keys and values, and the rows kept for what reads them back. */
    public long tokenBytes() {
        long bytes = keyValueBytes() + Integer.BYTES;
        for (int node : model.order) {
            if (kept[node]) {
                bytes += (long) width[node] * Float.BYTES;
            }
        }
        return bytes;
    }

    public Sequence sequence() {
        return new Sequence();
    }

    /** One sequence so far, with what its later rows will read back. */
    public final class Sequence {
        private int[] tokens = new int[PAGE];
        private int length;
        private final float[][] keys = new float[nodes][];
        private final float[][] values = new float[nodes][];
        private final float[][] rows = new float[nodes][];

        public int length() {
            return length;
        }

        public int token(int i) {
            return tokens[i];
        }

        /** Bytes it holds. */
        public long bytes() {
            long bytes = (long) tokens.length * Integer.BYTES;
            for (int node : model.order) {
                bytes += size(keys[node]) + size(values[node]) + size(rows[node]);
            }
            return bytes;
        }

        private static long size(float[] a) {
            return a == null ? 0 : (long) a.length * Float.BYTES;
        }

        /** How many tokens it shares with the start of {@code ids[from..from + n)}. */
        public int shared(int[] ids, int from, int n) {
            int most = Math.min(length, n);
            for (int i = 0; i < most; i++) {
                if (tokens[i] != ids[from + i]) {
                    return i;
                }
            }
            return most;
        }

        /** A sequence of its first {@code n} tokens. */
        public Sequence fork(int n) {
            Sequence s = new Sequence();
            s.length = n;
            int rowsNeeded = paged(n);
            s.tokens = Arrays.copyOf(tokens, rowsNeeded);
            for (int node : model.order) {
                s.keys[node] = copy(keys[node], n, rowsNeeded, width[node]);
                s.values[node] = copy(values[node], n, rowsNeeded, width[node]);
                s.rows[node] = copy(rows[node], n, rowsNeeded, width[node]);
            }
            return s;
        }

        private static float[] copy(float[] from, int n, int capacity, int width) {
            if (from == null) {
                return null;
            }
            float[] to = new float[capacity * width];
            System.arraycopy(from, 0, to, 0, n * width);
            return to;
        }

        /** The scores of the rows from {@code from} up to {@code to}, in {@code out} from {@code at}. */
        public void logits(int from, int to, float[] out, int at) {
            System.arraycopy(rows[model.spec.output], from * vocab, out, at, (to - from) * vocab);
        }

        private void grow(int more) {
            int capacity = paged(length + more);
            if (capacity > tokens.length) {
                tokens = Arrays.copyOf(tokens, capacity);
            }
            for (int node : model.order) {
                keys[node] = grown(keys[node], capacity, width[node]);
                values[node] = grown(values[node], capacity, width[node]);
                rows[node] = grown(rows[node], capacity, width[node]);
            }
        }

        private static float[] grown(float[] a, int capacity, int width) {
            return a == null || a.length >= capacity * width ? a : Arrays.copyOf(a, capacity * width);
        }
    }

    private static int paged(int n) {
        return Math.max(1, (n + PAGE - 1) / PAGE) * PAGE;
    }

    /** Adds {@code r} tokens to each of {@code seqs} in one pass: {@code ids} holds {@code r} for each sequence in turn. */
    public void pass(List<Sequence> seqs, int[] ids, int r) {
        if (watcher == null) {
            run(seqs, ids, r);
            return;
        }
        watcher.before();
        try {
            run(seqs, ids, r);
        } finally {
            watcher.after(seqs.size() * r);
        }
    }

    private void run(List<Sequence> seqs, int[] ids, int r) {
        int b = seqs.size();
        for (Sequence s : seqs) {
            s.grow(r);
        }
        Tokens added = new Tokens(ids, b, r);
        Tensor[] row = new Tensor[nodes];
        Tensor[][] whole = new Tensor[nodes][];
        for (int i = 0; i < b; i++) {
            Sequence s = seqs.get(i);
            System.arraycopy(ids, i * r, s.tokens, s.length, r);
        }
        for (int node : model.order) {
            GraphSpec.Node nd = model.spec.node(node);
            Map<String, Tensor> p = weights.of(node);
            Op.Frame f = new Op.Frame(node, r, null, nd.fused, null);
            switch (modes[node]) {
                case ROW -> {
                    Object[] in = new Object[nd.op.arity];
                    for (int k = 0; k < in.length; k++) {
                        in[k] = nd.inputs[k] == GraphSpec.TOKENS ? added : row[nd.inputs[k]];
                    }
                    row[node] = nd.op.run(null, in, p, nd.config, f);
                }
                case POSITION -> row[node] = positions(seqs, p.get("weight"), r, width[node]);
                case SHIFT -> row[node] = shift(seqs, nd.inputs[0], nd.config.integer("steps"), r, width[node]);
                case ATTENTION -> row[node] = attention(seqs, node, row[nd.inputs[0]], p, "", nd.config.integer("heads"), f, r);
                case TRANSFORMER -> {
                    Tensor x = row[nd.inputs[0]];
                    Tensor h = Op.layerNorm(null, x, p.get("ln1.weight"), p.get("ln1.bias"), f);
                    x = Ops.add(null, x, attention(seqs, node, h, p, "attn.", nd.config.integer("heads"), f, r));
                    h = Op.layerNorm(null, x, p.get("ln2.weight"), p.get("ln2.bias"), f);
                    row[node] = Ops.add(null, x,
                            Op.mlp(null, h, p, "mlp.", Ops.Activation.byId(nd.config.get("fn")), f));
                }
                case WHOLE -> {
                    whole[node] = new Tensor[b];
                    for (int i = 0; i < b; i++) {
                        whole[node][i] = whole(seqs.get(i), node, nd, p, whole, i, r);
                    }
                    if (width[node] > 0) {
                        row[node] = lastRows(whole[node], r, width[node]);
                    }
                }
            }
            weights.done(node);
            if (kept[node]) {
                int w = width[node];
                for (int i = 0; i < b; i++) {
                    Sequence s = seqs.get(i);
                    if (s.rows[node] == null) {
                        s.rows[node] = new float[s.tokens.length * w];
                    }
                    System.arraycopy(row[node].data, i * r * w, s.rows[node], s.length * w, r * w);
                }
            }
        }
        for (Sequence s : seqs) {
            s.length += r;
        }
    }

    /** The rows of the position table each sequence has come to. */
    private static Tensor positions(List<Sequence> seqs, Tensor table, int r, int d) {
        float[] out = new float[seqs.size() * r * d];
        for (int i = 0; i < seqs.size(); i++) {
            System.arraycopy(table.data, seqs.get(i).length * d, out, i * r * d, r * d);
        }
        return Tensor.of(new int[]{seqs.size(), r, d}, out);
    }

    /** The rows {@code steps} back along each sequence, zeros before its start. */
    private static Tensor shift(List<Sequence> seqs, int src, int steps, int r, int d) {
        float[] out = new float[seqs.size() * r * d];
        for (int i = 0; i < seqs.size(); i++) {
            Sequence s = seqs.get(i);
            for (int k = 0; k < r; k++) {
                int back = s.length + k - steps;
                if (back >= 0) {
                    System.arraycopy(s.rows[src], back * d, out, (i * r + k) * d, d);
                }
            }
        }
        return Tensor.of(new int[]{seqs.size(), r, d}, out);
    }

    /**
     * What {@link Op#attention} gives for the new rows: their keys and values go on the end of what each sequence keeps, then each
     * new row's queries read every key up to its own and mix the values, head by head.
     */
    private Tensor attention(List<Sequence> seqs, int node, Tensor x, Map<String, Tensor> p, String prefix, int heads, Op.Frame f, int r) {
        int b = seqs.size(), d = x.dim(-1), dh = d / heads;
        float[] q = Op.matmul(null, x, p.get(prefix + "q"), false, f).data;
        float[] k = Op.matmul(null, x, p.get(prefix + "k"), false, f).data;
        float[] v = Op.matmul(null, x, p.get(prefix + "v"), false, f).data;
        float scale = (float) (1.0 / Math.sqrt(dh));
        float[] y = new float[b * r * d];
        float[] weights = new float[seqs.stream().mapToInt(Sequence::length).max().orElse(0) + r];
        for (int i = 0; i < b; i++) {
            Sequence s = seqs.get(i);
            if (s.keys[node] == null) {
                s.keys[node] = new float[s.tokens.length * d];
                s.values[node] = new float[s.tokens.length * d];
            }
            float[] keys = s.keys[node], values = s.values[node];
            System.arraycopy(k, i * r * d, keys, s.length * d, r * d);
            System.arraycopy(v, i * r * d, values, s.length * d, r * d);
            for (int n = 0; n < r; n++) {
                int qi = (i * r + n) * d, seen = s.length + n + 1;
                for (int h = 0; h < heads; h++) {
                    int at = h * dh;
                    float max = Float.NEGATIVE_INFINITY;
                    for (int j = 0; j < seen; j++) {
                        int kj = j * d + at;
                        float dot = 0f;
                        for (int c = 0; c < dh; c++) {
                            dot += q[qi + at + c] * keys[kj + c];
                        }
                        dot *= scale;
                        weights[j] = dot;
                        max = Math.max(max, dot);
                    }
                    float sum = 0f;
                    for (int j = 0; j < seen; j++) {
                        float e = (float) Math.exp(weights[j] - max);
                        weights[j] = e;
                        sum += e;
                    }
                    float inv = 1f / sum;
                    for (int j = 0; j < seen; j++) {
                        float w = weights[j] * inv;
                        if (w == 0f) {
                            continue;
                        }
                        int vj = j * d + at;
                        for (int c = 0; c < dh; c++) {
                            y[qi + at + c] += w * values[vj + c];
                        }
                    }
                }
            }
        }
        return Op.matmul(null, Tensor.of(new int[]{b, r, d}, y), p.get(prefix + "o"), false, f);
    }

    /** A machine with no shortcut, on one sequence as far as this pass takes it. */
    private Tensor whole(Sequence s, int node, GraphSpec.Node nd, Map<String, Tensor> p, Tensor[][] done, int i, int r) {
        int t = s.length + r;
        Object[] in = new Object[nd.op.arity];
        for (int k = 0; k < in.length; k++) {
            int src = nd.inputs[k];
            if (src == GraphSpec.TOKENS) {
                in[k] = new Tokens(Arrays.copyOf(s.tokens, t), 1, t);
            } else if (done[src] != null) {
                in[k] = done[src][i];
            } else if (modes[src] == Mode.POSITION) {
                in[k] = Tensor.of(new int[]{t, width[src]}, Arrays.copyOf(weights.of(src).get("weight").data, t * width[src]));
                weights.done(src);
            } else {
                // the rows it has put away, this pass's among them
                in[k] = Tensor.of(new int[]{1, t, width[src]}, Arrays.copyOf(s.rows[src], t * width[src]));
            }
        }
        return nd.op.run(null, in, p, nd.config, new Op.Frame(node, t, null, nd.fused, null));
    }

    /** The last {@code r} rows of each sequence's whole output, as one batch. */
    private static Tensor lastRows(Tensor[] outputs, int r, int d) {
        float[] out = new float[outputs.length * r * d];
        for (int i = 0; i < outputs.length; i++) {
            float[] data = outputs[i].data;
            System.arraycopy(data, data.length - r * d, out, i * r * d, r * d);
        }
        return Tensor.of(new int[]{outputs.length, r, d}, out);
    }

    /** Whether, on {@code ids}, this gives what a whole forward pass gives, a token at a time and then all at once. */
    public boolean agrees(int[] ids) {
        Tensor full = model.forward(null, new Tokens(ids, 1, ids.length), null);
        float[] mine = new float[full.size()];
        Sequence stepped = sequence();
        for (int id : ids) {
            pass(List.of(stepped), new int[]{id}, 1);
        }
        stepped.logits(0, ids.length, mine, 0);
        Sequence together = sequence();
        pass(List.of(together), ids, ids.length);
        float[] all = new float[full.size()];
        together.logits(0, ids.length, all, 0);
        for (int i = 0; i < mine.length; i++) {
            if (!(Math.abs(mine[i] - full.data[i]) <= 1e-3f * (1f + Math.abs(full.data[i])))
                    || !(Math.abs(all[i] - full.data[i]) <= 1e-3f * (1f + Math.abs(full.data[i])))) {
                return false;
            }
        }
        return true;
    }

    /** A model's own weights, as floats. */
    public static Weights floats(CompiledGraph model) {
        return model::params;
    }
}
