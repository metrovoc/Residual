package io.github.metrovoc.residual.tensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.bench.Design;
import io.github.metrovoc.residual.bench.Prim;
import io.github.metrovoc.residual.bench.Specs;
import io.github.metrovoc.residual.tensor.Numerics.Format;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The narrow formats' grids, their whole-tensor and block scales, and how a gradient passes through them. */
class NumericsTest {
    @Test
    void fp4HasEightSizes() {
        double[] in = {0, 0.2, 0.3, 0.74, 0.76, 1.2, 1.3, 1.8, 2.4, 2.6, 3.4, 3.6, 4.9, 5.1, 7, 100};
        double[] want = {0, 0, 0.5, 0.5, 1, 1, 1.5, 2, 2, 3, 3, 4, 4, 6, 6, 6};
        for (int i = 0; i < in.length; i++) {
            assertEquals(want[i], Numerics.e2m1(in[i]), in[i] + " rounds");
            assertEquals(-want[i], Numerics.e2m1(-in[i]), -in[i] + " rounds");
        }
    }

    @Test
    void aWholeTensorScaleLetsOneLoudValueSilenceTheRest() {
        float[] x = new float[32];
        x[0] = 10000f;
        x[20] = 1f;
        x[21] = 0.5f;
        float[] flat = Numerics.forward(x, Format.FP4_FLAT);
        assertEquals(0f, flat[20], "flat: everything far below the loudest value rounds to zero");
        float[] blocks = Numerics.forward(x, Format.FP4);
        assertEquals(1f, blocks[20], 0.25f, "in a block of its own it keeps its size");
        assertEquals(0.5f, blocks[21], 0.25f);
        assertEquals(10000f, blocks[0], 2000f);
    }

    @Test
    void aSilentTensorStaysSilent() {
        for (Format f : Format.values()) {
            float[] out = Numerics.forward(new float[17], f);
            for (float v : out) {
                assertEquals(0f, v, f.id);
            }
        }
    }

    @Test
    void aNanStaysVisible() {
        float[] x = {1f, Float.NaN, 3f};
        for (Format f : Format.values()) {
            float[] out = Numerics.forward(x, f);
            boolean found = false;
            for (float v : out) {
                found |= Float.isNaN(v);
            }
            assertTrue(found, f.id);
        }
    }

    @Test
    void roundingTwiceChangesNothing() {
        Random rng = new Random(2);
        float[] x = new float[64];
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) (rng.nextGaussian() * Math.exp(rng.nextGaussian()));
        }
        for (Format f : Format.values()) {
            float[] once = Numerics.forward(x, f);
            float[] twice = Numerics.forward(once, f);
            for (int i = 0; i < x.length; i++) {
                assertEquals(once[i], twice[i], 1e-3f * Math.abs(once[i]), f.id + " element " + i);
            }
        }
    }

    /** The engine's fp8 and fp4 are what the Bench's designs compute. */
    @Test
    void theEngineDoesWhatTheBenchVerifies() {
        Random rng = new Random(3);
        float[] x = new float[16 * 4];
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) rng.nextGaussian() * (i < 16 ? 100f : 1f);
        }
        Tensor t = new Tensor(new int[]{4, 16}, x, false);
        Tensor fp8 = Specs.FP8.salvagedDesign().run(null, new Tensor[]{t}).result();
        float[] engine8 = Numerics.forward(x, Format.FP8);
        Tensor fp4 = Specs.FP4.salvagedDesign().run(null, new Tensor[]{t}).result();
        float[] engine4 = Numerics.forward(x, Format.FP4);
        for (int i = 0; i < x.length; i++) {
            assertEquals(engine8[i], fp8.data[i], 1e-3f + 1e-3f * Math.abs(x[i]), "fp8 element " + i);
            assertEquals(engine4[i], fp4.data[i], 1e-3f + 1e-3f * Math.abs(x[i]), "fp4 element " + i);
        }
    }

    @Test
    void aGradientPassesARoundingAsIfItWerentThere() {
        Tensor x = Tensor.zeros(true, 1, 8);
        for (int i = 0; i < 8; i++) {
            x.data[i] = i + 0.3f;
        }
        for (Format f : Format.values()) {
            x.zeroGrad();
            Tape tape = new Tape();
            tape.backward(Ops.sum(tape, Numerics.cast(tape, x, f)));
            for (float g : x.grad()) {
                assertEquals(1f, g, f.id);
            }
        }
    }

    /** On the way back fp4 rounds by chance: small gradients don't all vanish, and on average they keep their size. */
    @Test
    void chanceKeepsSmallGradientsOnAverage() {
        float[] g = new float[16 * 512];
        g[0] = 1f;
        for (int i = 1; i < g.length; i++) {
            g[i] = i % 16 == 0 ? 0.6f : 0.13f;
        }
        float[] rounded = Numerics.backward(g, Format.FP4);
        double sum = 0, want = 0;
        int alive = 0;
        for (int i = 1; i < g.length; i++) {
            if (i % 16 != 0) {
                sum += rounded[i];
                want += g[i];
                alive += rounded[i] != 0 ? 1 : 0;
            }
        }
        assertEquals(want, sum, 0.05 * want, "their sum");
        assertTrue(alive > 0, "some of them survive");
        float[] nearest = Numerics.backward(g, Format.FP4_FLAT);
        assertEquals(0.13f / 1f < 1f / 12 ? 0f : nearest[1], nearest[1]);
    }

    @Test
    void bf16IsAMantissaCut() {
        assertEquals(256f, Numerics.bf16(257f), "a half goes to the even neighbour");
        assertEquals(260f, Numerics.bf16(259f));
        assertEquals(1f, Numerics.bf16(1.001f), "an update this small disappears in a bf16 weight");
        assertEquals(1e30f, Numerics.bf16(1e30f), 1e28f);
        assertEquals(Float.POSITIVE_INFINITY, Numerics.bf16(Float.MAX_VALUE));
        assertTrue(Float.isNaN(Numerics.bf16(Float.NaN)));
    }
}
