package io.github.metrovoc.residual.graph;

import io.github.metrovoc.residual.graph.CompileException.Issue;
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

/** A checked graph in execution order, owning its parameter tensors. */
public final class CompiledGraph {
    public final GraphSpec spec;
    public final Env env;
    /** Node indices in execution order (only nodes that feed the output). */
    public final int[] order;
    /** Symbolic output shape of every node in {@link #order}; null for unused nodes. */
    public final Sym[] shapes;
    private final List<Map<String, Tensor>> params;

    private CompiledGraph(GraphSpec spec, Env env, int[] order, Sym[] shapes, List<Map<String, Tensor>> params) {
        this.spec = spec;
        this.env = env;
        this.order = order;
        this.shapes = shapes;
        this.params = params;
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
                if (prior != null && Arrays.equals(prior.shape, ps.shape())) {
                    System.arraycopy(prior.data, 0, init.data, 0, init.data.length);
                }
                fresh.put(ps.name(), init);
            }
            params.set(node, fresh);
        }
        int[] ord = order.stream().mapToInt(Integer::intValue).toArray();
        return new CompiledGraph(spec, env, ord, shapes, params);
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

    /** Runs the model on a token batch and returns logits [B, T, V]. */
    public Tensor forward(Tape tape, Tokens tokens, Captures captures) {
        Object[] values = new Object[spec.size()];
        for (int node : order) {
            GraphSpec.Node nd = spec.node(node);
            Object[] in = new Object[nd.op.arity];
            for (int i = 0; i < in.length; i++) {
                int src = nd.inputs[i];
                in[i] = src == GraphSpec.TOKENS ? tokens : values[src];
            }
            Tensor out = nd.op.run(tape, in, params.get(node), nd.config, new Op.Frame(node, tokens.time, captures, nd.fused));
            values[node] = out;
            if (captures != null) {
                captures.output(node, out);
            }
        }
        return (Tensor) values[spec.output];
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
