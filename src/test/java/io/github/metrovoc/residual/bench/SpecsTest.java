package io.github.metrovoc.residual.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Every salvaged design passes its spec, and the naive designs fail where real code fails. */
class SpecsTest {
    private static String failures(Spec spec, Design d) {
        StringBuilder sb = new StringBuilder();
        for (Spec.Result r : spec.check(d)) {
            if (!r.pass()) {
                sb.append(r.caseId()).append(": ").append(r.why()).append(' ');
            }
        }
        return sb.toString();
    }

    @Test
    void salvagedDesignsPass() {
        for (Spec spec : Specs.all()) {
            Design d = spec.salvagedDesign();
            assertTrue(d.fits(spec), spec.id() + " fits");
            assertEquals("", failures(spec, d), spec.id());
        }
    }

    /** A module used whole gives what its spec asks: every kernel passes the cases its machine's design is held to. */
    @Test
    void kernelsPassTheirOwnCases() {
        for (Spec spec : Specs.all()) {
            Design.Builder b = new Design.Builder(spec);
            int[] in = new int[spec.ports().size()];
            for (int i = 0; i < in.length; i++) {
                in[i] = i;
            }
            assertEquals("", failures(spec, b.output(b.module(spec.id(), in))), spec.id());
        }
    }

    @Test
    void naiveSoftmaxOverflows() {
        Design.Builder b = new Design.Builder(Specs.SOFTMAX);
        int e = b.prim(Prim.EXP, 0);
        Design naive = b.output(b.prim(Prim.DIV, e, b.prim(Prim.SUM, e)));
        List<Spec.Result> results = Specs.SOFTMAX.check(naive);
        assertTrue(results.get(0).pass(), "ordinary");
        assertEquals("residual.bench.why.nan", results.get(1).why(), "large");
        assertEquals("residual.bench.why.nan", results.get(2).why(), "negative");
    }

    @Test
    void meanByAConstantFailsLongerRows() {
        Design.Builder b = new Design.Builder(Specs.MEAN);
        Design four = b.output(b.prim(Prim.DIV, b.prim(Prim.SUM, 0), b.constant(4)));
        List<Spec.Result> results = Specs.MEAN.check(four);
        assertTrue(results.get(0).pass());
        assertFalse(results.get(1).pass());
    }

    @Test
    void layerNormNeedsEpsilonAndTwoPasses() {
        Design.Builder b = new Design.Builder(Specs.LAYER_NORM);
        int centered = b.prim(Prim.SUB, 0, b.module("mean", 0));
        int std = b.prim(Prim.SQRT, b.module("mean", b.prim(Prim.MUL, centered, centered)));
        Design noEps = b.output(b.prim(Prim.ADD, b.prim(Prim.MUL, b.prim(Prim.DIV, centered, std), 1), 2));
        List<Spec.Result> r = Specs.LAYER_NORM.check(noEps);
        assertTrue(r.get(0).pass());
        assertEquals("residual.bench.why.nan", r.get(1).why(), "flat row without epsilon");

        Design.Builder c = new Design.Builder(Specs.LAYER_NORM);
        int mean = c.module("mean", 0);
        int meanSq = c.module("mean", c.prim(Prim.MUL, 0, 0));
        int var = c.prim(Prim.SUB, meanSq, c.prim(Prim.MUL, mean, mean));
        int sd = c.prim(Prim.SQRT, c.prim(Prim.ADD, var, c.constant(Specs.EPS)));
        Design onePass = c.output(c.prim(Prim.ADD, c.prim(Prim.MUL, c.prim(Prim.DIV, c.prim(Prim.SUB, 0, mean), sd), 1), 2));
        List<Spec.Result> s = Specs.LAYER_NORM.check(onePass);
        assertTrue(s.get(0).pass(), "one pass is fine near zero");
        assertFalse(s.get(2).pass(), "one pass cancels catastrophically far from zero");
    }

    @Test
    void rmsNormNeedsItsEpsilonUnderTheRootAndNoCentring() {
        Design.Builder a = new Design.Builder(Specs.RMS_NORM);
        Design noEps = a.output(a.prim(Prim.MUL, a.prim(Prim.DIV, 0, a.prim(Prim.SQRT, a.module("mean", a.prim(Prim.MUL, 0, 0)))), 1));
        List<Spec.Result> r = Specs.RMS_NORM.check(noEps);
        assertTrue(r.get(0).pass());
        assertEquals("residual.bench.why.nan", r.get(2).why(), "a silent row without epsilon");

        Design.Builder b = new Design.Builder(Specs.RMS_NORM);
        int root = b.prim(Prim.ADD, b.prim(Prim.SQRT, b.module("mean", b.prim(Prim.MUL, 0, 0))), b.constant(Specs.EPS));
        List<Spec.Result> s = Specs.RMS_NORM.check(b.output(b.prim(Prim.MUL, b.prim(Prim.DIV, 0, root), 1)));
        assertTrue(s.get(2).pass(), "epsilon outside the root survives a silent row");
        assertFalse(s.get(3).pass(), "but not a quiet one");

        Design.Builder c = new Design.Builder(Specs.RMS_NORM);
        int centred = c.prim(Prim.SUB, 0, c.module("mean", 0));
        int rms = c.prim(Prim.SQRT, c.prim(Prim.ADD, c.module("mean", c.prim(Prim.MUL, centred, centred)), c.constant(Specs.EPS)));
        List<Spec.Result> t = Specs.RMS_NORM.check(c.output(c.prim(Prim.MUL, c.prim(Prim.DIV, centred, rms), 1)));
        assertFalse(t.get(1).pass(), "centring is layer norm's, and shows far from zero");
    }

    @Test
    void naiveSiluOverflows() {
        Design.Builder b = new Design.Builder(Specs.SILU);
        int e = b.prim(Prim.EXP, 0);
        Design naive = b.output(b.prim(Prim.DIV, b.prim(Prim.MUL, 0, e), b.prim(Prim.ADD, b.constant(1f), e)));
        List<Spec.Result> r = Specs.SILU.check(naive);
        assertTrue(r.get(0).pass());
        assertEquals("residual.bench.why.nan", r.get(1).why(), "large");
    }

    @Test
    void rotaryAsksForEveryPairsOwnTurnAtOnce() {
        // a width typed in works at one width only
        Design.Builder b = new Design.Builder(Specs.ROTARY);
        int rate = b.prim(Prim.EXP, b.prim(Prim.MUL, b.constant((float) -Math.log(10000)),
                b.prim(Prim.DIV, b.prim(Prim.MUL, b.constant(2f), b.prim(Prim.PAIR, 0)), b.constant(4f))));
        int angle = b.prim(Prim.MUL, b.prim(Prim.INDEX, 0), rate);
        Design typed = b.output(b.prim(Prim.ADD, b.prim(Prim.MUL, 0, b.prim(Prim.COS, angle)),
                b.prim(Prim.MUL, b.prim(Prim.TURN, 0), b.prim(Prim.SIN, angle))));
        List<Spec.Result> r = Specs.ROTARY.check(typed);
        assertTrue(r.get(0).pass(), "ordinary, four wide");
        assertFalse(r.get(1).pass(), "wide");

        // adding the place to the numbers, as a position table would, turns nothing
        Design.Builder c = new Design.Builder(Specs.ROTARY);
        assertFalse(Specs.ROTARY.check(c.output(c.prim(Prim.ADD, 0, c.prim(Prim.INDEX, 0)))).get(0).pass());
    }

    @Test
    void naiveZLossOverflows() {
        Design.Builder b = new Design.Builder(Specs.Z_LOSS);
        int z = b.prim(Prim.LOG, b.prim(Prim.SUM, b.prim(Prim.EXP, 0)));
        List<Spec.Result> r = Specs.Z_LOSS.check(b.output(b.prim(Prim.MUL, z, z)));
        assertTrue(r.get(0).pass());
        assertFalse(r.get(1).pass(), "large");
        assertFalse(r.get(2).pass(), "negative");
    }

    @Test
    void anOnlineSoftmaxHasToSurviveATileWithNothingToSee() {
        Design.Builder b = new Design.Builder(Specs.ONLINE_SOFTMAX);
        int v1 = b.prim(Prim.TRANSPOSE, b.prim(Prim.LEFT, b.prim(Prim.TRANSPOSE, 1)));
        int v2 = b.prim(Prim.TRANSPOSE, b.prim(Prim.RIGHT, b.prim(Prim.TRANSPOSE, 1)));
        int s1 = b.prim(Prim.LEFT, 0);
        int s2 = b.prim(Prim.RIGHT, 0);
        int m1 = b.prim(Prim.AMAX, s1);
        int e1 = b.prim(Prim.EXP, b.prim(Prim.SUB, s1, m1));
        int m = b.prim(Prim.MAX, m1, b.prim(Prim.AMAX, s2));
        int fade = b.prim(Prim.EXP, b.prim(Prim.SUB, m1, m));
        int e2 = b.prim(Prim.EXP, b.prim(Prim.SUB, s2, m));
        int sum = b.prim(Prim.ADD, b.prim(Prim.MUL, b.prim(Prim.SUM, e1), fade), b.prim(Prim.SUM, e2));
        int out = b.prim(Prim.ADD, b.prim(Prim.MUL, b.module("matmul", e1, v1), fade), b.module("matmul", e2, v2));
        List<Spec.Result> r = Specs.ONLINE_SOFTMAX.check(b.output(b.prim(Prim.DIV, out, sum)));
        assertTrue(r.get(0).pass());
        assertTrue(r.get(1).pass(), "rising");
        assertTrue(r.get(2).pass(), "falling");
        assertEquals("residual.bench.why.nan", r.get(3).why(), "masked: −∞ − −∞ is not a number");
    }

    @Test
    void naiveAttentionOverflowsOnSharpScores() {
        Design.Builder b = new Design.Builder(Specs.ATTENTION);
        int q = b.module("matmul", 0, 1);
        int k = b.module("matmul", 0, 2);
        int v = b.module("matmul", 0, 3);
        int scores = b.prim(Prim.DIV, b.module("matmul", q, b.prim(Prim.TRANSPOSE, k)), b.prim(Prim.SQRT, b.prim(Prim.SIZE, q)));
        int e = b.prim(Prim.EXP, b.prim(Prim.CAUSAL, scores));
        int weights = b.prim(Prim.DIV, e, b.prim(Prim.SUM, e));
        Design naive = b.output(b.module("matmul", b.module("matmul", weights, v), 4));
        List<Spec.Result> r = Specs.ATTENTION.check(naive);
        assertTrue(r.get(0).pass());
        assertFalse(r.get(2).pass(), "sharp");
    }
}
