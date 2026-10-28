package io.github.metrovoc.residual.tensor;

import java.util.List;

/** {@code torch.optim.AdamW}, with optional global-norm gradient clipping. */
public final class AdamW {
    public static final class Group {
        final Tensor param;
        final boolean decay;
        final float[] m;
        final float[] v;

        Group(Tensor param, boolean decay) {
            this.param = param;
            this.decay = decay;
            this.m = new float[param.size()];
            this.v = new float[param.size()];
        }
    }

    private final Group[] groups;
    public float beta1 = 0.9f;
    public float beta2 = 0.999f;
    public float eps = 1e-8f;
    public float weightDecay = 0.01f;
    private int step;

    /** Matrices (rank >= 2) get weight decay; gains and biases don't. */
    public AdamW(List<Tensor> params) {
        groups = new Group[params.size()];
        for (int i = 0; i < groups.length; i++) {
            Tensor p = params.get(i);
            groups[i] = new Group(p, p.rank() >= 2);
        }
    }

    public int stepCount() {
        return step;
    }

    /** Global L2 norm of all gradients. */
    public double gradNorm() {
        double sum = 0;
        for (Group g : groups) {
            if (!g.param.hasGrad()) {
                continue;
            }
            for (float x : g.param.grad()) {
                sum += (double) x * x;
            }
        }
        return Math.sqrt(sum);
    }

    /** Scales gradients so their global norm is at most {@code maxNorm}; returns the norm before clipping. */
    public double clip(double maxNorm) {
        double norm = gradNorm();
        if (maxNorm > 0 && norm > maxNorm) {
            float s = (float) (maxNorm / (norm + 1e-6));
            for (Group g : groups) {
                if (!g.param.hasGrad()) {
                    continue;
                }
                float[] grad = g.param.grad();
                for (int i = 0; i < grad.length; i++) {
                    grad[i] *= s;
                }
            }
        }
        return norm;
    }

    public void step(float lr) {
        step++;
        double bc1 = 1 - Math.pow(beta1, step);
        double bc2 = 1 - Math.pow(beta2, step);
        float stepSize = (float) (lr / bc1);
        float bc2Sqrt = (float) Math.sqrt(bc2);
        for (Group g : groups) {
            if (!g.param.hasGrad()) {
                continue;
            }
            float[] p = g.param.data;
            float[] grad = g.param.grad();
            float decay = g.decay ? 1f - lr * weightDecay : 1f;
            for (int i = 0; i < p.length; i++) {
                float gi = grad[i];
                g.m[i] = beta1 * g.m[i] + (1 - beta1) * gi;
                g.v[i] = beta2 * g.v[i] + (1 - beta2) * gi * gi;
                float denom = (float) Math.sqrt(g.v[i]) / bc2Sqrt + eps;
                p[i] = p[i] * decay - stepSize * g.m[i] / denom;
            }
        }
    }

    public void zeroGrad() {
        for (Group g : groups) {
            g.param.zeroGrad();
        }
    }
}
