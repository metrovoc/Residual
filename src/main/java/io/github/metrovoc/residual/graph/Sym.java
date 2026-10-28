package io.github.metrovoc.residual.graph;

import java.util.Arrays;

/**
 * A symbolic value shape used while checking a graph. Batch and time are kept symbolic so a
 * graph checks once and runs at any batch size or sequence length.
 */
public final class Sym {
    public static final int B = -1;
    public static final int T = -2;

    public final int[] shape;
    public final boolean tokens;

    private Sym(int[] shape, boolean tokens) {
        this.shape = shape;
        this.tokens = tokens;
    }

    public static Sym tokens() {
        return new Sym(new int[]{B, T}, true);
    }

    public static Sym of(int... shape) {
        return new Sym(shape, false);
    }

    public int rank() {
        return shape.length;
    }

    public int dim(int i) {
        return shape[i < 0 ? shape.length + i : i];
    }

    public Sym withLast(int d) {
        int[] s = shape.clone();
        s[s.length - 1] = d;
        return new Sym(s, false);
    }

    public boolean sameAs(Sym o) {
        return tokens == o.tokens && Arrays.equals(shape, o.shape);
    }

    /** Concrete shape for a batch of {@code batch} sequences of length {@code time}. */
    public int[] concrete(int batch, int time) {
        int[] s = shape.clone();
        for (int i = 0; i < s.length; i++) {
            if (s[i] == B) {
                s[i] = batch;
            } else if (s[i] == T) {
                s[i] = time;
            }
        }
        return s;
    }

    public static String dimName(int d) {
        return d == B ? "B" : d == T ? "T" : Integer.toString(d);
    }

    @Override
    public String toString() {
        if (tokens) {
            return "tokens[B×T]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) {
                sb.append('×');
            }
            sb.append(dimName(shape[i]));
        }
        return sb.append(']').toString();
    }
}
