package io.github.metrovoc.residual.tensor;

import java.util.Arrays;

/**
 * Primitive operations with full NumPy broadcasting, the parts the Bench builds modules from. Each records its own
 * backward pass, so any graph of primitives trains. Reductions run along the last axis and keep it, as
 * {@code keepdim=True} does, so their results broadcast back against their inputs.
 */
public final class Prims {
    private Prims() {
    }

    public enum Binary { ADD, SUB, MUL, DIV, MAX }

    public enum Unary { NEG, EXP, LOG, SQRT, TANH }

    private static boolean tracks(Tape tape, Tensor... inputs) {
        if (tape == null) {
            return false;
        }
        for (Tensor t : inputs) {
            if (t.requiresGrad) {
                return true;
            }
        }
        return false;
    }

    /** A constant of shape [1], which broadcasts against anything. */
    public static Tensor scalar(float v) {
        return new Tensor(new int[]{1}, new float[]{v}, false);
    }

    /** The shape two operands broadcast to, aligning their last dimensions; a dimension of 1 stretches. */
    public static int[] broadcast(int[] a, int[] b) {
        int n = Math.max(a.length, b.length);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            int da = i < n - a.length ? 1 : a[i - (n - a.length)];
            int db = i < n - b.length ? 1 : b[i - (n - b.length)];
            if (da != db && da != 1 && db != 1) {
                throw new ShapeException("Shapes " + Tensor.shapeString(a) + " and " + Tensor.shapeString(b)
                        + " don't line up: lined up from the right, sizes " + da + " and " + db + " differ and neither is 1.");
            }
            out[i] = Math.max(da, db);
        }
        return out;
    }

    /** Strides that read an operand of {@code shape} at each position of the broadcast {@code out} shape. */
    private static int[] strides(int[] shape, int[] out) {
        int[] s = new int[out.length];
        int stride = 1;
        for (int i = shape.length - 1; i >= 0; i--) {
            int o = out.length - shape.length + i;
            s[o] = shape[i] == 1 ? 0 : stride;
            stride *= shape[i];
        }
        return s;
    }

    /** For each element of {@code out}, the index read from an operand with the given strides. */
    private static int[] gather(int[] out, int[] strides) {
        int n = Tensor.numel(out);
        int[] idx = new int[n];
        int[] at = new int[out.length];
        int pos = 0;
        for (int e = 0; e < n; e++) {
            idx[e] = pos;
            for (int d = out.length - 1; d >= 0; d--) {
                at[d]++;
                pos += strides[d];
                if (at[d] < out[d]) {
                    break;
                }
                pos -= strides[d] * at[d];
                at[d] = 0;
            }
        }
        return idx;
    }

    public static Tensor binary(Tape tape, Binary op, Tensor a, Tensor b) {
        int[] shape = broadcast(a.shape, b.shape);
        int[] ia = Arrays.equals(a.shape, shape) ? null : gather(shape, strides(a.shape, shape));
        int[] ib = Arrays.equals(b.shape, shape) ? null : gather(shape, strides(b.shape, shape));
        int n = Tensor.numel(shape);
        Tensor out = new Tensor(shape, new float[n], tracks(tape, a, b));
        float[] o = out.data;
        for (int e = 0; e < n; e++) {
            float x = a.data[ia == null ? e : ia[e]];
            float y = b.data[ib == null ? e : ib[e]];
            o[e] = switch (op) {
                case ADD -> x + y;
                case SUB -> x - y;
                case MUL -> x * y;
                case DIV -> x / y;
                case MAX -> Math.max(x, y);
            };
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] ga = a.requiresGrad ? a.grad() : null;
                float[] gb = b.requiresGrad ? b.grad() : null;
                for (int e = 0; e < n; e++) {
                    int ja = ia == null ? e : ia[e];
                    int jb = ib == null ? e : ib[e];
                    float x = a.data[ja];
                    float y = b.data[jb];
                    switch (op) {
                        case ADD -> {
                            if (ga != null) {
                                ga[ja] += g[e];
                            }
                            if (gb != null) {
                                gb[jb] += g[e];
                            }
                        }
                        case SUB -> {
                            if (ga != null) {
                                ga[ja] += g[e];
                            }
                            if (gb != null) {
                                gb[jb] -= g[e];
                            }
                        }
                        case MUL -> {
                            if (ga != null) {
                                ga[ja] += g[e] * y;
                            }
                            if (gb != null) {
                                gb[jb] += g[e] * x;
                            }
                        }
                        case DIV -> {
                            if (ga != null) {
                                ga[ja] += g[e] / y;
                            }
                            if (gb != null) {
                                gb[jb] -= g[e] * x / (y * y);
                            }
                        }
                        case MAX -> {
                            // ties go to the first operand, as torch.maximum's gradient splits them evenly
                            if (x >= y) {
                                if (ga != null) {
                                    ga[ja] += g[e];
                                }
                            } else if (gb != null) {
                                gb[jb] += g[e];
                            }
                        }
                    }
                }
            });
        }
        return out;
    }

    public static Tensor unary(Tape tape, Unary op, Tensor x) {
        Tensor out = new Tensor(x.shape.clone(), new float[x.size()], tracks(tape, x));
        float[] in = x.data;
        float[] o = out.data;
        for (int i = 0; i < in.length; i++) {
            float v = in[i];
            o[i] = switch (op) {
                case NEG -> -v;
                case EXP -> (float) Math.exp(v);
                case LOG -> (float) Math.log(v);
                case SQRT -> (float) Math.sqrt(v);
                case TANH -> (float) Math.tanh(v);
            };
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gx = x.grad();
                for (int i = 0; i < in.length; i++) {
                    gx[i] += g[i] * switch (op) {
                        case NEG -> -1f;
                        case EXP -> o[i];
                        case LOG -> 1f / in[i];
                        case SQRT -> 0.5f / o[i];
                        case TANH -> 1f - o[i] * o[i];
                    };
                }
            });
        }
        return out;
    }

    private static int[] keepLast(int[] shape) {
        int[] s = shape.length == 0 ? new int[]{1} : shape.clone();
        s[s.length - 1] = 1;
        return s;
    }

    /** {@code x.sum(-1, keepdim=True)}. */
    public static Tensor sum(Tape tape, Tensor x) {
        int d = x.dim(-1);
        int rows = x.rows();
        Tensor out = new Tensor(keepLast(x.shape), new float[rows], tracks(tape, x));
        for (int r = 0; r < rows; r++) {
            float s = 0f;
            for (int j = 0; j < d; j++) {
                s += x.data[r * d + j];
            }
            out.data[r] = s;
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gx = x.grad();
                for (int r = 0; r < rows; r++) {
                    for (int j = 0; j < d; j++) {
                        gx[r * d + j] += g[r];
                    }
                }
            });
        }
        return out;
    }

    /** {@code x.amax(-1, keepdim=True)}; the gradient goes to the first largest entry of each row. */
    public static Tensor amax(Tape tape, Tensor x) {
        int d = x.dim(-1);
        int rows = x.rows();
        Tensor out = new Tensor(keepLast(x.shape), new float[rows], tracks(tape, x));
        int[] arg = new int[rows];
        for (int r = 0; r < rows; r++) {
            int best = 0;
            float m = x.data[r * d];
            for (int j = 1; j < d; j++) {
                // NaN wins, as in PyTorch, so a broken row stays visibly broken
                float v = x.data[r * d + j];
                if (v > m || Float.isNaN(v) && !Float.isNaN(m)) {
                    m = v;
                    best = j;
                }
            }
            out.data[r] = m;
            arg[r] = best;
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gx = x.grad();
                for (int r = 0; r < rows; r++) {
                    gx[r * d + arg[r]] += g[r];
                }
            });
        }
        return out;
    }

    /** {@code x.transpose(-2, -1)}; a vector counts as a single row. */
    public static Tensor transpose(Tape tape, Tensor x) {
        int[] shape = x.rank() == 1 ? new int[]{1, x.dim(0)} : x.shape;
        int m = shape[shape.length - 2];
        int n = shape[shape.length - 1];
        int[] outShape = shape.clone();
        outShape[outShape.length - 2] = n;
        outShape[outShape.length - 1] = m;
        int grids = x.size() / (m * n);
        Tensor out = new Tensor(outShape, new float[x.size()], tracks(tape, x));
        for (int gi = 0; gi < grids; gi++) {
            int base = gi * m * n;
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    out.data[base + j * m + i] = x.data[base + i * n + j];
                }
            }
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gx = x.grad();
                for (int gi = 0; gi < grids; gi++) {
                    int base = gi * m * n;
                    for (int i = 0; i < m; i++) {
                        for (int j = 0; j < n; j++) {
                            gx[base + i * n + j] += g[base + j * m + i];
                        }
                    }
                }
            });
        }
        return out;
    }

    /** The same values under another shape with as many elements, as {@code x.reshape} does. */
    static Tensor view(Tape tape, Tensor x, int[] shape) {
        Tensor out = new Tensor(shape, x.data.clone(), tracks(tape, x));
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gx = x.grad();
                for (int i = 0; i < g.length; i++) {
                    gx[i] += g[i];
                }
            });
        }
        return out;
    }

    /** {@code x.unsqueeze(-2)}: [.., n] becomes [.., 1, n]. */
    public static Tensor unsqueeze(Tape tape, Tensor x) {
        int[] s = new int[x.rank() + 1];
        System.arraycopy(x.shape, 0, s, 0, x.rank() - 1);
        s[s.length - 2] = 1;
        s[s.length - 1] = x.dim(-1);
        return view(tape, x, s);
    }

    /** {@code x.squeeze(-1)}: [.., n, 1] becomes [.., n]. */
    public static Tensor squeeze(Tape tape, Tensor x) {
        if (x.dim(-1) != 1 || x.rank() < 2) {
            throw new ShapeException("Squeeze drops a last axis of size 1, but " + x.shapeString() + " ends in " + x.dim(-1) + ".");
        }
        return view(tape, x, Arrays.copyOf(x.shape, x.rank() - 1));
    }

    /** The length of the last axis, as a constant [1]. */
    public static Tensor size(Tensor x) {
        return scalar(x.dim(-1));
    }

    /**
     * {@code a @ b} for matrices, batches of matrices with matching leading sizes, or a batch against one shared
     * matrix; a vector on the left counts as a single row.
     */
    public static Tensor matmul(Tape tape, Tensor a, Tensor b) {
        Tensor left = a.rank() == 1 ? view(tape, a, new int[]{1, a.dim(0)}) : a;
        if (b.rank() < 2) {
            throw new ShapeException("The right side of a matrix multiply must be a matrix, got " + b.shapeString() + ".");
        }
        return Ops.matmul(tape, left, b, false);
    }

    /** Hides the future: −∞ above the diagonal of the last two axes, which must be square. */
    public static Tensor causal(Tape tape, Tensor x) {
        return Ops.causalMask(tape, x);
    }
}
