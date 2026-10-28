package io.github.metrovoc.residual.graph;

import io.github.metrovoc.residual.tensor.Tensor;
import java.util.Random;

/** A learnable tensor an operator owns, with its shape and how to initialise it. */
public record ParamSpec(String name, int[] shape, Init init) {
    public enum Kind { NORMAL, FAN_IN_UNIFORM, ONES, ZEROS }

    public record Init(Kind kind, float scale) {
        public static Init normal(float std) {
            return new Init(Kind.NORMAL, std);
        }

        /** PyTorch's default for nn.Linear weights: U(-1/sqrt(fan_in), 1/sqrt(fan_in)). */
        public static Init fanIn() {
            return new Init(Kind.FAN_IN_UNIFORM, 1f);
        }

        public static Init ones() {
            return new Init(Kind.ONES, 1f);
        }

        public static Init zeros() {
            return new Init(Kind.ZEROS, 0f);
        }
    }

    public Tensor create(Random rng) {
        Tensor t = Tensor.zeros(true, shape);
        float[] d = t.data;
        switch (init.kind()) {
            case NORMAL -> {
                for (int i = 0; i < d.length; i++) {
                    d[i] = (float) rng.nextGaussian() * init.scale();
                }
            }
            case FAN_IN_UNIFORM -> {
                float bound = (float) (init.scale() / Math.sqrt(shape[0]));
                for (int i = 0; i < d.length; i++) {
                    d[i] = (rng.nextFloat() * 2f - 1f) * bound;
                }
            }
            case ONES -> java.util.Arrays.fill(d, 1f);
            case ZEROS -> {
            }
        }
        return t;
    }
}
