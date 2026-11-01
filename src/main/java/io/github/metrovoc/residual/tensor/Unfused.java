package io.github.metrovoc.residual.tensor;

import io.github.metrovoc.residual.tensor.Ops.Activation;
import io.github.metrovoc.residual.tensor.Prims.Binary;
import io.github.metrovoc.residual.tensor.Prims.Unary;

/**
 * The fused operations of {@link Ops} computed the way a salvaged design is written: one primitive at a time, each
 * pass writing a whole intermediate tensor and recording its own backward step. Same values, more memory traffic,
 * which is why a fused kernel is faster.
 */
public final class Unfused {
    private Unfused() {
    }

    private static Tensor bin(Tape tape, Binary op, Tensor a, Tensor b) {
        return Prims.binary(tape, op, a, b);
    }

    /**
     * The salvaged matrix multiply: every product a[i][k]·b[k][j] laid out at once, then summed over k. It holds
     * n·m·k numbers where the fused kernel holds n·m.
     */
    public static Tensor matmul(Tape tape, Tensor a, Tensor b, boolean transB) {
        Tensor rows = Prims.unsqueeze(tape, a);
        Tensor cols = transB ? b : Prims.transpose(tape, b);
        if (cols.rank() > 2) {
            int[] s = new int[cols.rank() + 1];
            System.arraycopy(cols.shape, 0, s, 0, cols.rank() - 2);
            s[s.length - 3] = 1;
            s[s.length - 2] = cols.dim(-2);
            s[s.length - 1] = cols.dim(-1);
            cols = Prims.view(tape, cols, s);
        }
        return Prims.squeeze(tape, Prims.sum(tape, bin(tape, Binary.MUL, rows, cols)));
    }

    /** exp(x − max) / sum(exp(x − max)) along the last axis. */
    public static Tensor softmax(Tape tape, Tensor x) {
        Tensor shifted = bin(tape, Binary.SUB, x, Prims.amax(tape, x));
        Tensor e = Prims.unary(tape, Unary.EXP, shifted);
        return bin(tape, Binary.DIV, e, Prims.sum(tape, e));
    }

    private static Tensor mean(Tape tape, Tensor x) {
        return bin(tape, Binary.DIV, Prims.sum(tape, x), Prims.size(x));
    }

    public static Tensor layerNorm(Tape tape, Tensor x, Tensor gamma, Tensor beta, float eps) {
        Tensor centered = bin(tape, Binary.SUB, x, mean(tape, x));
        Tensor variance = mean(tape, bin(tape, Binary.MUL, centered, centered));
        Tensor std = Prims.unary(tape, Unary.SQRT, bin(tape, Binary.ADD, variance, Prims.scalar(eps)));
        return bin(tape, Binary.ADD, bin(tape, Binary.MUL, bin(tape, Binary.DIV, centered, std), gamma), beta);
    }

    public static Tensor rmsNorm(Tape tape, Tensor x, Tensor gamma, float eps) {
        Tensor ms = mean(tape, bin(tape, Binary.MUL, x, x));
        Tensor rms = Prims.unary(tape, Unary.SQRT, bin(tape, Binary.ADD, ms, Prims.scalar(eps)));
        return bin(tape, Binary.MUL, bin(tape, Binary.DIV, x, rms), gamma);
    }

    /** A pair at row t turned by t / 10000^(2k/d): x·cos θ + turn(x)·sin θ, each part a tensor of its own. */
    public static Tensor rotary(Tape tape, Tensor x) {
        Tensor rate = Prims.unary(tape, Unary.EXP, bin(tape, Binary.MUL, Prims.scalar((float) -Math.log(10000)),
                bin(tape, Binary.DIV, bin(tape, Binary.MUL, Prims.scalar(2f), Prims.pair(x)), Prims.size(x))));
        Tensor angle = bin(tape, Binary.MUL, Prims.index(x), rate);
        return bin(tape, Binary.ADD, bin(tape, Binary.MUL, x, Prims.unary(tape, Unary.COS, angle)),
                bin(tape, Binary.MUL, Prims.turn(tape, x), Prims.unary(tape, Unary.SIN, angle)));
    }

    /** The grid of the Bench's fp32 spec, each part a tensor of its own: exponent, spacing, rounding, then the top. */
    public static Tensor grid(Tape tape, Tensor x, Tensor mantissa, Tensor lowest, Tensor top) {
        Tensor e = bin(tape, Binary.MAX, Prims.unary(tape, Unary.EXPONENT, x), lowest);
        Tensor q = Prims.unary(tape, Unary.POW2, bin(tape, Binary.SUB, e, mantissa));
        Tensor y = bin(tape, Binary.MUL, Prims.unary(tape, Unary.ROUND, bin(tape, Binary.DIV, x, q)), q);
        Tensor under = Prims.unary(tape, Unary.NEG, top);
        return bin(tape, Binary.MAX, under, Prims.unary(tape, Unary.NEG, bin(tape, Binary.MAX, under, Prims.unary(tape, Unary.NEG, y))));
    }

    /** fp4 with each row a block, as the Bench's spec has it. */
    public static Tensor fp4Rows(Tape tape, Tensor x) {
        Tensor size = bin(tape, Binary.MAX, x, Prims.unary(tape, Unary.NEG, x));
        Tensor rows = Prims.amax(tape, size);
        Tensor most = bin(tape, Binary.MAX, Prims.amax(tape, Prims.transpose(tape, rows)), Prims.scalar(1e-30f));
        Tensor whole = bin(tape, Binary.DIV, most, Prims.scalar(2688f));
        Tensor kept = grid(tape, bin(tape, Binary.DIV, bin(tape, Binary.DIV, rows, Prims.scalar(6f)), whole), Prims.scalar(3f), Prims.scalar(-6f), Prims.scalar(448f));
        Tensor s = bin(tape, Binary.MUL, bin(tape, Binary.MAX, kept, Prims.scalar(1e-30f)), whole);
        return bin(tape, Binary.MUL, grid(tape, bin(tape, Binary.DIV, x, s), Prims.scalar(1f), Prims.scalar(0f), Prims.scalar(6f)), s);
    }

    private static Tensor sigmoid(Tape tape, Tensor x) {
        Tensor one = Prims.scalar(1f);
        return bin(tape, Binary.DIV, one, bin(tape, Binary.ADD, one, Prims.unary(tape, Unary.EXP, Prims.unary(tape, Unary.NEG, x))));
    }

    public static Tensor activate(Tape tape, Tensor x, Activation f) {
        return switch (f) {
            case RELU -> bin(tape, Binary.MAX, x, Prims.scalar(0f));
            case TANH -> Prims.unary(tape, Unary.TANH, x);
            case SIGMOID -> sigmoid(tape, x);
            case SILU -> bin(tape, Binary.MUL, x, sigmoid(tape, x));
            case GELU -> {
                Tensor cube = bin(tape, Binary.MUL, bin(tape, Binary.MUL, x, x), x);
                Tensor inner = bin(tape, Binary.MUL, Prims.scalar((float) Math.sqrt(2.0 / Math.PI)),
                        bin(tape, Binary.ADD, x, bin(tape, Binary.MUL, Prims.scalar(0.044715f), cube)));
                Tensor gate = bin(tape, Binary.ADD, Prims.scalar(1f), Prims.unary(tape, Unary.TANH, inner));
                yield bin(tape, Binary.MUL, bin(tape, Binary.MUL, Prims.scalar(0.5f), x), gate);
            }
        };
    }
}
