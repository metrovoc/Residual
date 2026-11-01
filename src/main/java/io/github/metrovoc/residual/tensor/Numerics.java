package io.github.metrovoc.residual.tensor;

import java.util.Arrays;

/**
 * Narrow floats, for real. A format is a grid of numbers: a sign, a few bits of exponent, a few of mantissa. A value is
 * rounded to the grid's nearest point, a half to the even neighbour, and past the grid's top it stays at the top.
 * The narrowest formats have so small a range that a whole tensor, or each block of it, is scaled to fit first.
 *
 * <p>Training in a format follows what real low-precision training does: weights stay in fp32 and are rounded on the way
 * into each matrix multiply, with activations; the product comes out in bf16, and on the way back the gradient reaching
 * it is rounded too. A rounding has no useful slope, so the gradient passes it as if it weren't there.
 */
public final class Numerics {
    /** Values a block of fp4 shares one scale over. */
    public static final int BLOCK = 16;
    private static final double[] POW2 = new double[1000];
    private static final double[] INVERSE = new double[1000];
    private static final int ZERO = 500;

    static {
        for (int i = 0; i < POW2.length; i++) {
            POW2[i] = Math.scalb(1.0, i - ZERO);
            INVERSE[i] = Math.scalb(1.0, ZERO - i);
        }
    }

    private Numerics() {
    }

    public enum Format {
        FP32("fp32", 4f),
        BF16("bf16", 2f),
        FP8("fp8", 1f),
        /** fp4 the plain way: one scale for the whole tensor, and gradients rounded to the nearest point. */
        FP4_FLAT("fp4_flat", 0.5f),
        /** fp4 as it is made to work: blocks of 16 with a scale of their own kept in 8 bits, gradients rounded by chance. */
        FP4("fp4", 0.5625f);

        public final String id;
        /** What a weight takes, in bytes, with what its scale takes. */
        public final float bytes;

        Format(String id, float bytes) {
            this.id = id;
            this.bytes = bytes;
        }

        public boolean narrow() {
            return this != FP32;
        }

        public static Format byId(String id) {
            for (Format f : values()) {
                if (f.id.equals(id)) {
                    return f;
                }
            }
            return FP32;
        }
    }

    // ---------------------------------------------------------------- one number

    /**
     * The point nearest {@code x} of a grid with {@code mantissa} bits of mantissa, whose smallest exponent is
     * {@code lowest} and whose largest value is {@code top}: below 2^lowest the grid's points are evenly spaced.
     */
    public static double grid(double x, int mantissa, int lowest, double top) {
        double a = Math.abs(x);
        if (!(a < top)) {
            return Double.isNaN(x) ? x : Math.copySign(top, x);
        }
        int at = Math.max(Math.getExponent(a), lowest) - mantissa + ZERO;
        return Math.copySign(Math.min(Math.rint(a * INVERSE[at]) * POW2[at], top), x);
    }

    /** As {@link #grid}, but up or down by chance in proportion to how near each neighbour is, so that small values don't all vanish. */
    private static double stochastic(double x, int mantissa, int lowest, double top, double chance) {
        double a = Math.abs(x);
        if (!(a < top)) {
            return Double.isNaN(x) ? x : Math.copySign(top, x);
        }
        int at = Math.max(Math.getExponent(a), lowest) - mantissa + ZERO;
        double steps = a * INVERSE[at];
        double floor = Math.floor(steps);
        return Math.copySign(Math.min((chance < steps - floor ? floor + 1 : floor) * POW2[at], top), x);
    }

    /** bf16: a float32 with its mantissa cut to 7 bits. Its range is float32's; rounding past the top gives infinity. */
    public static float bf16(float x) {
        if (Float.isNaN(x)) {
            return x;
        }
        int bits = Float.floatToRawIntBits(x);
        return Float.intBitsToFloat((bits + 0x7FFF + (bits >> 16 & 1)) & 0xFFFF0000);
    }

    /** fp8's forward format: 4 bits of exponent, 3 of mantissa, up to 448. */
    public static double e4m3(double x) {
        return grid(x, 3, -6, 448);
    }

    /** fp8's backward format: 5 bits of exponent, 2 of mantissa, up to 57,344. */
    public static double e5m2(double x) {
        return grid(x, 2, -14, 57344);
    }

    /** fp4: 2 bits of exponent, 1 of mantissa: 0, 0.5, 1, 1.5, 2, 3, 4 and 6. */
    public static double e2m1(double x) {
        return grid(x, 1, 0, 6);
    }

    // ---------------------------------------------------------------- a whole tensor

    private static double largest(float[] x) {
        double top = 0;
        for (float v : x) {
            // a NaN wins, so a run gone wrong stays visibly wrong
            double a = Math.abs((double) v);
            top = a > top || a != a ? a : top;
        }
        return top;
    }

    /** The largest size in a whole tensor mapped to the top of a grid: what fp8 does. */
    private static void scaled(float[] x, float[] out, int mantissa, int lowest, double top) {
        double most = largest(x);
        if (!(most > 0) || Double.isInfinite(most)) {
            // nothing to scale: all zero stays zero, and what is already infinite or NaN stays so
            System.arraycopy(x, 0, out, 0, x.length);
            return;
        }
        double s = most / top;
        Parallel.range(x.length, 8, (from, to) -> {
            for (int i = from; i < to; i++) {
                out[i] = (float) (grid(x[i] / s, mantissa, lowest, top) * s);
            }
        });
    }

    private static void bf16(float[] x, float[] out) {
        Parallel.range(x.length, 2, (from, to) -> {
            for (int i = from; i < to; i++) {
                out[i] = bf16(x[i]);
            }
        });
    }

    /**
     * fp4 in blocks: each {@link #BLOCK} values share a scale, kept in E4M3, so one loud value hurts only its own block.
     * The tensor's one fp32 scale brings every block's scale into E4M3's range. With {@code jitter}, values round by
     * chance, which a hash of each value and its place stands in for, so a run repeats exactly.
     */
    private static void blocks(float[] x, float[] out, boolean jitter) {
        double most = largest(x);
        if (!(most > 0) || Double.isInfinite(most) || Double.isNaN(most)) {
            System.arraycopy(x, 0, out, 0, x.length);
            return;
        }
        double tensor = most / (6 * 448);
        int count = (x.length + BLOCK - 1) / BLOCK;
        Parallel.range(count, BLOCK * 8L, (from, to) -> {
            for (int b = from; b < to; b++) {
                int start = b * BLOCK, end = Math.min(start + BLOCK, x.length);
                double loudest = 0;
                for (int i = start; i < end; i++) {
                    loudest = Math.max(loudest, Math.abs((double) x[i]));
                }
                double s = e4m3(loudest / 6 / tensor) * tensor;
                if (s == 0) {
                    Arrays.fill(out, start, end, 0f);
                    continue;
                }
                for (int i = start; i < end; i++) {
                    out[i] = (float) ((jitter ? stochastic(x[i] / s, 1, 0, 6, chance(x[i], i)) : e2m1(x[i] / s)) * s);
                }
            }
        });
    }

    private static double chance(float value, int place) {
        int h = (Float.floatToRawIntBits(value) ^ place * 0x9E3779B9) * 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h >>> 1) / 2147483648.0;
    }

    /** What weights and activations become on the way into a matrix multiply. */
    public static float[] forward(float[] x, Format f) {
        float[] out = new float[x.length];
        switch (f) {
            case FP32 -> System.arraycopy(x, 0, out, 0, x.length);
            case BF16 -> bf16(x, out);
            case FP8 -> scaled(x, out, 3, -6, 448);
            case FP4_FLAT -> scaled(x, out, 1, 0, 6);
            case FP4 -> blocks(x, out, false);
        }
        return out;
    }

    /** What a gradient becomes on its way back through a matrix multiply. */
    public static float[] backward(float[] g, Format f) {
        float[] out = new float[g.length];
        switch (f) {
            case FP32 -> System.arraycopy(g, 0, out, 0, g.length);
            case BF16 -> bf16(g, out);
            case FP8 -> scaled(g, out, 2, -14, 57344);
            case FP4_FLAT -> scaled(g, out, 1, 0, 6);
            case FP4 -> blocks(g, out, true);
        }
        return out;
    }

    /** {@code x} rounded to {@code f}; the gradient passes straight through. */
    public static Tensor cast(Tape tape, Tensor x, Format f) {
        if (f == Format.FP32) {
            return x;
        }
        boolean tracks = tape != null && x.requiresGrad;
        Tensor out = new Tensor(x.shape.clone(), forward(x.data, f), tracks);
        if (tracks) {
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

    /** A matrix multiply's product: kept in bf16 going forward, and on the way back its gradient is rounded to {@code f}. */
    public static Tensor product(Tape tape, Tensor y, Format f) {
        if (f == Format.FP32) {
            return y;
        }
        boolean tracks = tape != null && y.requiresGrad;
        Tensor out = new Tensor(y.shape.clone(), forward(y.data, Format.BF16), tracks);
        if (tracks) {
            tape.record(() -> {
                if (!out.hasGrad()) {
                    return;
                }
                float[] g = backward(out.grad(), f);
                float[] gy = y.grad();
                for (int i = 0; i < g.length; i++) {
                    gy[i] += g[i];
                }
            });
        }
        return out;
    }
}
