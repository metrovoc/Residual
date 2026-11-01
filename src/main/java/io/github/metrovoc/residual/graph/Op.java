package io.github.metrovoc.residual.graph;

import io.github.metrovoc.residual.graph.ParamSpec.Init;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Ops.Activation;
import io.github.metrovoc.residual.tensor.ShapeException;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import io.github.metrovoc.residual.tensor.Unfused;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * Every operator a machine can perform. Input 0 arrives on a machine's back face, input 1 on any side face;
 * the result leaves through the front.
 */
public enum Op {
    EMBEDDING("embedding", 1, ConfigField.width("dim", "32", true)) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            if (!in[0].tokens) {
                throw new ShapeException("residual.shape.embedding", in[0]);
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

    POSITION("position", 0, ConfigField.width("dim", "32", false)) {
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

    LINEAR("linear", 1, ConfigField.width("out", "32", true), ConfigField.toggle("bias", true)) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            features(in[0], "linear");
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
            Tensor y = matmul(tape, (Tensor) in[0], p.get("weight"), false, f);
            Tensor b = p.get("bias");
            return b == null ? y : Ops.add(tape, y, b);
        }
    },

    ACTIVATION("activation", 1, ConfigField.choice("fn", "relu", "relu", "gelu", "tanh", "sigmoid", "silu")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            floats(in[0], "activation");
            return in[0];
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return activate(tape, (Tensor) in[0], Activation.byId(c.get("fn")), f);
        }
    },

    ADD("add", 2) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            return broadcast(in[0], in[1], "add");
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.add(tape, (Tensor) in[0], (Tensor) in[1]);
        }
    },

    MULTIPLY("multiply", 2) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            return broadcast(in[0], in[1], "multiply");
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.mul(tape, (Tensor) in[0], (Tensor) in[1]);
        }
    },

    NORM("norm", 1) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            features(in[0], "norm");
            return in[0];
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            int d = in[0].dim(-1);
            return List.of(new ParamSpec("weight", new int[]{d}, Init.ones()), new ParamSpec("bias", new int[]{d}, Init.zeros()));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return layerNorm(tape, (Tensor) in[0], p.get("weight"), p.get("bias"), f);
        }
    },

    MATMUL("matmul", 2, ConfigField.toggle("transpose", false)) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym a = floats(in[0], "matmul");
            Sym b = floats(in[1], "matmul");
            boolean tb = c.flag("transpose");
            if (a.rank() < 2 || b.rank() < 2) {
                throw new ShapeException("residual.shape.matmul.grids", a, b);
            }
            int bk = tb ? b.dim(-1) : b.dim(-2);
            int n = tb ? b.dim(-2) : b.dim(-1);
            if (a.dim(-1) != bk) {
                throw new ShapeException(tb ? "residual.shape.matmul.inner_transposed" : "residual.shape.matmul.inner",
                        a, Sym.dimName(a.dim(-1)), b, Sym.dimName(bk));
            }
            if (b.rank() != 2) {
                if (b.rank() != a.rank()) {
                    throw new ShapeException("residual.shape.matmul.batch_rank", a, b);
                }
                for (int i = 0; i < a.rank() - 2; i++) {
                    if (a.dim(i) != b.dim(i)) {
                        throw new ShapeException("residual.shape.matmul.batch", a, b);
                    }
                }
            }
            return a.withLast(n);
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return matmul(tape, (Tensor) in[0], (Tensor) in[1], c.flag("transpose"), f);
        }
    },

    SCALE("scale", 1, ConfigField.choice("factor", "inv_sqrt", "inv_sqrt", "0.5", "0.25", "0.125", "2", "4")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            if ("inv_sqrt".equals(c.get("factor"))) {
                features(in[0], "scale_inv_sqrt");
            } else {
                floats(in[0], "scale");
            }
            return in[0];
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            Tensor x = (Tensor) in[0];
            return Ops.scale(tape, x, factor(c, x.dim(-1)));
        }
    },

    CAUSAL_MASK("causal_mask", 1) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = floats(in[0], "causal_mask");
            if (x.rank() < 2 || x.dim(-1) != Sym.T || x.dim(-2) != Sym.T) {
                throw new ShapeException("residual.shape.causal_mask", x);
            }
            return x;
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.causalMask(tape, (Tensor) in[0]);
        }
    },

    SOFTMAX("softmax", 1) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            return floats(in[0], "softmax");
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            Tensor y = softmax(tape, (Tensor) in[0], f);
            if (f.captures() != null && y.rank() >= 3 && y.dim(-1) == y.dim(-2)) {
                f.captures().pattern(f.node(), y);
            }
            return y;
        }
    },

    SPLIT_HEADS("split_heads", 1, ConfigField.choice("heads", "4", "1", "2", "4", "8")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = sequence(in[0], "split_heads");
            int heads = c.integer("heads");
            if (x.dim(-1) % heads != 0) {
                throw new ShapeException("residual.shape.split_heads", x.dim(-1), heads);
            }
            return Sym.of(Sym.B, heads, Sym.T, x.dim(-1) / heads);
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.splitHeads(tape, (Tensor) in[0], c.integer("heads"));
        }
    },

    MERGE_HEADS("merge_heads", 1) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = floats(in[0], "merge_heads");
            if (x.rank() != 4 || x.dim(0) != Sym.B || x.dim(2) != Sym.T || x.dim(1) < 1 || x.dim(3) < 1) {
                throw new ShapeException("residual.shape.merge_heads", x);
            }
            return Sym.of(Sym.B, Sym.T, x.dim(1) * x.dim(3));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.mergeHeads(tape, (Tensor) in[0]);
        }
    },

    SHIFT("shift", 1, ConfigField.choice("steps", "1", "1", "2", "3", "4")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = floats(in[0], "shift");
            if (x.rank() < 2 || x.dim(-2) != Sym.T) {
                throw new ShapeException("residual.shape.shift", x);
            }
            return x;
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.shift(tape, (Tensor) in[0], c.integer("steps"));
        }
    },

    CONCAT("concat", 2) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym a = features(in[0], "concat");
            Sym b = features(in[1], "concat");
            if (a.rank() != b.rank()) {
                throw new ShapeException("residual.shape.concat.rank", a, b);
            }
            for (int i = 0; i < a.rank() - 1; i++) {
                if (a.dim(i) != b.dim(i)) {
                    throw new ShapeException("residual.shape.concat", a, b);
                }
            }
            return a.withLast(a.dim(-1) + b.dim(-1));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return Ops.concat(tape, (Tensor) in[0], (Tensor) in[1]);
        }
    },

    ATTENTION("attention", 1, ConfigField.choice("heads", "4", "1", "2", "4", "8")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = sequence(in[0], "attention");
            if (x.dim(-1) % c.integer("heads") != 0) {
                throw new ShapeException("residual.shape.heads", Component.translatable("residual.who.attention"), x.dim(-1), c.integer("heads"));
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

    MLP("mlp", 1, ConfigField.choice("expand", "4", "1", "2", "4"), ConfigField.choice("fn", "gelu", "relu", "gelu", "silu")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            sequence(in[0], "mlp");
            return in[0];
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            return mlpParams("", in[0].dim(-1), c.integer("expand"));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return mlp(tape, (Tensor) in[0], p, "", Activation.byId(c.get("fn")), f);
        }
    },

    TRANSFORMER("transformer", 1, ConfigField.choice("heads", "4", "1", "2", "4", "8"),
            ConfigField.choice("expand", "4", "1", "2", "4"), ConfigField.choice("fn", "gelu", "relu", "gelu", "silu")) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = sequence(in[0], "transformer");
            if (x.dim(-1) % c.integer("heads") != 0) {
                throw new ShapeException("residual.shape.heads", Component.translatable("residual.who.transformer"), x.dim(-1), c.integer("heads"));
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
            Tensor h = layerNorm(tape, x, p.get("ln1.weight"), p.get("ln1.bias"), f);
            x = Ops.add(tape, x, attention(tape, h, p, "attn.", c.integer("heads"), f));
            h = layerNorm(tape, x, p.get("ln2.weight"), p.get("ln2.bias"), f);
            return Ops.add(tape, x, mlp(tape, h, p, "mlp.", Activation.byId(c.get("fn")), f));
        }
    },

    RMS_NORM("rms_norm", 1) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            features(in[0], "rms_norm");
            return in[0];
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            return List.of(new ParamSpec("weight", new int[]{in[0].dim(-1)}, Init.ones()));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return rmsNorm(tape, (Tensor) in[0], p.get("weight"), f);
        }
    },

    SWIGLU("swiglu", 1) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            sequence(in[0], "swiglu");
            return in[0];
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            return swigluParams("", in[0].dim(-1));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return swiglu(tape, (Tensor) in[0], p, "", f);
        }
    },

    ROTARY("rotary", 1) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = features(in[0], "rotary");
            if (x.rank() < 2 || x.dim(-2) != Sym.T || x.dim(-1) % 2 != 0) {
                throw new ShapeException("residual.shape.rotary", x);
            }
            return x;
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return rotary(tape, (Tensor) in[0], f);
        }
    },

    QK_NORM("qk_norm", 2) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym q = features(in[0], "qk_norm");
            Sym k = features(in[1], "qk_norm");
            if (!q.sameAs(k) || q.rank() < 2 || q.dim(-2) != Sym.T) {
                throw new ShapeException("residual.shape.qk_norm", q, k);
            }
            return q.withLast(Sym.T);
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            return List.of(new ParamSpec("t", new int[]{1}, Init.ones()));
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            Tensor q = Ops.scaled(tape, unit(tape, (Tensor) in[0], f), p.get("t"));
            Tensor k = unit(tape, (Tensor) in[1], f);
            return Ops.scale(tape, matmul(tape, q, k, true, f), (float) (1.0 / Math.sqrt(q.dim(-1))));
        }
    },

    Z_LOSS("z_loss", 1) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = floats(in[0], "z_loss");
            if (x.rank() != 3 || x.dim(-1) != env.vocab()) {
                throw new ShapeException("residual.shape.z_loss", CompiledGraph.vocabShape(env), x);
            }
            return x;
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            Tensor x = (Tensor) in[0];
            if (f.extras() != null) {
                f.extras().penalties.add(Ops.scale(tape, Ops.zLoss(tape, x), Extras.Z_WEIGHT));
            }
            return x;
        }
    },

    MODERN_ATTENTION("modern_attention", 1, ConfigField.choice("heads", "4", "1", "2", "4", "8"), ConfigField.toggle("rotary", true),
            ConfigField.toggle("qk_norm", true)) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = sequence(in[0], "modern_attention");
            modernHeads(x, c, "modern_attention");
            return x;
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            return modernParams("", in[0].dim(-1), c);
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            return modernAttention(tape, (Tensor) in[0], p, "", c, f);
        }
    },

    MODERN_BLOCK("modern_block", 1, ConfigField.choice("heads", "4", "1", "2", "4", "8"), ConfigField.toggle("rotary", true),
            ConfigField.toggle("qk_norm", true)) {
        @Override
        public Sym infer(Sym[] in, Config c, Env env) {
            Sym x = sequence(in[0], "modern_block");
            modernHeads(x, c, "modern_block");
            return x;
        }

        @Override
        public List<ParamSpec> params(Sym[] in, Config c, Env env) {
            int d = in[0].dim(-1);
            List<ParamSpec> ps = new ArrayList<>();
            ps.add(new ParamSpec("rms1.weight", new int[]{d}, Init.ones()));
            ps.addAll(modernParams("attn.", d, c));
            ps.add(new ParamSpec("rms2.weight", new int[]{d}, Init.ones()));
            ps.addAll(swigluParams("mlp.", d));
            return ps;
        }

        @Override
        Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f) {
            Tensor x = (Tensor) in[0];
            x = Ops.add(tape, x, modernAttention(tape, rmsNorm(tape, x, p.get("rms1.weight"), f), p, "attn.", c, f));
            return Ops.add(tape, x, swiglu(tape, rmsNorm(tape, x, p.get("rms2.weight"), f), p, "mlp.", f));
        }
    };

    public static final float EPS = 1e-5f;

    public final String id;
    public final int arity;
    private final List<ConfigField> fields;

    Op(String id, int arity, ConfigField... fields) {
        this.id = id;
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
        return this == ATTENTION || this == MLP || this == TRANSFORMER || this == SWIGLU || this == MODERN_ATTENTION || this == MODERN_BLOCK;
    }

    /** Whether its picture is the attention pattern of its first head. */
    public boolean attends() {
        return this == ATTENTION || this == TRANSFORMER || this == MODERN_ATTENTION || this == MODERN_BLOCK;
    }

    /** Output shape for the given input shapes; throws {@link ShapeException} with a player-facing reason. */
    public abstract Sym infer(Sym[] in, Config c, Env env);

    public List<ParamSpec> params(Sym[] in, Config c, Env env) {
        return List.of();
    }

    abstract Tensor run(Tape tape, Object[] in, Map<String, Tensor> p, Config c, Frame f);

    /**
     * Per-call context: the node being run, the sequence length, an optional capture sink, whether the machine was
     * fabricated from a verified design (fused kernels) or a salvaged one (one primitive at a time), and, while
     * training, what it adds to the loss.
     */
    record Frame(int node, int time, Captures captures, boolean fused, Extras extras) {
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

    /** {@code who} names the machine in messages: {@code residual.who.<who>}, with its article in English. */
    static Sym floats(Sym x, String who) {
        if (x.tokens) {
            throw new ShapeException("residual.shape.tokens", Component.translatable("residual.who." + who));
        }
        return x;
    }

    static Sym features(Sym x, String who) {
        floats(x, who);
        if (x.rank() < 1 || x.dim(-1) < 0) {
            throw new ShapeException(x.dim(-1) == Sym.T ? "residual.shape.features_t" : "residual.shape.features",
                    Component.translatable("residual.who." + who), x, Sym.dimName(x.dim(-1)));
        }
        return x;
    }

    static Sym sequence(Sym x, String who) {
        features(x, who);
        if (x.rank() != 3 || x.dim(0) != Sym.B || x.dim(1) != Sym.T) {
            throw new ShapeException("residual.shape.sequence", Component.translatable("residual.who." + who), x);
        }
        return x;
    }

    static Sym broadcast(Sym a, Sym b, String verb) {
        floats(a, "junction");
        floats(b, "junction");
        Sym big = a.rank() >= b.rank() ? a : b;
        Sym small = big == a ? b : a;
        int off = big.rank() - small.rank();
        for (int i = 0; i < small.rank(); i++) {
            if (small.dim(i) != big.dim(off + i)) {
                throw new ShapeException("residual.shape.broadcast." + verb, a, b);
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
        Tensor q = Ops.splitHeads(tape, matmul(tape, x, p.get(prefix + "q"), false, f), heads);
        Tensor k = Ops.splitHeads(tape, matmul(tape, x, p.get(prefix + "k"), false, f), heads);
        Tensor v = Ops.splitHeads(tape, matmul(tape, x, p.get(prefix + "v"), false, f), heads);
        Tensor scores = Ops.scale(tape, matmul(tape, q, k, true, f), (float) (1.0 / Math.sqrt(dh)));
        Tensor probs = softmax(tape, Ops.causalMask(tape, scores), f);
        if (f.captures() != null) {
            f.captures().pattern(f.node(), probs);
        }
        Tensor y = Ops.mergeHeads(tape, matmul(tape, probs, v, false, f));
        return matmul(tape, y, p.get(prefix + "o"), false, f);
    }

    /** What the modern block's feed-forward part widens to: 8/3 of its width costs as many weights as a GELU block's 4. */
    static int swigluWidth(int d) {
        return Math.round(8f * d / 3f);
    }

    static List<ParamSpec> swigluParams(String prefix, int d) {
        int h = swigluWidth(d);
        return List.of(
                new ParamSpec(prefix + "w1", new int[]{d, h}, Init.fanIn()),
                new ParamSpec(prefix + "w2", new int[]{d, h}, Init.fanIn()),
                new ParamSpec(prefix + "w3", new int[]{h, d}, Init.fanIn()));
    }

    static Tensor swiglu(Tape tape, Tensor x, Map<String, Tensor> p, String prefix, Frame f) {
        Tensor gate = activate(tape, matmul(tape, x, p.get(prefix + "w1"), false, f), Activation.SILU, f);
        Tensor gated = Ops.mul(tape, gate, matmul(tape, x, p.get(prefix + "w2"), false, f));
        return matmul(tape, gated, p.get(prefix + "w3"), false, f);
    }

    /** The checks the modern attention machines make of their width and heads. */
    static void modernHeads(Sym x, Config c, String who) {
        int heads = c.integer("heads");
        if (x.dim(-1) % heads != 0) {
            throw new ShapeException("residual.shape.heads", Component.translatable("residual.who." + who), x.dim(-1), heads);
        }
        if (c.flag("rotary") && x.dim(-1) / heads % 2 != 0) {
            throw new ShapeException("residual.shape.rotary_heads", Component.translatable("residual.who." + who), x.dim(-1) / heads);
        }
    }

    static List<ParamSpec> modernParams(String prefix, int d, Config c) {
        List<ParamSpec> ps = new ArrayList<>(attentionParams(prefix, d));
        if (c.flag("qk_norm")) {
            ps.add(new ParamSpec(prefix + "t", new int[]{1}, Init.ones()));
        }
        return ps;
    }

    /**
     * Attention as the late designs do it: queries and keys turned by their rows' places, then normalized with one learned
     * temperature, so a score can't exceed t·√d however large they grow. A verified machine never holds the T×T scores
     * ({@link Ops#flashAttention}); the plain path is the salvaged machine's, and what a picture of the pattern needs.
     */
    static Tensor modernAttention(Tape tape, Tensor x, Map<String, Tensor> p, String prefix, Config c, Frame f) {
        int heads = c.integer("heads");
        int dh = x.dim(-1) / heads;
        float scale = (float) (1.0 / Math.sqrt(dh));
        Tensor q = Ops.splitHeads(tape, matmul(tape, x, p.get(prefix + "q"), false, f), heads);
        Tensor k = Ops.splitHeads(tape, matmul(tape, x, p.get(prefix + "k"), false, f), heads);
        Tensor v = Ops.splitHeads(tape, matmul(tape, x, p.get(prefix + "v"), false, f), heads);
        if (c.flag("rotary")) {
            q = rotary(tape, q, f);
            k = rotary(tape, k, f);
        }
        if (c.flag("qk_norm")) {
            q = Ops.scaled(tape, unit(tape, q, f), p.get(prefix + "t"));
            k = unit(tape, k, f);
        }
        Tensor y;
        if (f.fused() && f.captures() == null) {
            y = Ops.flashAttention(tape, q, k, v, scale);
        } else {
            Tensor probs = softmax(tape, Ops.causalMask(tape, Ops.scale(tape, matmul(tape, q, k, true, f), scale)), f);
            if (f.captures() != null) {
                f.captures().pattern(f.node(), probs);
            }
            y = matmul(tape, probs, v, false, f);
        }
        return matmul(tape, Ops.mergeHeads(tape, y), p.get(prefix + "o"), false, f);
    }

    static Tensor mlp(Tape tape, Tensor x, Map<String, Tensor> p, String prefix, Activation fn, Frame f) {
        Tensor h = Ops.add(tape, matmul(tape, x, p.get(prefix + "w1"), false, f), p.get(prefix + "b1"));
        h = activate(tape, h, fn, f);
        return Ops.add(tape, matmul(tape, h, p.get(prefix + "w2"), false, f), p.get(prefix + "b2"));
    }

    // ------------------------------------------------------------------ fused or not

    static Tensor matmul(Tape tape, Tensor a, Tensor b, boolean transB, Frame f) {
        return f.fused() ? Ops.matmul(tape, a, b, transB) : Unfused.matmul(tape, a, b, transB);
    }

    static Tensor softmax(Tape tape, Tensor x, Frame f) {
        if (f.captures() != null) {
            f.captures().score(x);
        }
        return f.fused() ? Ops.softmax(tape, x) : Unfused.softmax(tape, x);
    }

    static Tensor layerNorm(Tape tape, Tensor x, Tensor gamma, Tensor beta, Frame f) {
        return f.fused() ? Ops.layerNorm(tape, x, gamma, beta, EPS) : Unfused.layerNorm(tape, x, gamma, beta, EPS);
    }

    static Tensor rmsNorm(Tape tape, Tensor x, Tensor gamma, Frame f) {
        return f.fused() ? Ops.rmsNorm(tape, x, gamma, EPS) : Unfused.rmsNorm(tape, x, gamma, EPS);
    }

    /** RMS norm with no gain: every vector's numbers come out with a root mean square of 1. */
    static Tensor unit(Tape tape, Tensor x, Frame f) {
        float[] ones = new float[x.dim(-1)];
        Arrays.fill(ones, 1f);
        return rmsNorm(tape, x, Tensor.of(new int[]{ones.length}, ones), f);
    }

    static Tensor rotary(Tape tape, Tensor x, Frame f) {
        return f.fused() ? Ops.rotary(tape, x) : Unfused.rotary(tape, x);
    }

    static Tensor activate(Tape tape, Tensor x, Activation fn, Frame f) {
        return f.fused() ? Ops.activate(tape, x, fn) : Unfused.activate(tape, x, fn);
    }
}
