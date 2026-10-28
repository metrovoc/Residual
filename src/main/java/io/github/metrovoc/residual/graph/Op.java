package io.github.metrovoc.residual.graph;

import io.github.metrovoc.residual.graph.ParamSpec.Init;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Ops.Activation;
import io.github.metrovoc.residual.tensor.ShapeException;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every operator a machine can perform. Input 0 arrives on a machine's back face, input 1 on any side face;
 * the result leaves through the front.
 */
public enum Op {
    EMBEDDING("embedding", "Embedding Table", 1, ConfigField.width("dim", "32", true)) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            if (!in[0].tokens) {
                throw new ShapeException("The Embedding Table looks up token ids, so it must be fed directly by the Model Core. It got " + in[0] + " instead.");
            }
            return Sym.of(Sym.B, Sym.T, c.width("dim", env.vocab()));
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            return List.of(new ParamSpec("weight", new int[]{env.vocab(), c.width("dim", env.vocab())}, Init.normal(1f)));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.embedding(tape, (Tokens) in[0], p.get("weight"));
        }
    },

    POSITION("position", "Position Table", 0, ConfigField.width("dim", "32", false)) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            return Sym.of(Sym.T, c.width("dim", env.vocab()));
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            return List.of(new ParamSpec("weight", new int[]{env.context(), c.width("dim", env.vocab())}, Init.normal(1f)));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.positions(tape, p.get("weight"), f.time());
        }
    },

    LINEAR("linear", "Linear Layer", 1, ConfigField.width("out", "32", true), ConfigField.toggle("bias", true)) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            features(in[0], "A Linear Layer");
            return in[0].withLast(c.width("out", env.vocab()));
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            int fanIn = in[0].dim(-1);
            int out = c.width("out", env.vocab());
            List<ParamSpec> ps = new ArrayList<>();
            ps.add(new ParamSpec("weight", new int[]{fanIn, out}, Init.fanIn()));
            if (c.flag("bias")) {
                ps.add(new ParamSpec("bias", new int[]{out}, Init.zeros()));
            }
            return ps;
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            Tensor y = Ops.matmul(tape, (Tensor) in[0], p.get("weight"), false);
            Tensor b = p.get("bias");
            return b == null ? y : Ops.add(tape, y, b);
        }
    },

    ACTIVATION("activation", "Activation", 1, ConfigField.choice("fn", "relu", "relu", "gelu", "tanh", "sigmoid", "silu")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            floats(in[0], "An Activation");
            return in[0];
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.activate(tape, (Tensor) in[0], Activation.byId(c.get("fn")));
        }
    },

    ADD("add", "Residual Junction", 2) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            return broadcast(in[0], in[1], "add");
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.add(tape, (Tensor) in[0], (Tensor) in[1]);
        }
    },

    MULTIPLY("multiply", "Gate", 2) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            return broadcast(in[0], in[1], "multiply");
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.mul(tape, (Tensor) in[0], (Tensor) in[1]);
        }
    },

    NORM("norm", "Normalizer", 1, ConfigField.choice("kind", "layer", "layer", "rms")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            features(in[0], "A Normalizer");
            return in[0];
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            int d = in[0].dim(-1);
            return "rms".equals(c.get("kind"))
                    ? List.of(new ParamSpec("weight", new int[]{d}, Init.ones()))
                    : List.of(new ParamSpec("weight", new int[]{d}, Init.ones()), new ParamSpec("bias", new int[]{d}, Init.zeros()));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            Tensor x = (Tensor) in[0];
            return "rms".equals(c.get("kind"))
                    ? Ops.rmsNorm(tape, x, p.get("weight"), EPS)
                    : Ops.layerNorm(tape, x, p.get("weight"), p.get("bias"), EPS);
        }
    },

    MATMUL("matmul", "Matrix Multiplier", 2, ConfigField.toggle("transpose", false)) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym a = floats(in[0], "The Matrix Multiplier");
            Sym b = floats(in[1], "The Matrix Multiplier");
            boolean tb = c.flag("transpose");
            if (a.rank() < 2 || b.rank() < 2) {
                throw new ShapeException("The Matrix Multiplier needs grids on both inputs, got " + a + " and " + b + ".");
            }
            int bk = tb ? b.dim(-1) : b.dim(-2);
            int n = tb ? b.dim(-2) : b.dim(-1);
            if (a.dim(-1) != bk) {
                throw new ShapeException("Matrix Multiplier: the back input " + a + " has rows of " + Sym.dimName(a.dim(-1))
                        + " but the side input " + b + (tb ? " (transposed)" : "") + " has columns of " + Sym.dimName(bk)
                        + ". The inner sizes must match" + (tb ? "." : " - try the transpose switch."));
            }
            if (b.rank() != 2) {
                if (b.rank() != a.rank()) {
                    throw new ShapeException("Matrix Multiplier: " + a + " and " + b + " have different numbers of batch dimensions.");
                }
                for (int i = 0; i < a.rank() - 2; i++) {
                    if (a.dim(i) != b.dim(i)) {
                        throw new ShapeException("Matrix Multiplier: batch sizes of " + a + " and " + b + " differ.");
                    }
                }
            }
            return a.withLast(n);
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.matmul(tape, (Tensor) in[0], (Tensor) in[1], c.flag("transpose"));
        }
    },

    SCALE("scale", "Scaler", 1, ConfigField.choice("factor", "inv_sqrt", "inv_sqrt", "0.5", "0.25", "0.125", "2", "4")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            if ("inv_sqrt".equals(c.get("factor"))) {
                features(in[0], "A Scaler set to 1/√d");
            } else {
                floats(in[0], "A Scaler");
            }
            return in[0];
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            Tensor x = (Tensor) in[0];
            return Ops.scale(tape, x, factor(c, x.dim(-1)));
        }
    },

    CAUSAL_MASK("causal_mask", "Causal Mask", 1) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = floats(in[0], "The Causal Mask");
            if (x.rank() < 2 || x.dim(-1) != Sym.T || x.dim(-2) != Sym.T) {
                throw new ShapeException("The Causal Mask hides future positions in a grid of scores [.., T×T], one row per token. It got " + x + ".");
            }
            return x;
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.causalMask(tape, (Tensor) in[0]);
        }
    },

    SOFTMAX("softmax", "Softmax", 1) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            return floats(in[0], "Softmax");
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            Tensor y = Ops.softmax(tape, (Tensor) in[0]);
            if (f.captures() != null && y.rank() >= 3 && y.dim(-1) == y.dim(-2)) {
                f.captures().pattern(f.node(), y);
            }
            return y;
        }
    },

    SPLIT_HEADS("split_heads", "Head Splitter", 1, ConfigField.choice("heads", "4", "1", "2", "4", "8")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = sequence(in[0], "The Head Splitter");
            int heads = c.integer("heads");
            if (x.dim(-1) % heads != 0) {
                throw new ShapeException("The Head Splitter can't divide " + x.dim(-1) + " features into " + heads + " equal heads.");
            }
            return Sym.of(Sym.B, heads, Sym.T, x.dim(-1) / heads);
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.splitHeads(tape, (Tensor) in[0], c.integer("heads"));
        }
    },

    MERGE_HEADS("merge_heads", "Head Merger", 1) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = floats(in[0], "The Head Merger");
            if (x.rank() != 4 || x.dim(0) != Sym.B || x.dim(2) != Sym.T || x.dim(1) < 1 || x.dim(3) < 1) {
                throw new ShapeException("The Head Merger joins heads [B×H×T×d] back into [B×T×(H·d)]. It got " + x + ".");
            }
            return Sym.of(Sym.B, Sym.T, x.dim(1) * x.dim(3));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.mergeHeads(tape, (Tensor) in[0]);
        }
    },

    SHIFT("shift", "Delay Line", 1, ConfigField.choice("steps", "1", "1", "2", "3", "4")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = floats(in[0], "The Delay Line");
            if (x.rank() < 2 || x.dim(-2) != Sym.T) {
                throw new ShapeException("The Delay Line shifts a sequence along time [.., T×d]. It got " + x + ".");
            }
            return x;
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.shift(tape, (Tensor) in[0], c.integer("steps"));
        }
    },

    CONCAT("concat", "Concatenator", 2) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym a = features(in[0], "The Concatenator");
            Sym b = features(in[1], "The Concatenator");
            if (a.rank() != b.rank()) {
                throw new ShapeException("The Concatenator can only join values of the same rank: " + a + " and " + b + ".");
            }
            for (int i = 0; i < a.rank() - 1; i++) {
                if (a.dim(i) != b.dim(i)) {
                    throw new ShapeException("The Concatenator joins along features; everything else must match: " + a + " and " + b + ".");
                }
            }
            return a.withLast(a.dim(-1) + b.dim(-1));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.concat(tape, (Tensor) in[0], (Tensor) in[1]);
        }
    },

    ATTENTION("attention", "Attention Head", 1, ConfigField.choice("heads", "4", "1", "2", "4", "8")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = sequence(in[0], "An Attention Head");
            if (x.dim(-1) % c.integer("heads") != 0) {
                throw new ShapeException("An Attention Head can't split " + x.dim(-1) + " features into " + c.integer("heads") + " heads.");
            }
            return x;
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            int d = in[0].dim(-1);
            return attentionParams("", d);
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return attention(tape, (Tensor) in[0], p, "", c.integer("heads"), f);
        }
    },

    MLP("mlp", "Feed-Forward Block", 1, ConfigField.choice("expand", "4", "1", "2", "4"), ConfigField.choice("fn", "gelu", "relu", "gelu", "silu")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            sequence(in[0], "A Feed-Forward Block");
            return in[0];
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            return mlpParams("", in[0].dim(-1), c.integer("expand"));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return mlp(tape, (Tensor) in[0], p, "", Activation.byId(c.get("fn")));
        }
    },

    TRANSFORMER("transformer", "Transformer Block", 1, ConfigField.choice("heads", "4", "1", "2", "4", "8"),
            ConfigField.choice("expand", "4", "1", "2", "4"), ConfigField.choice("fn", "gelu", "relu", "gelu", "silu")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = sequence(in[0], "A Transformer Block");
            if (x.dim(-1) % c.integer("heads") != 0) {
                throw new ShapeException("A Transformer Block can't split " + x.dim(-1) + " features into " + c.integer("heads") + " heads.");
            }
            return x;
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            int d = in[0].dim(-1);
            List<ParamSpec> ps = new ArrayList<>();
            ps.add(new ParamSpec("ln1.weight", new int[]{d}, Init.ones()));
            ps.add(new ParamSpec("ln1.bias", new int[]{d}, Init.zeros()));
            ps.addAll(attentionParams("attn.", d));
            ps.add(new ParamSpec("ln2.weight", new int[]{d}, Init.ones()));
            ps.add(new ParamSpec("ln2.bias", new int[]{d}, Init.zeros()));
            ps.addAll(mlpParams("mlp.", d, c.integer("expand")));
            return ps;
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            Tensor x = (Tensor) in[0];
            Tensor h = Ops.layerNorm(tape, x, p.get("ln1.weight"), p.get("ln1.bias"), EPS);
            x = Ops.add(tape, x, attention(tape, h, p, "attn.", c.integer("heads"), f));
            h = Ops.layerNorm(tape, x, p.get("ln2.weight"), p.get("ln2.bias"), EPS);
            return Ops.add(tape, x, mlp(tape, h, p, "mlp.", Activation.byId(c.get("fn"))));
        }
    };

    public static final float EPS = 1e-5f;

    public final String id;
    public final String title;
    public final int arity;
    private final List<ConfigField> fields;

    Op(String id, String title, int arity, ConfigField... fields) {
        this.id = id;
        this.title = title;
        this.arity = arity;
        this.fields = List.of(fields);
    }

    public List<ConfigField> fields() {
        return fields;
    }

    public ConfigField field(String key) {
        for (ConfigField f : fields) {
            if (f.key().equals(key)) {
                return f;
            }
        }
        return null;
    }

    public boolean compound() {
        return this == ATTENTION || this == MLP || this == TRANSFORMER;
    }

    /** Output shape for the given input shapes; throws {@link ShapeException} with a player-facing reason. */
    public abstract Sym infer(Sym[] in, Config c, Env env);

    public List<ParamSpec> params(Sym[] in, Config c, Env env) {
        return List.of();
    }

    abstract Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f);

    /** Per-call context: the node being run, the sequence length and an optional capture sink. */
    record Frame(int node, int time, Captures captures) {
    }

    public static Op byId(String id) {
        for (Op op : values()) {
            if (op.id.equals(id)) {
                return op;
            }
        }
        return null;
    }

    static float factor(Config c, int features) {
        String f = c.get("factor");
        return "inv_sqrt".equals(f) ? (float) (1.0 / Math.sqrt(features)) : Float.parseFloat(f);
    }

    // ------------------------------------------------------------------ checks

    static Sym floats(Sym x, String who) {
        if (x.tokens) {
            throw new ShapeException(who + " can't work on raw token ids. Turn them into vectors with an Embedding Table first.");
        }
        return x;
    }

    static Sym features(Sym x, String who) {
        floats(x, who);
        if (x.rank() < 1 || x.dim(-1) < 0) {
            throw new ShapeException(who + " needs a fixed number of features, but the last size of " + x + " is "
                    + Sym.dimName(x.dim(-1)) + (x.dim(-1) == Sym.T ? " (the sequence length)." : "."));
        }
        return x;
    }

    static Sym sequence(Sym x, String who) {
        features(x, who);
        if (x.rank() != 3 || x.dim(0) != Sym.B || x.dim(1) != Sym.T) {
            throw new ShapeException(who + " works on a sequence of vectors [B×T×d]. It got " + x + ".");
        }
        return x;
    }

    static Sym broadcast(Sym a, Sym b, String verb) {
        floats(a, "This junction");
        floats(b, "This junction");
        Sym big = a.rank() >= b.rank() ? a : b;
        Sym small = big == a ? b : a;
        int off = big.rank() - small.rank();
        for (int i = 0; i < small.rank(); i++) {
            if (small.dim(i) != big.dim(off + i)) {
                throw new ShapeException("Can't " + verb + " " + a + " and " + b + ": their sizes don't line up.");
            }
        }
        return big;
    }

    // ------------------------------------------------------------------ compound blocks

    static List<ParamSpec> attentionParams(String prefix, int d) {
        return List.of(
                new ParamSpec(prefix + "q", new int[]{d, d}, Init.fanIn()),
                new ParamSpec(prefix + "k", new int[]{d, d}, Init.fanIn()),
                new ParamSpec(prefix + "v", new int[]{d, d}, Init.fanIn()),
                new ParamSpec(prefix + "o", new int[]{d, d}, Init.fanIn()));
    }

    static List<ParamSpec> mlpParams(String prefix, int d, int expand) {
        int h = d * expand;
        return List.of(
                new ParamSpec(prefix + "w1", new int[]{d, h}, Init.fanIn()),
                new ParamSpec(prefix + "b1", new int[]{h}, Init.zeros()),
                new ParamSpec(prefix + "w2", new int[]{h, d}, Init.fanIn()),
                new ParamSpec(prefix + "b2", new int[]{d}, Init.zeros()));
    }

    static Tensor attention(Tape tape, Tensor x, Map<String, Tensor> p, String prefix, int heads, Frame f) {
        int dh = x.dim(-1) / heads;
        Tensor q = Ops.splitHeads(tape, Ops.matmul(tape, x, p.get(prefix + "q"), false), heads);
        Tensor k = Ops.splitHeads(tape, Ops.matmul(tape, x, p.get(prefix + "k"), false), heads);
        Tensor v = Ops.splitHeads(tape, Ops.matmul(tape, x, p.get(prefix + "v"), false), heads);
        Tensor scores = Ops.scale(tape, Ops.matmul(tape, q, k, true), (float) (1.0 / Math.sqrt(dh)));
        Tensor probs = Ops.softmax(tape, Ops.causalMask(tape, scores));
        if (f.captures() != null) {
            f.captures().pattern(f.node(), probs);
        }
        Tensor y = Ops.mergeHeads(tape, Ops.matmul(tape, probs, v, false));
        return Ops.matmul(tape, y, p.get(prefix + "o"), false);
    }

    static Tensor mlp(Tape tape, Tensor x, Map<String, Tensor> p, String prefix, Activation fn) {
        Tensor h = Ops.add(tape, Ops.matmul(tape, x, p.get(prefix + "w1"), false), p.get(prefix + "b1"));
        h = Ops.activate(tape, h, fn);
        return Ops.add(tape, Ops.matmul(tape, h, p.get(prefix + "w2"), false), p.get(prefix + "b2"));
    }
}
