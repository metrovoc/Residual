package io.github.metrovoc.residual.bench;

import io.github.metrovoc.residual.bench.Spec.Case;
import io.github.metrovoc.residual.bench.Spec.Port;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Ops.Activation;
import io.github.metrovoc.residual.tensor.Prims;
import io.github.metrovoc.residual.tensor.Prims.Binary;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * Every spec, in the order the Bench lists them. Each answer is worked out in double precision with the stable
 * formula, so a design passes by computing the right thing in float32 the way real code has to.
 */
public final class Specs {
    public static final float EPS = 1e-5f;
    private static final Map<String, Spec> ALL = new LinkedHashMap<>();

    public static final Spec MEAN = add("mean", ports("x"), List.of(), List.of(),
            in -> rowMean(in[0]),
            (tape, in) -> mean(tape, in[0]),
            b -> b.output(b.prim(Prim.DIV, b.prim(Prim.SUM, 0), b.prim(Prim.SIZE, 0))),
            new Object[]{"ordinary", vec(2, 4, 6, 8)},
            new Object[]{"longer", vec(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)},
            new Object[]{"rows", mat(2, 3, 1, 2, 3, 4, 5, 6)});

    public static final Spec SOFTMAX = add("softmax", ports("x"), List.of(), List.of(Op.SOFTMAX),
            in -> softmax(in[0]),
            (tape, in) -> Ops.softmax(tape, in[0]),
            b -> {
                int shifted = b.prim(Prim.SUB, 0, b.prim(Prim.AMAX, 0));
                int e = b.prim(Prim.EXP, shifted);
                return b.output(b.prim(Prim.DIV, e, b.prim(Prim.SUM, e)));
            },
            new Object[]{"ordinary", vec(2f, 1f, 0.1f, -1f, 3f)},
            new Object[]{"large", vec(1001f, 1000f, 999.1f, 998f, 1002f)},
            new Object[]{"negative", vec(-997f, -998f, -998.9f, -1000f, -996f)},
            new Object[]{"masked", vec(1.5f, Float.NEGATIVE_INFINITY, 0.5f, Float.NEGATIVE_INFINITY)},
            new Object[]{"rows", mat(2, 3, 1, 2, 3, 1000, 1000, 1000)});

    public static final Spec MATMUL = add("matmul", ports("a", "b"), List.of(), List.of(Op.MATMUL),
            in -> matmul(in[0], in[1]),
            (tape, in) -> Prims.matmul(tape, in[0], in[1]),
            b -> {
                int spread = b.prim(Prim.UNSQUEEZE, 0);
                int columns = b.prim(Prim.TRANSPOSE, 1);
                return b.output(b.prim(Prim.SQUEEZE, b.prim(Prim.SUM, b.prim(Prim.MUL, spread, columns))));
            },
            new Object[]{"square", mat(2, 2, 1, 2, 3, 4), mat(2, 2, 5, 6, 7, 8)},
            new Object[]{"tall", mat(3, 2, 1, 0, -1, 2, 0.5f, 3), mat(2, 4, 2, 1, 0, -1, 1, 3, 2, 0.5f)},
            new Object[]{"row", mat(1, 3, 1, 2, 3), mat(3, 2, 1, -1, 0, 2, 4, 1)});

    public static final Spec LINEAR = add("linear", ports("x", "+W", "+b"), List.of("matmul"), List.of(Op.LINEAR),
            in -> plus(matmul(in[0], in[1]), in[2]),
            (tape, in) -> linear(tape, in[0], in[1], in[2]),
            b -> b.output(b.prim(Prim.ADD, b.module("matmul", 0, 1), 2)),
            new Object[]{"ordinary", rand(1, 3, 4), rand(2, 4, 2), rand(3, 1, 2)},
            new Object[]{"wider", rand(4, 2, 3), rand(5, 3, 5), rand(6, 1, 5)});

    public static final Spec GELU = add("gelu", ports("x"), List.of(), List.of(Op.ACTIVATION),
            in -> map(in[0], Specs::gelu),
            (tape, in) -> Ops.activate(tape, in[0], Activation.GELU),
            b -> {
                int cube = b.prim(Prim.MUL, b.prim(Prim.MUL, 0, 0), 0);
                int inner = b.prim(Prim.MUL, b.constant((float) Math.sqrt(2 / Math.PI)),
                        b.prim(Prim.ADD, 0, b.prim(Prim.MUL, b.constant(0.044715f), cube)));
                int gate = b.prim(Prim.ADD, b.constant(1f), b.prim(Prim.TANH, inner));
                return b.output(b.prim(Prim.MUL, b.prim(Prim.MUL, b.constant(0.5f), 0), gate));
            },
            new Object[]{"ordinary", vec(-3f, -1f, -0.5f, 0f, 0.5f, 1f, 3f)},
            new Object[]{"rows", mat(2, 3, -2f, 0.25f, 4f, 1.5f, -0.75f, 0f)});

    public static final Spec LAYER_NORM = add("layer_norm", ports("x", "+gamma", "+beta"), List.of("mean"), List.of(Op.NORM),
            in -> layerNorm(in[0], in[1], in[2]),
            (tape, in) -> Ops.layerNorm(tape, in[0], in[1], in[2], EPS),
            b -> {
                int centered = b.prim(Prim.SUB, 0, b.module("mean", 0));
                int variance = b.module("mean", b.prim(Prim.MUL, centered, centered));
                int std = b.prim(Prim.SQRT, b.prim(Prim.ADD, variance, b.constant(EPS)));
                return b.output(b.prim(Prim.ADD, b.prim(Prim.MUL, b.prim(Prim.DIV, centered, std), 1), 2));
            },
            new Object[]{"ordinary", mat(2, 4, 1, 2, 3, 4, 2, -1, 0, 5), vec(1, 0.5f, 2, 1), vec(0, 0.1f, -0.2f, 0)},
            new Object[]{"flat", mat(1, 4, 3, 3, 3, 3), vec(1, 0.5f, 2, 1), vec(0, 0.1f, -0.2f, 0)},
            new Object[]{"far", mat(1, 4, 10000, 10001, 10002, 10003), vec(1, 0.5f, 2, 1), vec(0, 0.1f, -0.2f, 0)});

    public static final Spec ATTENTION = add("attention", ports("x", "+Wq", "+Wk", "+Wv", "+Wo"), List.of("softmax", "matmul"),
            List.of(Op.ATTENTION),
            in -> attention(in[0], in[1], in[2], in[3], in[4]),
            (tape, in) -> attention(tape, in[0], in[1], in[2], in[3], in[4]),
            b -> {
                int q = b.module("matmul", 0, 1);
                int k = b.module("matmul", 0, 2);
                int v = b.module("matmul", 0, 3);
                int scores = b.prim(Prim.DIV, b.module("matmul", q, b.prim(Prim.TRANSPOSE, k)), b.prim(Prim.SQRT, b.prim(Prim.SIZE, q)));
                int weights = b.module("softmax", b.prim(Prim.CAUSAL, scores));
                return b.output(b.module("matmul", b.module("matmul", weights, v), 4));
            },
            new Object[]{"four", rand(11, 4, 4), rand(12, 4, 4), rand(13, 4, 4), rand(14, 4, 4), rand(15, 4, 4)},
            new Object[]{"three", rand(21, 3, 2), rand(22, 2, 2), rand(23, 2, 2), rand(24, 2, 2), rand(25, 2, 2)},
            new Object[]{"sharp", scaled(rand(31, 4, 4), 6), scaled(rand(32, 4, 4), 6), scaled(rand(33, 4, 4), 6), rand(34, 4, 4), rand(35, 4, 4)});

    public static final Spec MLP = add("mlp", ports("x", "+W1", "+b1", "+W2", "+b2"), List.of("linear", "gelu"), List.of(Op.MLP),
            in -> plus(matmul(map(plus(matmul(in[0], in[1]), in[2]), Specs::gelu), in[3]), in[4]),
            (tape, in) -> mlp(tape, in[0], in[1], in[2], in[3], in[4]),
            b -> b.output(b.module("linear", b.module("gelu", b.module("linear", 0, 1, 2)), 3, 4)),
            new Object[]{"ordinary", rand(41, 3, 4), rand(42, 4, 8), rand(43, 1, 8), rand(44, 8, 4), rand(45, 1, 4)},
            new Object[]{"narrow", rand(51, 2, 2), rand(52, 2, 4), rand(53, 1, 4), rand(54, 4, 2), rand(55, 1, 2)});

    public static final Spec TRANSFORMER = add("transformer",
            ports("x", "+gamma1", "+beta1", "+Wq", "+Wk", "+Wv", "+Wo", "+gamma2", "+beta2", "+W1", "+b1", "+W2", "+b2"),
            List.of("layer_norm", "attention", "mlp"), List.of(Op.TRANSFORMER),
            in -> {
                M h = plus(in[0], attention(layerNorm(in[0], in[1], in[2]), in[3], in[4], in[5], in[6]));
                M f = layerNorm(h, in[7], in[8]);
                return plus(h, plus(matmul(map(plus(matmul(f, in[9]), in[10]), Specs::gelu), in[11]), in[12]));
            },
            (tape, in) -> {
                Tensor h = Prims.binary(tape, Binary.ADD, in[0],
                        attention(tape, Ops.layerNorm(tape, in[0], in[1], in[2], EPS), in[3], in[4], in[5], in[6]));
                Tensor f = Ops.layerNorm(tape, h, in[7], in[8], EPS);
                return Prims.binary(tape, Binary.ADD, h, mlp(tape, f, in[9], in[10], in[11], in[12]));
            },
            b -> {
                int h = b.prim(Prim.ADD, 0, b.module("attention", b.module("layer_norm", 0, 1, 2), 3, 4, 5, 6));
                return b.output(b.prim(Prim.ADD, h, b.module("mlp", b.module("layer_norm", h, 7, 8), 9, 10, 11, 12)));
            },
            transformerCase("four", 61, 4, 4), transformerCase("three", 71, 3, 2));

    private Specs() {
    }

    public static List<Spec> all() {
        return List.copyOf(ALL.values());
    }

    @Nullable
    public static Spec get(String id) {
        return ALL.get(id);
    }

    /** The spec whose verified design lets the Fabricator make {@code op}, or null for a machine it always makes. */
    @Nullable
    public static Spec unlocking(Op op) {
        for (Spec s : ALL.values()) {
            if (s.unlocks().contains(op)) {
                return s;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- building specs

    /** Ports by name; a leading + marks a parameter, which the fabricated machine learns. */
    private static List<Port> ports(String... names) {
        List<Port> out = new ArrayList<>();
        for (String n : names) {
            out.add(n.startsWith("+") ? new Port(n.substring(1), true) : new Port(n, false));
        }
        return List.copyOf(out);
    }

    private static Spec add(String id, List<Port> ports, List<String> requires, List<Op> unlocks, Function<M[], M> reference,
                            Spec.Kernel kernel, Function<Design.Builder, Design> salvaged, Object[]... cases) {
        List<Case> list = new ArrayList<>();
        for (Object[] c : cases) {
            Tensor[] inputs = new Tensor[c.length - 1];
            M[] exact = new M[c.length - 1];
            for (int i = 1; i < c.length; i++) {
                exact[i - 1] = (M) c[i];
                inputs[i - 1] = ((M) c[i]).tensor();
            }
            list.add(new Case((String) c[0], inputs, reference.apply(exact).tensor()));
        }
        Spec spec = new Spec(id, ports, List.copyOf(list), kernel, requires, unlocks, salvaged);
        ALL.put(id, spec);
        return spec;
    }

    private static Object[] transformerCase(String id, int seed, int t, int d) {
        int h = d * 2;
        return new Object[]{id, rand(seed, t, d), ones(d), zeros(d), rand(seed + 1, d, d), rand(seed + 2, d, d), rand(seed + 3, d, d),
                rand(seed + 4, d, d), ones(d), zeros(d), rand(seed + 5, d, h), rand(seed + 6, 1, h), rand(seed + 7, h, d), rand(seed + 8, 1, d)};
    }

    // ---------------------------------------------------------------- fused kernels

    private static Tensor mean(@Nullable Tape tape, Tensor x) {
        return Prims.binary(tape, Binary.DIV, Prims.sum(tape, x), Prims.size(x));
    }

    private static Tensor linear(@Nullable Tape tape, Tensor x, Tensor w, Tensor b) {
        return Prims.binary(tape, Binary.ADD, Prims.matmul(tape, x, w), b);
    }

    private static Tensor attention(@Nullable Tape tape, Tensor x, Tensor wq, Tensor wk, Tensor wv, Tensor wo) {
        Tensor q = Prims.matmul(tape, x, wq);
        Tensor k = Prims.matmul(tape, x, wk);
        Tensor v = Prims.matmul(tape, x, wv);
        Tensor scores = Ops.scale(tape, Prims.matmul(tape, q, Prims.transpose(tape, k)), (float) (1 / Math.sqrt(q.dim(-1))));
        Tensor weights = Ops.softmax(tape, Ops.causalMask(tape, scores));
        return Prims.matmul(tape, Prims.matmul(tape, weights, v), wo);
    }

    private static Tensor mlp(@Nullable Tape tape, Tensor x, Tensor w1, Tensor b1, Tensor w2, Tensor b2) {
        return linear(tape, Ops.activate(tape, linear(tape, x, w1, b1), Activation.GELU), w2, b2);
    }

    // ---------------------------------------------------------------- exact answers

    /** A double-precision matrix; a vector is one row that remembers it was a vector. */
    record M(int rows, int cols, double[] v, boolean vector) {
        double at(int r, int c) {
            return v[r * cols + c];
        }

        Tensor tensor() {
            float[] f = new float[v.length];
            for (int i = 0; i < v.length; i++) {
                f[i] = (float) v[i];
            }
            return new Tensor(vector ? new int[]{cols} : new int[]{rows, cols}, f, false);
        }
    }

    private static M vec(float... v) {
        double[] d = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            d[i] = v[i];
        }
        return new M(1, v.length, d, true);
    }

    private static M mat(int rows, int cols, float... v) {
        double[] d = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            d[i] = v[i];
        }
        return new M(rows, cols, d, false);
    }

    private static M ones(int n) {
        float[] v = new float[n];
        java.util.Arrays.fill(v, 1f);
        return vec(v);
    }

    private static M zeros(int n) {
        return vec(new float[n]);
    }

    /** Values from a normal distribution, rounded to hundredths so they read well on the Bench. */
    private static M rand(int seed, int rows, int cols) {
        Random r = new Random(seed);
        float[] v = new float[rows * cols];
        for (int i = 0; i < v.length; i++) {
            v[i] = Math.round(r.nextGaussian() * 100) / 100f;
        }
        return rows == 1 ? vec(v) : mat(rows, cols, v);
    }

    private static M scaled(M m, double s) {
        double[] d = m.v.clone();
        for (int i = 0; i < d.length; i++) {
            d[i] = Math.round(d[i] * s * 100) / 100.0;
        }
        return new M(m.rows, m.cols, d, m.vector);
    }

    private static M rowMean(M x) {
        double[] out = new double[x.rows];
        for (int r = 0; r < x.rows; r++) {
            double s = 0;
            for (int c = 0; c < x.cols; c++) {
                s += x.at(r, c);
            }
            out[r] = s / x.cols;
        }
        return x.vector ? new M(1, 1, out, true) : new M(x.rows, 1, out, false);
    }

    private static M softmax(M x) {
        double[] out = new double[x.v.length];
        for (int r = 0; r < x.rows; r++) {
            double m = Double.NEGATIVE_INFINITY;
            for (int c = 0; c < x.cols; c++) {
                m = Math.max(m, x.at(r, c));
            }
            double s = 0;
            for (int c = 0; c < x.cols; c++) {
                out[r * x.cols + c] = Math.exp(x.at(r, c) - m);
                s += out[r * x.cols + c];
            }
            for (int c = 0; c < x.cols; c++) {
                out[r * x.cols + c] /= s;
            }
        }
        return new M(x.rows, x.cols, out, x.vector);
    }

    private static M matmul(M a, M b) {
        double[] out = new double[a.rows * b.cols];
        for (int i = 0; i < a.rows; i++) {
            for (int j = 0; j < b.cols; j++) {
                double s = 0;
                for (int k = 0; k < a.cols; k++) {
                    s += a.at(i, k) * b.at(k, j);
                }
                out[i * b.cols + j] = s;
            }
        }
        return new M(a.rows, b.cols, out, false);
    }

    private static M transpose(M a) {
        double[] out = new double[a.v.length];
        for (int i = 0; i < a.rows; i++) {
            for (int j = 0; j < a.cols; j++) {
                out[j * a.rows + i] = a.at(i, j);
            }
        }
        return new M(a.cols, a.rows, out, false);
    }

    /** {@code a + b}, a row vector {@code b} added to every row. */
    private static M plus(M a, M b) {
        double[] out = new double[a.v.length];
        for (int i = 0; i < a.rows; i++) {
            for (int j = 0; j < a.cols; j++) {
                out[i * a.cols + j] = a.at(i, j) + (b.rows == 1 ? b.at(0, j) : b.at(i, j));
            }
        }
        return new M(a.rows, a.cols, out, a.vector);
    }

    private static M map(M a, java.util.function.DoubleUnaryOperator f) {
        double[] out = new double[a.v.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = f.applyAsDouble(a.v[i]);
        }
        return new M(a.rows, a.cols, out, a.vector);
    }

    private static double gelu(double x) {
        return 0.5 * x * (1 + Math.tanh(Math.sqrt(2 / Math.PI) * (x + 0.044715 * x * x * x)));
    }

    private static M layerNorm(M x, M gamma, M beta) {
        double[] out = new double[x.v.length];
        for (int r = 0; r < x.rows; r++) {
            double mean = 0;
            for (int c = 0; c < x.cols; c++) {
                mean += x.at(r, c);
            }
            mean /= x.cols;
            double var = 0;
            for (int c = 0; c < x.cols; c++) {
                var += (x.at(r, c) - mean) * (x.at(r, c) - mean);
            }
            var /= x.cols;
            for (int c = 0; c < x.cols; c++) {
                out[r * x.cols + c] = (x.at(r, c) - mean) / Math.sqrt(var + EPS) * gamma.v[c] + beta.v[c];
            }
        }
        return new M(x.rows, x.cols, out, x.vector);
    }

    private static M attention(M x, M wq, M wk, M wv, M wo) {
        M q = matmul(x, wq);
        M k = matmul(x, wk);
        M v = matmul(x, wv);
        M s = matmul(q, transpose(k));
        double scale = 1 / Math.sqrt(q.cols);
        double[] masked = new double[s.v.length];
        for (int i = 0; i < s.rows; i++) {
            for (int j = 0; j < s.cols; j++) {
                masked[i * s.cols + j] = j > i ? Double.NEGATIVE_INFINITY : s.at(i, j) * scale;
            }
        }
        return matmul(matmul(softmax(new M(s.rows, s.cols, masked, false)), v), wo);
    }
}
