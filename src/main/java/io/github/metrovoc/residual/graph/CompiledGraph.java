package io.github.metrovoc.residual.graph;

import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.compute.Split;
import io.github.metrovoc.residual.graph.CompileException.Issue;
import io.github.metrovoc.residual.tensor.Numerics;
import io.github.metrovoc.residual.tensor.ShapeException;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** A checked graph in execution order, owning its parameter tensors. */
public final class CompiledGraph {
    public final GraphSpec spec;
    public final Env env;
    /** Node indices in execution order (only nodes that feed the output). */
    public final int[] order;
    /** Symbolic output shape of every node in {@link #order}; null for unused nodes. */
    public final Sym[] shapes;
    private final List<Map<String, Tensor>> params;
    /** What its numbers are rounded to on the way through it. */
    public volatile Numerics.Format precision = Numerics.Format.FP32;
    /** How its training is split across devices, or null for one device. */
    public volatile Split split;
    private Boolean addsToLoss;
    /** What answers its forward passes while nothing is learning, instead of running them itself. */
    @Nullable
    private final Forwarder forwarder;

    /** Answers a model's forward passes, with no tape and nothing captured. */
    @FunctionalInterface
    public interface Forwarder {
        Tensor forward(Tokens tokens);
    }

    private CompiledGraph(GraphSpec spec, Env env, int[] order, Sym[] shapes, List<Map<String, Tensor>> params, @Nullable Forwarder forwarder) {
        this.spec = spec;
        this.env = env;
        this.order = order;
        this.shapes = shapes;
        this.params = params;
        this.forwarder = forwarder;
    }

    /** This model with the same weights, whose forward passes go to {@code forwarder}. */
    public CompiledGraph through(Forwarder forwarder) {
        return new CompiledGraph(spec, env, order, shapes, params, forwarder);
    }

    /** What its forward passes go to, or null if it runs them itself. */
    @Nullable
    public Forwarder forwarder() {
        return forwarder;
    }

    /**
     * Checks a graph and builds its parameters. Existing parameter values (from {@code existing}) are reused when
     * their shapes still fit; everything else is freshly initialised from {@code seed}.
     */
    public static CompiledGraph compile(GraphSpec spec, Env env, IntFunction<Map<String, Tensor>> existing, long seed) throws CompileException {
        List<Issue> issues = new ArrayList<>();
        int n = spec.size();
        if (spec.output == GraphSpec.MISSING) {
            issues.add(new Issue(-1, Component.translatable("residual.graph.no_output", vocabShape(env))));
            throw new CompileException(issues);
        }
        if (spec.output == GraphSpec.CONFLICT) {
            issues.add(new Issue(-1, Component.translatable("residual.graph.output_conflict")));
            throw new CompileException(issues);
        }
        if (spec.output == GraphSpec.TOKENS) {
            issues.add(new Issue(-1, Component.translatable("residual.graph.short_circuit")));
            throw new CompileException(issues);
        }

        // Depth-first topological sort from the output, detecting cycles.
        int[] state = new int[n];
        List<Integer> order = new ArrayList<>();
        boolean[] cyclic = {false};
        visit(spec, spec.output, state, order, issues, cyclic);
        if (cyclic[0]) {
            throw new CompileException(issues);
        }

        Sym[] shapes = new Sym[n];
        boolean[] broken = new boolean[n];
        for (int node : order) {
            GraphSpec.Node nd = spec.node(node);
            Sym[] in = new Sym[nd.op.arity];
            boolean ok = true;
            for (int i = 0; i < in.length; i++) {
                int src = nd.inputs[i];
                String port = i == 0 ? "back" : "side";
                Component title = Component.translatable("block.residual." + nd.op.id);
                if (src == GraphSpec.MISSING) {
                    issues.add(new Issue(node, Component.translatable("residual.graph.unconnected." + port, title)));
                    ok = false;
                } else if (src == GraphSpec.CONFLICT) {
                    issues.add(new Issue(node, Component.translatable("residual.graph.conflict." + port, title)));
                    ok = false;
                } else if (src == GraphSpec.TOKENS) {
                    in[i] = Sym.tokens();
                } else if (broken[src] || shapes[src] == null) {
                    ok = false;
                } else {
                    in[i] = shapes[src];
                }
            }
            if (!ok) {
                broken[node] = true;
                continue;
            }
            try {
                shapes[node] = nd.op.infer(in, nd.config, env);
            } catch (ShapeException e) {
                issues.add(new Issue(node, e.message));
                broken[node] = true;
            }
        }
        if (issues.isEmpty()) {
            Sym out = shapes[spec.output];
            if (out.tokens || out.rank() != 3 || out.dim(0) != Sym.B || out.dim(1) != Sym.T || out.dim(2) != env.vocab()) {
                String hint = "";
                if (out.rank() == 3 && out.dim(0) == Sym.B && out.dim(1) == Sym.T) {
                    hint = switch (spec.node(spec.output).op) {
                        case LINEAR -> ".linear";
                        case EMBEDDING -> ".embedding";
                        default -> ".other";
                    };
                }
                issues.add(new Issue(spec.output, Component.translatable("residual.graph.wrong_output" + hint, vocabShape(env), out.toString())));
            }
        }
        if (!issues.isEmpty()) {
            throw new CompileException(issues, shapes);
        }

        List<Map<String, Tensor>> params = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            params.add(Map.of());
        }
        for (int node : order) {
            GraphSpec.Node nd = spec.node(node);
            Sym[] in = new Sym[nd.op.arity];
            for (int i = 0; i < in.length; i++) {
                in[i] = nd.inputs[i] == GraphSpec.TOKENS ? Sym.tokens() : shapes[nd.inputs[i]];
            }
            List<ParamSpec> specs = nd.op.params(in, nd.config, env);
            if (specs.isEmpty()) {
                continue;
            }
            Map<String, Tensor> old = existing == null ? null : existing.apply(node);
            Map<String, Tensor> fresh = new LinkedHashMap<>();
            Random rng = new Random(seed * 31 + node * 7919L + nd.op.ordinal());
            for (ParamSpec ps : specs) {
                Tensor prior = old == null ? null : old.get(ps.name());
                Tensor init = ps.create(rng);
                Tensor saved = prior == null || Arrays.equals(prior.shape, ps.shape()) ? prior : grown(prior, ps.shape(), env.vocab());
                if (saved != null) {
                    System.arraycopy(saved.data, 0, init.data, 0, init.data.length);
                }
                fresh.put(ps.name(), init);
            }
            params.set(node, fresh);
        }
        int[] ord = order.stream().mapToInt(Integer::intValue).toArray();
        return new CompiledGraph(spec, env, ord, shapes, params, null);
    }

    private static void visit(GraphSpec spec, int node, int[] state, List<Integer> order, List<Issue> issues, boolean[] cyclic) {
        if (node < 0 || state[node] == 2) {
            return;
        }
        if (state[node] == 1) {
            if (!cyclic[0]) {
                issues.add(new Issue(node, Component.translatable("residual.graph.loop", label(spec.node(node)))));
            }
            cyclic[0] = true;
            return;
        }
        state[node] = 1;
        for (int src : spec.node(node).inputs) {
            visit(spec, src, state, order, issues, cyclic);
        }
        state[node] = 2;
        order.add(node);
    }

    static String vocabShape(Env env) {
        return "[B×T×" + env.vocab() + "]";
    }

    static Component label(GraphSpec.Node nd) {
        Component title = Component.translatable("block.residual." + nd.op.id);
        return nd.name == null || nd.name.isEmpty() ? title : Component.translatable("residual.graph.named", title, nd.name);
    }

    /**
     * A table saved when the alphabet was shorter, in the shape it has now, or null if that is not what kept it from fitting.
     * The new characters go in after the old ones and before any merges. As something read, one starts at zero. As something
     * scored, it takes the mean of the others, so its score is the mean of theirs: it can never come out on top, and whatever
     * the model answered before it answers still, until it practises on them.
     */
    private static Tensor grown(Tensor saved, int[] want, int vocab) {
        if (saved.shape.length != want.length || want.length < 1 || want.length > 2) {
            return null;
        }
        for (int d = 0; d < want.length; d++) {
            int more = want[d] - saved.shape[d];
            if (more != 0 && (want[d] != vocab || more != Vocab.CHARACTERS - Vocab.TEXT)) {
                return null;
            }
        }
        int[] was = want.length == 1 ? new int[]{1, saved.shape[0]} : saved.shape;
        int[] now = want.length == 1 ? new int[]{1, want[0]} : want;
        int rowsAt = Vocab.CHARACTERS - (now[0] - was[0]), columnsAt = Vocab.CHARACTERS - (now[1] - was[1]);
        float[] data = new float[now[0] * now[1]];
        for (int r = 0; r < was[0]; r++) {
            int row = r < rowsAt || now[0] == was[0] ? r : r + now[0] - was[0];
            float mean = 0;
            for (int c = 0; c < was[1]; c++) {
                float v = saved.data[r * was[1] + c];
                mean += v / was[1];
                data[row * now[1] + (c < columnsAt || now[1] == was[1] ? c : c + now[1] - was[1])] = v;
            }
            for (int c = 0; c < now[1] - was[1]; c++) {
                data[row * now[1] + columnsAt + c] = mean;
            }
        }
        return Tensor.of(want, data);
    }

    /** Runs the model on a token batch and returns logits [B, T, V]. */
    public Tensor forward(Tape tape, Tokens tokens, Captures captures) {
        if (forwarder != null && tape == null && captures == null) {
            return forwarder.forward(tokens);
        }
        return forward(tape, tokens, captures, null);
    }

    /** As {@link #forward(Tape, Tokens, Captures)}, for a training pass: machines leave what they add to the loss in {@code extras}. */
    public Tensor forward(Tape tape, Tokens tokens, Captures captures, Extras extras) {
        return forward(tape, tokens, captures, extras, precision);
    }

    /**
     * As {@link #forward(Tape, Tokens, Captures, Extras)}, its numbers rounded to {@code format}. A split takes a plain training pass;
     * rounding, rows packed with masks and what machines add to the loss are done on one device.
     */
    public Tensor forward(Tape tape, Tokens tokens, Captures captures, Extras extras, Numerics.Format format) {
        return split != null && tape != null && captures == null && format == Numerics.Format.FP32 && tokens.starts == null && !addsToLoss()
                ? split.forward(tape, tokens) : plain(tape, tokens, captures, extras, format);
    }

    /** Whether any of its machines adds to the loss, found by a pass over one token the first time it is asked. */
    private boolean addsToLoss() {
        if (addsToLoss == null) {
            Extras probe = new Extras();
            plain(null, new Tokens(new int[1], 1, 1), null, probe, Numerics.Format.FP32);
            addsToLoss = !probe.penalties.isEmpty() || probe.second != null;
        }
        return addsToLoss;
    }

    /** {@link #forward} on this thread alone, whatever its split. */
    public Tensor plain(Tape tape, Tokens tokens, Captures captures) {
        return plain(tape, tokens, captures, null, Numerics.Format.FP32);
    }

    private Tensor plain(Tape tape, Tokens tokens, Captures captures, Extras extras, Numerics.Format format) {
        Object[] values = new Object[spec.size()];
        for (int node : order) {
            node(node, values, tape, tokens, captures, extras, format);
        }
        return (Tensor) values[spec.output];
    }

    /** The format a node's matrices take: fp4 made to work keeps the first and last layers in fp8. */
    private Numerics.Format format(int node, Numerics.Format format) {
        return format == Numerics.Format.FP4 && (node == spec.output || isTable(spec.node(node).op)) ? Numerics.Format.FP8 : format;
    }

    private static boolean isTable(Op op) {
        return op == Op.EMBEDDING || op == Op.POSITION;
    }

    /** Gains, offsets and temperatures stay in bf16 whatever the matrices are in. */
    private static Map<String, Tensor> narrowed(Tape tape, Map<String, Tensor> p, Numerics.Format format) {
        if (!format.narrow() || p.isEmpty()) {
            return p;
        }
        Map<String, Tensor> out = new LinkedHashMap<>();
        p.forEach((name, t) -> out.put(name, t.rank() < 2 ? Numerics.cast(tape, t, Numerics.Format.BF16) : t));
        return out;
    }

    /** What its weights take in {@code format}, in bytes. */
    public long weightBytes(Numerics.Format format) {
        double bytes = 0;
        for (int node : order) {
            for (Tensor t : params.get(node).values()) {
                bytes += t.size() * (t.rank() < 2 ? Math.min(2f, format.bytes) : format(node, format).bytes);
            }
        }
        return Math.round(bytes);
    }

    /** The matrices inside the model: what Muon moves, leaving the token and position tables and the last layer to AdamW. */
    public List<Tensor> hiddenMatrices() {
        List<Tensor> out = new ArrayList<>();
        for (int node : order) {
            if (node != spec.output && !isTable(spec.node(node).op)) {
                for (Tensor t : params.get(node).values()) {
                    if (t.rank() == 2) {
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }

    public List<Tensor> otherParameters() {
        List<Tensor> out = parameters();
        out.removeAll(hiddenMatrices());
        return out;
    }

    /** Runs one node, whose inputs are in {@code values}, and puts what it makes there. */
    public void node(int node, Object[] values, Tape tape, Tokens tokens, Captures captures) {
        node(node, values, tape, tokens, captures, null, Numerics.Format.FP32);
    }

    private void node(int node, Object[] values, Tape tape, Tokens tokens, Captures captures, Extras extras, Numerics.Format format) {
        GraphSpec.Node nd = spec.node(node);
        Object[] in = new Object[nd.op.arity];
        for (int i = 0; i < in.length; i++) {
            int src = nd.inputs[i];
            in[i] = src == GraphSpec.TOKENS ? tokens : values[src];
        }
        Tensor out = nd.op.run(tape, in, narrowed(tape, params.get(node), format), nd.config, new Op.Frame(node, tokens.time, captures,
                nd.fused, extras, format(node, format), tokens.starts, tokens.batch));
        if (captures != null) {
            if (captures.edit != null) {
                out = captures.edit.apply(tape, node, out);
            }
            captures.output(node, out);
        }
        values[node] = out;
    }

    /** As {@link #forward}, its weights being what {@code weights} says, so that what they are made from can learn instead. */
    public Tensor forwardWith(Tape tape, Tokens tokens, Decoder.Weights weights) {
        Object[] values = new Object[spec.size()];
        for (int node : order) {
            GraphSpec.Node nd = spec.node(node);
            Object[] in = new Object[nd.op.arity];
            for (int i = 0; i < in.length; i++) {
                int src = nd.inputs[i];
                in[i] = src == GraphSpec.TOKENS ? tokens : values[src];
            }
            values[node] = nd.op.run(tape, in, weights.of(node), nd.config, new Op.Frame(node, tokens.time, null, nd.fused, null));
        }
        return (Tensor) values[spec.output];
    }

    /** Runs one-input nodes in turn, the first reading {@code x}: what comes after a point of the model, without what came before it. */
    public Tensor through(Tape tape, int[] nodes, Tensor x) {
        Tensor out = x;
        for (int node : nodes) {
            GraphSpec.Node nd = spec.node(node);
            out = nd.op.run(tape, new Object[]{out}, params.get(node), nd.config, new Op.Frame(node, x.dim(-2), null, nd.fused, null));
        }
        return out;
    }

    /** A copy with weights of its own, to run where the model it was copied from runs too, or to train on without touching it. */
    public CompiledGraph copy() {
        try {
            return compile(spec, env, this::params, 0);
        } catch (CompileException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A model of the same design with fresh weights. */
    public CompiledGraph fresh(long seed) {
        try {
            return compile(spec, env, null, seed);
        } catch (CompileException e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Tensor> params(int node) {
        return params.get(node);
    }

    public List<Tensor> parameters() {
        List<Tensor> all = new ArrayList<>();
        for (int node : order) {
            all.addAll(params.get(node).values());
        }
        return all;
    }

    public long parameterCount() {
        long c = 0;
        for (Tensor t : parameters()) {
            c += t.size();
        }
        return c;
    }

    /** Copies of all parameters, keyed by node, for saving back into the world. */
    public Map<Integer, Map<String, Tensor>> snapshot() {
        Map<Integer, Map<String, Tensor>> out = new HashMap<>();
        for (int node : order) {
            Map<String, Tensor> p = params.get(node);
            if (p.isEmpty()) {
                continue;
            }
            Map<String, Tensor> copy = new LinkedHashMap<>();
            p.forEach((k, v) -> copy.put(k, v.copy(false)));
            out.put(node, copy);
        }
        return out;
    }

    public boolean uses(int node) {
        for (int o : order) {
            if (o == node) {
                return true;
            }
        }
        return false;
    }
}
