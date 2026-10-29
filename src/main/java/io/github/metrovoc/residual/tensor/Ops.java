package io.github.metrovoc.residual.tensor;

import java.util.Arrays;

/**
 * Differentiable tensor operations. Every op takes an optional {@link Tape}; when it is non-null and an
 * input requires gradients, the op records how to push gradients back to its inputs.
 * Semantics match the PyTorch functions named in each comment.
 */
public final class Ops {
    private Ops() {
    }

    public enum Activation {
        RELU("relu"), GELU("gelu"), TANH("tanh"), SIGMOID("sigmoid"), SILU("silu");

        public final String id;

        Activation(String id) {
            this.id = id;
        }

        public static Activation byId(String id) {
            for (Activation a : values()) {
                if (a.id.equals(id)) {
                    return a;
                }
            }
            return RELU;
        }
    }

    private static boolean tracks(Tape tape, Tensor... inputs) {
        if (tape == null) {
            return false;
        }
        for (Tensor t : inputs) {
            if (t != null && t.requiresGrad) {
                return true;
            }
        }
        return false;
    }

    private static Tensor result(int[] shape, boolean requiresGrad) {
        return new Tensor(shape, new float[Tensor.numel(shape)], requiresGrad);
    }

    // ------------------------------------------------------------------ matmul

    /**
     * C[m,n] (+)= op(A) . op(B), where A is stored [m,k] (or [k,m] when transA) and B is stored [k,n]
     * (or [n,k] when transB).
     */
    static void gemm(float[] a, int ao, float[] b, int bo, float[] c, int co, int m, int k, int n,
                     boolean transA, boolean transB, boolean accumulate) {
        if (transA && transB) {
            throw new UnsupportedOperationException("gemm TT");
        }
        if (!transA && !transB) {
            Parallel.range(m, (long) k * n, (from, to) -> {
                for (int i = from; i < to; i++) {
                    int ci = co + i * n;
                    if (!accumulate) {
                        Arrays.fill(c, ci, ci + n, 0f);
                    }
                    int ai = ao + i * k;
                    for (int p = 0; p < k; p++) {
                        float av = a[ai + p];
                        if (av == 0f) {
                            continue;
                        }
                        int bp = bo + p * n;
                        for (int j = 0; j < n; j++) {
                            c[ci + j] += av * b[bp + j];
                        }
                    }
                }
            });
        } else if (transB) {
            Parallel.range(m, (long) k * n, (from, to) -> {
                for (int i = from; i < to; i++) {
                    int ai = ao + i * k;
                    int ci = co + i * n;
                    for (int j = 0; j < n; j++) {
                        int bj = bo + j * k;
                        float s = 0f;
                        for (int p = 0; p < k; p++) {
                            s += a[ai + p] * b[bj + p];
                        }
                        c[ci + j] = accumulate ? c[ci + j] + s : s;
                    }
                }
            });
        } else {
            Parallel.range(m, (long) k * n, (from, to) -> {
                for (int i = from; i < to; i++) {
                    int ci = co + i * n;
                    if (!accumulate) {
                        Arrays.fill(c, ci, ci + n, 0f);
                    }
                    for (int p = 0; p < k; p++) {
                        float av = a[ao + p * m + i];
                        if (av == 0f) {
                            continue;
                        }
                        int bp = bo + p * n;
                        for (int j = 0; j < n; j++) {
                            c[ci + j] += av * b[bp + j];
                        }
                    }
                }
            });
        }
    }

    /**
     * {@code a @ b} (or {@code a @ b.transpose(-2, -1)} when {@code transB}).
     * {@code b} is either a shared rank-2 matrix or has the same leading dimensions as {@code a}.
     */
    public static Tensor matmul(Tape tape, Tensor a, Tensor b, boolean transB) {
        if (a.rank() < 2 || b.rank() < 2) {
            throw new ShapeException("Matrix multiply needs matrices, got " + a.shapeString() + " and " + b.shapeString());
        }
        int m = a.dim(-2);
        int k = a.dim(-1);
        int bk = transB ? b.dim(-1) : b.dim(-2);
        int n = transB ? b.dim(-2) : b.dim(-1);
        if (bk != k) {
            throw new ShapeException("Matrix multiply: inner sizes differ (" + k + " vs " + bk + ") for "
                    + a.shapeString() + " @ " + b.shapeString() + (transB ? "ᵀ" : ""));
        }
        boolean shared = b.rank() == 2;
        int batch = a.size() / (m * k);
        if (!shared) {
            if (b.rank() != a.rank() || !Arrays.equals(a.shape, 0, a.rank() - 2, b.shape, 0, b.rank() - 2)) {
                throw new ShapeException("Matrix multiply: batch sizes differ for " + a.shapeString() + " @ " + b.shapeString());
            }
        }
        int[] shape = a.shape.clone();
        shape[shape.length - 1] = n;
        Tensor out = result(shape, tracks(tape, a, b));
        if (shared) {
            gemm(a.data, 0, b.data, 0, out.data, 0, batch * m, k, n, false, transB, false);
        } else {
            for (int bi = 0; bi < batch; bi++) {
                gemm(a.data, bi * m * k, b.data, bi * k * n, out.data, bi * m * n, m, k, n, false, transB, false);
            }
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                if (a.requiresGrad) {
                    float[] ga = a.grad();
                    if (shared) {
                        // dA = dC . op(B)^T
                        gemm(g, 0, b.data, 0, ga, 0, batch * m, n, k, false, !transB, true);
                    } else {
                        for (int bi = 0; bi < batch; bi++) {
                            gemm(g, bi * m * n, b.data, bi * k * n, ga, bi * m * k, m, n, k, false, !transB, true);
                        }
                    }
                }
                if (b.requiresGrad) {
                    float[] gb = b.grad();
                    if (shared) {
                        if (transB) {
                            // dB[n,k] = dC^T . A
                            gemm(g, 0, a.data, 0, gb, 0, n, batch * m, k, true, false, true);
                        } else {
                            // dB[k,n] = A^T . dC
                            gemm(a.data, 0, g, 0, gb, 0, k, batch * m, n, true, false, true);
                        }
                    } else {
                        for (int bi = 0; bi < batch; bi++) {
                            if (transB) {
                                gemm(g, bi * m * n, a.data, bi * m * k, gb, bi * k * n, n, m, k, true, false, true);
                            } else {
                                gemm(a.data, bi * m * k, g, bi * m * n, gb, bi * k * n, k, m, n, true, false, true);
                            }
                        }
                    }
                }
            });
        }
        return out;
    }

    // ------------------------------------------------------------------ elementwise

    private static boolean isSuffix(int[] small, int[] big) {
        if (small.length > big.length) {
            return false;
        }
        int off = big.length - small.length;
        for (int i = 0; i < small.length; i++) {
            if (small[i] != big[off + i]) {
                return false;
            }
        }
        return true;
    }

    /** {@code a + b} where the smaller shape must be a suffix of the larger one (PyTorch broadcasting). */
    public static Tensor add(Tape tape, Tensor a, Tensor b) {
        Tensor big = a.rank() >= b.rank() ? a : b;
        Tensor small = big == a ? b : a;
        if (!isSuffix(small.shape, big.shape)) {
            throw new ShapeException("Can't add " + a.shapeString() + " and " + b.shapeString() + ": shapes don't line up");
        }
        Tensor out = new Tensor(big.shape.clone(), big.data.clone(), tracks(tape, a, b));
        int s = small.size();
        float[] o = out.data;
        float[] sd = small.data;
        for (int i = 0; i < o.length; i++) {
            o[i] += sd[i % s];
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                if (big.requiresGrad) {
                    float[] gb = big.grad();
                    for (int i = 0; i < g.length; i++) {
                        gb[i] += g[i];
                    }
                }
                if (small.requiresGrad) {
                    float[] gs = small.grad();
                    for (int i = 0; i < g.length; i++) {
                        gs[i % s] += g[i];
                    }
                }
            });
        }
        return out;
    }

    /** Elementwise {@code a * b} with the same broadcasting rule as {@link #add}. */
    public static Tensor mul(Tape tape, Tensor a, Tensor b) {
        Tensor big = a.rank() >= b.rank() ? a : b;
        Tensor small = big == a ? b : a;
        if (!isSuffix(small.shape, big.shape)) {
            throw new ShapeException("Can't multiply " + a.shapeString() + " and " + b.shapeString() + " elementwise");
        }
        Tensor out = result(big.shape.clone(), tracks(tape, a, b));
        int s = small.size();
        for (int i = 0; i < out.data.length; i++) {
            out.data[i] = big.data[i] * small.data[i % s];
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                if (big.requiresGrad) {
                    float[] gb = big.grad();
                    for (int i = 0; i < g.length; i++) {
                        gb[i] += g[i] * small.data[i % s];
                    }
                }
                if (small.requiresGrad) {
                    float[] gs = small.grad();
                    for (int i = 0; i < g.length; i++) {
                        gs[i % s] += g[i] * big.data[i];
                    }
                }
            });
        }
        return out;
    }

    /** {@code x * s}. */
    public static Tensor scale(Tape tape, Tensor x, float s) {
        Tensor out = result(x.shape.clone(), tracks(tape, x));
        for (int i = 0; i < x.data.length; i++) {
            out.data[i] = x.data[i] * s;
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gx = x.grad();
                for (int i = 0; i < g.length; i++) {
                    gx[i] += g[i] * s;
                }
            });
        }
        return out;
    }

    private static final float SQRT_2_OVER_PI = (float) Math.sqrt(2.0 / Math.PI);

    /** relu / gelu(approximate='tanh') / tanh / sigmoid / silu. */
    public static Tensor activate(Tape tape, Tensor x, Activation f) {
        Tensor out = result(x.shape.clone(), tracks(tape, x));
        float[] in = x.data;
        float[] o = out.data;
        for (int i = 0; i < in.length; i++) {
            float v = in[i];
            o[i] = switch (f) {
                case RELU -> v > 0f ? v : 0f;
                case GELU -> 0.5f * v * (1f + (float) Math.tanh(SQRT_2_OVER_PI * (v + 0.044715f * v * v * v)));
                case TANH -> (float) Math.tanh(v);
                case SIGMOID -> 1f / (1f + (float) Math.exp(-v));
                case SILU -> v / (1f + (float) Math.exp(-v));
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
                    float v = in[i];
                    float d = switch (f) {
                        case RELU -> v > 0f ? 1f : 0f;
                        case GELU -> {
                            float t = (float) Math.tanh(SQRT_2_OVER_PI * (v + 0.044715f * v * v * v));
                            yield 0.5f * (1f + t) + 0.5f * v * (1f - t * t) * SQRT_2_OVER_PI * (1f + 3f * 0.044715f * v * v);
                        }
                        case TANH -> 1f - o[i] * o[i];
                        case SIGMOID -> o[i] * (1f - o[i]);
                        case SILU -> {
                            float sg = 1f / (1f + (float) Math.exp(-v));
                            yield sg * (1f + v * (1f - sg));
                        }
                    };
                    gx[i] += g[i] * d;
                }
            });
        }
        return out;
    }

    // ------------------------------------------------------------------ softmax & masking

    /** {@code F.softmax(x, dim=-1)}; entries equal to -inf get probability 0. */
    public static Tensor softmax(Tape tape, Tensor x) {
        int d = x.dim(-1);
        int rows = x.rows();
        Tensor out = result(x.shape.clone(), tracks(tape, x));
        float[] in = x.data;
        float[] o = out.data;
        Parallel.range(rows, d * 4L, (from, to) -> {
            for (int r = from; r < to; r++) {
                int off = r * d;
                float max = Float.NEGATIVE_INFINITY;
                for (int j = 0; j < d; j++) {
                    max = Math.max(max, in[off + j]);
                }
                if (max == Float.NEGATIVE_INFINITY) {
                    continue;
                }
                float sum = 0f;
                for (int j = 0; j < d; j++) {
                    float e = (float) Math.exp(in[off + j] - max);
                    o[off + j] = e;
                    sum += e;
                }
                float inv = 1f / sum;
                for (int j = 0; j < d; j++) {
                    o[off + j] *= inv;
                }
            }
        });
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gx = x.grad();
                for (int r = 0; r < rows; r++) {
                    int off = r * d;
                    float dot = 0f;
                    for (int j = 0; j < d; j++) {
                        dot += g[off + j] * o[off + j];
                    }
                    for (int j = 0; j < d; j++) {
                        gx[off + j] += o[off + j] * (g[off + j] - dot);
                    }
                }
            });
        }
        return out;
    }

    /** {@code x.masked_fill(future, -inf)} over the last two dimensions [..., T, T]. */
    public static Tensor causalMask(Tape tape, Tensor x) {
        if (x.rank() < 2 || x.dim(-1) != x.dim(-2)) {
            throw new ShapeException("Causal mask needs square score grids [.., T, T], got " + x.shapeString());
        }
        int t = x.dim(-1);
        int grids = x.size() / (t * t);
        Tensor out = result(x.shape.clone(), tracks(tape, x));
        for (int gi = 0; gi < grids; gi++) {
            int base = gi * t * t;
            for (int i = 0; i < t; i++) {
                for (int j = 0; j < t; j++) {
                    int idx = base + i * t + j;
                    out.data[idx] = j > i ? Float.NEGATIVE_INFINITY : x.data[idx];
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
                    int base = gi * t * t;
                    for (int i = 0; i < t; i++) {
                        for (int j = 0; j <= i; j++) {
                            gx[base + i * t + j] += g[base + i * t + j];
                        }
                    }
                }
            });
        }
        return out;
    }

    // ------------------------------------------------------------------ normalisation

    /** {@code F.layer_norm(x, (d,), gamma, beta, eps)}. */
    public static Tensor layerNorm(Tape tape, Tensor x, Tensor gamma, Tensor beta, float eps) {
        int d = x.dim(-1);
        if (gamma.size() != d || beta.size() != d) {
            throw new ShapeException("Layer norm over " + d + " features has " + gamma.size() + " gains");
        }
        int rows = x.rows();
        Tensor out = result(x.shape.clone(), tracks(tape, x, gamma, beta));
        float[] xhat = new float[x.size()];
        float[] rstd = new float[rows];
        for (int r = 0; r < rows; r++) {
            int off = r * d;
            float mean = 0f;
            for (int j = 0; j < d; j++) {
                mean += x.data[off + j];
            }
            mean /= d;
            float var = 0f;
            for (int j = 0; j < d; j++) {
                float c = x.data[off + j] - mean;
                var += c * c;
            }
            var /= d;
            float rs = (float) (1.0 / Math.sqrt(var + eps));
            rstd[r] = rs;
            for (int j = 0; j < d; j++) {
                float h = (x.data[off + j] - mean) * rs;
                xhat[off + j] = h;
                out.data[off + j] = h * gamma.data[j] + beta.data[j];
            }
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gg = gamma.requiresGrad ? gamma.grad() : null;
                float[] gbeta = beta.requiresGrad ? beta.grad() : null;
                float[] gx = x.requiresGrad ? x.grad() : null;
                for (int r = 0; r < rows; r++) {
                    int off = r * d;
                    float sumG = 0f;
                    float sumGH = 0f;
                    for (int j = 0; j < d; j++) {
                        float gh = g[off + j] * gamma.data[j];
                        sumG += gh;
                        sumGH += gh * xhat[off + j];
                        if (gg != null) {
                            gg[j] += g[off + j] * xhat[off + j];
                        }
                        if (gbeta != null) {
                            gbeta[j] += g[off + j];
                        }
                    }
                    if (gx != null) {
                        float mG = sumG / d;
                        float mGH = sumGH / d;
                        for (int j = 0; j < d; j++) {
                            float gh = g[off + j] * gamma.data[j];
                            gx[off + j] += rstd[r] * (gh - mG - xhat[off + j] * mGH);
                        }
                    }
                }
            });
        }
        return out;
    }

    /** {@code x * rsqrt(mean(x^2, -1) + eps) * gamma}. */
    public static Tensor rmsNorm(Tape tape, Tensor x, Tensor gamma, float eps) {
        int d = x.dim(-1);
        if (gamma.size() != d) {
            throw new ShapeException("RMS norm over " + d + " features has " + gamma.size() + " gains");
        }
        int rows = x.rows();
        Tensor out = result(x.shape.clone(), tracks(tape, x, gamma));
        float[] rinv = new float[rows];
        for (int r = 0; r < rows; r++) {
            int off = r * d;
            float ms = 0f;
            for (int j = 0; j < d; j++) {
                ms += x.data[off + j] * x.data[off + j];
            }
            ms /= d;
            float ri = (float) (1.0 / Math.sqrt(ms + eps));
            rinv[r] = ri;
            for (int j = 0; j < d; j++) {
                out.data[off + j] = x.data[off + j] * ri * gamma.data[j];
            }
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gg = gamma.requiresGrad ? gamma.grad() : null;
                float[] gx = x.requiresGrad ? x.grad() : null;
                for (int r = 0; r < rows; r++) {
                    int off = r * d;
                    float ri = rinv[r];
                    float dot = 0f;
                    for (int j = 0; j < d; j++) {
                        float gn = g[off + j] * gamma.data[j];
                        dot += gn * x.data[off + j];
                        if (gg != null) {
                            gg[j] += g[off + j] * x.data[off + j] * ri;
                        }
                    }
                    if (gx != null) {
                        float c = ri * ri * ri * dot / d;
                        for (int j = 0; j < d; j++) {
                            gx[off + j] += ri * g[off + j] * gamma.data[j] - c * x.data[off + j];
                        }
                    }
                }
            });
        }
        return out;
    }

    // ------------------------------------------------------------------ tokens

    /** {@code F.embedding(ids, table)}: ids [B, T] -> [B, T, d]. */
    public static Tensor embedding(Tape tape, Tokens ids, Tensor table) {
        int v = table.dim(0);
        int d = table.dim(1);
        for (int id : ids.ids) {
            if (id < 0 || id >= v) {
                throw new ShapeException("Token id " + id + " is outside the embedding table (" + v + " rows)");
            }
        }
        Tensor out = result(new int[]{ids.batch, ids.time, d}, tracks(tape, table));
        for (int n = 0; n < ids.ids.length; n++) {
            System.arraycopy(table.data, ids.ids[n] * d, out.data, n * d, d);
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gt = table.grad();
                for (int n = 0; n < ids.ids.length; n++) {
                    int src = n * d;
                    int dst = ids.ids[n] * d;
                    for (int j = 0; j < d; j++) {
                        gt[dst + j] += g[src + j];
                    }
                }
            });
        }
        return out;
    }

    /** {@code table[:T]}: the first T rows of a position table, shape [T, d]. */
    public static Tensor positions(Tape tape, Tensor table, int t) {
        if (t > table.dim(0)) {
            throw new ShapeException("Position table has " + table.dim(0) + " rows but the context is " + t + " tokens long");
        }
        int d = table.dim(1);
        Tensor out = new Tensor(new int[]{t, d}, Arrays.copyOf(table.data, t * d), tracks(tape, table));
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gt = table.grad();
                for (int i = 0; i < g.length; i++) {
                    gt[i] += g[i];
                }
            });
        }
        return out;
    }

    /**
     * Mean cross-entropy between logits [..., V] and integer targets, counting only rows whose weight is
     * non-zero ({@code weights == null} counts every row). Equivalent to {@code F.cross_entropy} with
     * ignored rows removed.
     */
    public static Tensor crossEntropy(Tape tape, Tensor logits, int[] targets, float[] weights) {
        int v = logits.dim(-1);
        int rows = logits.rows();
        if (targets.length != rows) {
            throw new ShapeException("Loss expects " + targets.length + " predictions, got " + rows);
        }
        float[] probs = new float[logits.size()];
        double total = 0;
        double count = 0;
        for (int r = 0; r < rows; r++) {
            float w = weights == null ? 1f : weights[r];
            int off = r * v;
            float max = Float.NEGATIVE_INFINITY;
            for (int j = 0; j < v; j++) {
                max = Math.max(max, logits.data[off + j]);
            }
            double sum = 0;
            for (int j = 0; j < v; j++) {
                float e = (float) Math.exp(logits.data[off + j] - max);
                probs[off + j] = e;
                sum += e;
            }
            float inv = (float) (1.0 / sum);
            for (int j = 0; j < v; j++) {
                probs[off + j] *= inv;
            }
            if (w != 0f) {
                int t = targets[r];
                if (t < 0 || t >= v) {
                    throw new ShapeException("Target token " + t + " is outside the " + v + " logits");
                }
                total += w * (Math.log(sum) + max - logits.data[off + t]);
                count += w;
            }
        }
        float loss = count == 0 ? 0f : (float) (total / count);
        Tensor out = new Tensor(new int[]{1}, new float[]{loss}, tracks(tape, logits));
        final double norm = count;
        if (out.requiresGrad && norm > 0) {
            tape.record(() -> {
                float gl = out.grad()[0];
                float[] gx = logits.grad();
                for (int r = 0; r < rows; r++) {
                    float w = weights == null ? 1f : weights[r];
                    if (w == 0f) {
                        continue;
                    }
                    float c = (float) (gl * w / norm);
                    int off = r * v;
                    for (int j = 0; j < v; j++) {
                        gx[off + j] += c * probs[off + j];
                    }
                    gx[off + targets[r]] -= c;
                }
            });
        }
        return out;
    }

    // ------------------------------------------------------------------ reshaping

    /** [B, T, H*dh] -> [B, H, T, dh], i.e. {@code x.view(B, T, H, dh).transpose(1, 2)}. */
    public static Tensor splitHeads(Tape tape, Tensor x, int heads) {
        if (x.rank() != 3) {
            throw new ShapeException("Split heads expects [batch, time, features], got " + x.shapeString());
        }
        int b = x.dim(0);
        int t = x.dim(1);
        int d = x.dim(2);
        if (heads < 1 || d % heads != 0) {
            throw new ShapeException(d + " features don't split evenly into " + heads + " heads");
        }
        int dh = d / heads;
        Tensor out = result(new int[]{b, heads, t, dh}, tracks(tape, x));
        for (int bi = 0; bi < b; bi++) {
            for (int ti = 0; ti < t; ti++) {
                for (int h = 0; h < heads; h++) {
                    System.arraycopy(x.data, (bi * t + ti) * d + h * dh, out.data, ((bi * heads + h) * t + ti) * dh, dh);
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
                for (int bi = 0; bi < b; bi++) {
                    for (int ti = 0; ti < t; ti++) {
                        for (int h = 0; h < heads; h++) {
                            int src = ((bi * heads + h) * t + ti) * dh;
                            int dst = (bi * t + ti) * d + h * dh;
                            for (int j = 0; j < dh; j++) {
                                gx[dst + j] += g[src + j];
                            }
                        }
                    }
                }
            });
        }
        return out;
    }

    /** [B, H, T, dh] -> [B, T, H*dh], i.e. {@code x.transpose(1, 2).reshape(B, T, H * dh)}. */
    public static Tensor mergeHeads(Tape tape, Tensor x) {
        if (x.rank() != 4) {
            throw new ShapeException("Merge heads expects [batch, heads, time, features], got " + x.shapeString());
        }
        int b = x.dim(0);
        int heads = x.dim(1);
        int t = x.dim(2);
        int dh = x.dim(3);
        int d = heads * dh;
        Tensor out = result(new int[]{b, t, d}, tracks(tape, x));
        for (int bi = 0; bi < b; bi++) {
            for (int h = 0; h < heads; h++) {
                for (int ti = 0; ti < t; ti++) {
                    System.arraycopy(x.data, ((bi * heads + h) * t + ti) * dh, out.data, (bi * t + ti) * d + h * dh, dh);
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
                for (int bi = 0; bi < b; bi++) {
                    for (int h = 0; h < heads; h++) {
                        for (int ti = 0; ti < t; ti++) {
                            int dst = ((bi * heads + h) * t + ti) * dh;
                            int src = (bi * t + ti) * d + h * dh;
                            for (int j = 0; j < dh; j++) {
                                gx[dst + j] += g[src + j];
                            }
                        }
                    }
                }
            });
        }
        return out;
    }

    /** Delays a sequence by {@code n} steps along time (dim -2), filling the start with zeros. */
    public static Tensor shift(Tape tape, Tensor x, int n) {
        if (x.rank() < 2) {
            throw new ShapeException("Shift expects a sequence [.., time, features], got " + x.shapeString());
        }
        int t = x.dim(-2);
        int d = x.dim(-1);
        int seqs = x.size() / (t * d);
        Tensor out = result(x.shape.clone(), tracks(tape, x));
        for (int s = 0; s < seqs; s++) {
            int base = s * t * d;
            for (int ti = n; ti < t; ti++) {
                System.arraycopy(x.data, base + (ti - n) * d, out.data, base + ti * d, d);
            }
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                float[] gx = x.grad();
                for (int s = 0; s < seqs; s++) {
                    int base = s * t * d;
                    for (int ti = n; ti < t; ti++) {
                        int src = base + ti * d;
                        int dst = base + (ti - n) * d;
                        for (int j = 0; j < d; j++) {
                            gx[dst + j] += g[src + j];
                        }
                    }
                }
            });
        }
        return out;
    }

    /** {@code torch.cat([a, b], dim=-1)}. */
    public static Tensor concat(Tape tape, Tensor a, Tensor b) {
        if (a.rank() != b.rank() || !Arrays.equals(a.shape, 0, a.rank() - 1, b.shape, 0, b.rank() - 1)) {
            throw new ShapeException("Can't join " + a.shapeString() + " and " + b.shapeString() + ": only the last size may differ");
        }
        int da = a.dim(-1);
        int db = b.dim(-1);
        int rows = a.rows();
        int[] shape = a.shape.clone();
        shape[shape.length - 1] = da + db;
        Tensor out = result(shape, tracks(tape, a, b));
        for (int r = 0; r < rows; r++) {
            System.arraycopy(a.data, r * da, out.data, r * (da + db), da);
            System.arraycopy(b.data, r * db, out.data, r * (da + db) + da, db);
        }
        if (out.requiresGrad) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = out.grad();
                for (int r = 0; r < rows; r++) {
                    if (a.requiresGrad) {
                        float[] ga = a.grad();
                        for (int j = 0; j < da; j++) {
                            ga[r * da + j] += g[r * (da + db) + j];
                        }
                    }
                    if (b.requiresGrad) {
                        float[] gb = b.grad();
                        for (int j = 0; j < db; j++) {
                            gb[r * db + j] += g[r * (da + db) + da + j];
                        }
                    }
                }
            });
        }
        return out;
    }

    /** Sum of all elements, as a [1] tensor. */
    public static Tensor sum(Tape tape, Tensor x) {
        double s = 0;
        for (float v : x.data) {
            s += v;
        }
        Tensor out = new Tensor(new int[]{1}, new float[]{(float) s}, tracks(tape, x));
        if (out.requiresGrad) {
            tape.record(() -> {
                float g = out.grad()[0];
                float[] gx = x.grad();
                for (int i = 0; i < gx.length; i++) {
                    gx[i] += g;
                }
            });
        }
        return out;
    }
}
