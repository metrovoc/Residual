package io.github.metrovoc.residual.tensor;

import java.util.List;

/**
 * {@code torch.optim.Muon} on the hidden matrices, with {@code match_rms_adamw}: each matrix steps along its momentum with
 * every singular value pushed towards 1, so all its directions move alike, and a learning rate tuned for AdamW carries over.
 * Everything else (token and position tables, the model's last layer, vectors) goes on with AdamW, as it does in practice.
 */
public final class Muon implements Optimizer {
    /** The quintic Newton-Schulz iteration: X ← a·X + (b·G + c·G²)·X, with G = X·Xᵀ. */
    public static final float A = 3.4445f, B = -4.7750f, C = 2.0315f;
    public static final int NEWTON_STEPS = 5;
    private static final float MOMENTUM = 0.95f;
    private static final float WEIGHT_DECAY = 0.01f;
    private static final double EPS = 1e-7;

    private final Tensor[] matrices;
    private final float[][] momentum;
    private final AdamW rest;

    public Muon(List<Tensor> hidden, List<Tensor> others) {
        matrices = hidden.toArray(new Tensor[0]);
        momentum = new float[matrices.length][];
        for (int i = 0; i < matrices.length; i++) {
            momentum[i] = new float[matrices[i].size()];
        }
        rest = new AdamW(others);
    }

    /** One step of the iteration on an r×c matrix, written over {@code x}. */
    static void newtonStep(float[] x, int r, int c) {
        int rows = Math.min(r, c);
        int cols = Math.max(r, c);
        boolean wide = r <= c;
        // X as rows×cols, its short side first, so G stays small
        float[] m = wide ? x : transposed(x, r, c);
        float[] g = new float[rows * rows];
        Ops.gemm(m, 0, m, 0, g, 0, rows, cols, rows, false, true, false);
        float[] gg = new float[rows * rows];
        Ops.gemm(g, 0, g, 0, gg, 0, rows, rows, rows, false, false, false);
        for (int i = 0; i < g.length; i++) {
            g[i] = B * g[i] + C * gg[i];
        }
        float[] update = new float[rows * cols];
        Ops.gemm(g, 0, m, 0, update, 0, rows, rows, cols, false, false, false);
        for (int i = 0; i < m.length; i++) {
            m[i] = A * m[i] + update[i];
        }
        if (!wide) {
            float[] back = transposed(m, rows, cols);
            System.arraycopy(back, 0, x, 0, x.length);
        }
    }

    private static float[] transposed(float[] x, int r, int c) {
        float[] out = new float[x.length];
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                out[j * r + i] = x[i * c + j];
            }
        }
        return out;
    }

    /** {@code x} with its singular values all brought near 1: scaled by its Frobenius norm, then iterated. */
    public static float[] orthogonalized(float[] x, int r, int c) {
        double sum = 0;
        for (float v : x) {
            sum += (double) v * v;
        }
        double norm = Math.max(Math.sqrt(sum), EPS);
        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = (float) (x[i] / norm);
        }
        for (int s = 0; s < NEWTON_STEPS; s++) {
            newtonStep(out, r, c);
        }
        return out;
    }

    private double norm() {
        double sum = 0;
        for (Tensor t : matrices) {
            if (t.hasGrad()) {
                for (float g : t.grad()) {
                    sum += (double) g * g;
                }
            }
        }
        double others = rest.gradNorm();
        return Math.sqrt(sum + others * others);
    }

    @Override
    public double clip(double maxNorm) {
        double norm = norm();
        if (maxNorm > 0 && norm > maxNorm) {
            float s = (float) (maxNorm / (norm + 1e-6));
            for (Tensor t : matrices) {
                if (t.hasGrad()) {
                    float[] g = t.grad();
                    for (int i = 0; i < g.length; i++) {
                        g[i] *= s;
                    }
                }
            }
            rest.scale(s);
        }
        return norm;
    }

    @Override
    public void step(float lr) {
        for (int m = 0; m < matrices.length; m++) {
            Tensor p = matrices[m];
            if (!p.hasGrad()) {
                continue;
            }
            float[] g = p.grad();
            float[] buf = momentum[m];
            float[] update = new float[g.length];
            for (int i = 0; i < g.length; i++) {
                buf[i] = MOMENTUM * buf[i] + (1 - MOMENTUM) * g[i];
                // Nesterov's: the gradient looks ahead along the momentum
                update[i] = (1 - MOMENTUM) * g[i] + MOMENTUM * buf[i];
            }
            int r = p.dim(0), c = p.dim(1);
            float[] step = orthogonalized(update, r, c);
            // an orthogonal matrix's entries have a root mean square of 1/√max(r, c), where AdamW's steps are about 1
            float size = lr * 0.2f * (float) Math.sqrt(Math.max(r, c));
            float decay = 1f - lr * WEIGHT_DECAY;
            for (int i = 0; i < g.length; i++) {
                p.data[i] = p.data[i] * decay - size * step[i];
            }
        }
        rest.step(lr);
    }

    @Override
    public void zeroGrad() {
        for (Tensor t : matrices) {
            t.zeroGrad();
        }
        rest.zeroGrad();
    }
}
