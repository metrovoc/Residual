package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;

/**
 * Superposition in a toy: more features than dimensions to keep them in, squeezed through a small matrix and read back out by the same
 * matrix. When features are common it keeps the most important and drops the rest. When they are rare it keeps more of them than it has
 * dimensions, each in a direction of its own that overlaps the others a little, since two rarely turn up at once.
 */
public final class Toy {
    /** How often a feature is absent, for the screen's choice. */
    public static final float[] SPARSITIES = {0f, 0.7f, 0.9f, 0.97f, 0.99f, 0.997f};
    /** A feature with at least this much of a direction is one the toy keeps. */
    public static final float KEPT = 0.5f;
    private static final int STEPS = 8000, BATCH = 512;

    /**
     * @param weights  [features][dimensions]
     * @param norms    how long each feature's direction is
     * @param overlap  how much of each kept feature's direction the others share
     * @param kept     how many features it keeps
     */
    public record Reading(int features, int dimensions, float sparsity, float[] weights, float[] norms, float[] overlap, int kept, float loss) {
        /** How many features a dimension holds, on average. */
        public float perDimension() {
            return (float) kept / dimensions;
        }

        public float dot(int i, int j) {
            float sum = 0;
            for (int k = 0; k < dimensions; k++) {
                sum += weights[i * dimensions + k] * weights[j * dimensions + k];
            }
            return sum;
        }
    }

    private Toy() {
    }

    /** Trains a toy of this shape from scratch: each feature counts 0.9 times as much as the one before. */
    public static Reading train(int features, int dimensions, float sparsity, long seed, BooleanSupplier stop) {
        Random rng = new Random(seed);
        Tensor w = Tensor.zeros(true, features, dimensions);
        for (int i = 0; i < w.data.length; i++) {
            w.data[i] = (float) (rng.nextGaussian() * Math.sqrt(2.0 / (features + dimensions)));
        }
        Tensor bias = Tensor.zeros(true, features);
        AdamW optimizer = new AdamW(List.of(w, bias));
        optimizer.weightDecay = 0;
        float[] importance = new float[BATCH * features];
        for (int b = 0; b < BATCH; b++) {
            for (int i = 0; i < features; i++) {
                importance[b * features + i] = (float) Math.pow(0.9, i);
            }
        }
        Tensor weightOf = Tensor.of(new int[]{BATCH, features}, importance);
        float last = 0;
        for (int step = 0; step < STEPS && !stop.getAsBoolean(); step++) {
            float[] x = new float[BATCH * features];
            for (int i = 0; i < x.length; i++) {
                x[i] = rng.nextFloat() < sparsity ? 0f : rng.nextFloat();
            }
            Tensor in = Tensor.of(new int[]{BATCH, features}, x);
            Tape tape = new Tape();
            Tensor hidden = Ops.matmul(tape, in, w, false);
            Tensor back = Ops.activate(tape, Ops.add(tape, Ops.matmul(tape, hidden, w, true), bias), Ops.Activation.RELU);
            Tensor diff = Ops.add(tape, back, Ops.scale(null, in, -1));
            Tensor loss = Ops.scale(tape, Ops.sum(tape, Ops.mul(tape, Ops.mul(tape, diff, diff), weightOf)), 1f / BATCH);
            optimizer.zeroGrad();
            tape.backward(loss);
            optimizer.step(3e-3f * (step < STEPS * 3 / 4 ? 1f : 0.2f));
            last = loss.item();
        }
        float[] norms = new float[features], overlap = new float[features];
        int kept = 0;
        for (int i = 0; i < features; i++) {
            double n = 0;
            for (int k = 0; k < dimensions; k++) {
                n += w.data[i * dimensions + k] * w.data[i * dimensions + k];
            }
            norms[i] = (float) Math.sqrt(n);
            kept += norms[i] >= KEPT ? 1 : 0;
        }
        Reading plain = new Reading(features, dimensions, sparsity, w.data.clone(), norms, overlap, kept, last);
        for (int i = 0; i < features; i++) {
            double shared = 0;
            for (int j = 0; j < features; j++) {
                if (j != i && norms[i] > 1e-3f) {
                    double along = plain.dot(i, j) / norms[i];
                    shared += along * along;
                }
            }
            overlap[i] = (float) shared;
        }
        return plain;
    }

    /** Two dimensions as arrows from the middle of a circle; more as how every feature's direction lies on every other's. */
    public static Picture picture(Reading r) {
        Picture.Builder b = new Picture.Builder();
        b.text(4, 3, Component.translatable("residual.scope.toy.title", Picture.percent(r.sparsity), Integer.toString(r.kept), Integer.toString(r.features),
                Integer.toString(r.dimensions), String.format(java.util.Locale.ROOT, "%.1f", r.perDimension())), Picture.DIM);
        int top = 16, size = Picture.H - top - 6;
        if (r.dimensions == 2) {
            int cx = 8 + size / 2, cy = top + size / 2, radius = size / 2;
            for (int a = 0; a < 64; a++) {
                double t0 = a * Math.PI / 32, t1 = (a + 1) * Math.PI / 32;
                b.line(cx + (int) Math.round(radius * Math.cos(t0)), cy + (int) Math.round(radius * Math.sin(t0)),
                        cx + (int) Math.round(radius * Math.cos(t1)), cy + (int) Math.round(radius * Math.sin(t1)), Picture.GRID);
            }
            float scale = radius / 1.25f;
            for (int i = 0; i < r.features; i++) {
                int x = cx + Math.round(r.weights[i * 2] * scale), y = cy + Math.round(r.weights[i * 2 + 1] * scale);
                int color = r.norms[i] >= KEPT ? Picture.heat(1f - i * 0.15f) : Picture.DIM;
                b.line(cx, cy, x, y, color);
                b.box(x - 1, y - 1, 3, 3, color, Component.translatable("residual.scope.toy.feature", Integer.toString(i + 1), Picture.two(r.norms[i]), Picture.two(r.overlap[i])));
            }
        } else {
            int cell = size / r.features;
            for (int i = 0; i < r.features; i++) {
                for (int j = 0; j < r.features; j++) {
                    float dot = r.dot(i, j);
                    b.box(8 + j * cell, top + i * cell, cell, cell, Picture.heat(dot), Component.literal(Picture.signed(dot)));
                }
            }
        }
        // each feature's length, and how much the others lie along it
        int x0 = 8 + size + 16, width = Picture.W - x0 - 8, rows = r.features, height = Math.max(1, size / rows);
        for (int i = 0; i < rows; i++) {
            int y = top + i * height;
            int filled = Math.round(width * Math.min(1f, r.norms[i]));
            int shared = Math.round(width * Math.min(1f, r.overlap[i]));
            Component tip = Component.translatable("residual.scope.toy.feature", Integer.toString(i + 1), Picture.two(r.norms[i]), Picture.two(r.overlap[i]));
            b.box(x0, y, width, height - 1, Picture.GRID, tip);
            b.box(x0, y, filled, Math.max(1, height - 1 - height / 3), r.norms[i] >= KEPT ? Picture.TEAL : Picture.DIM, tip);
            if (height >= 3 && shared > 0) {
                b.box(x0, y + height - 1 - height / 3, shared, height / 3, Picture.AMBER, tip);
            }
        }
        return b.build();
    }
}
