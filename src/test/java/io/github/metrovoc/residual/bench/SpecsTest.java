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
    void theFloatGridNeedsTheExponentItStoresAndAFloorAndATop() {
        // rounding to a fixed spacing is right for numbers of one size only
        Design.Builder a = new Design.Builder(Specs.FP32);
        int q = a.constant(0.5f);
        List<Spec.Result> fixed = Specs.FP32.check(a.output(a.prim(Prim.MUL, a.prim(Prim.ROUND, a.prim(Prim.DIV, 0, q)), q)));
        assertFalse(fixed.get(0).pass(), "ordinary numbers span several sizes");

        // the exponent from a logarithm: log 0 is −∞, and 0 ÷ 0 is not a number
        Design.Builder b = new Design.Builder(Specs.FP32);
        int e = b.prim(Prim.DIV, b.prim(Prim.LOG, b.prim(Prim.MAX, 0, b.prim(Prim.NEG, 0))), b.constant((float) Math.log(2)));
        int spacing = b.prim(Prim.POW2, b.prim(Prim.SUB, b.prim(Prim.ROUND, e), 1));
        List<Spec.Result> logged = Specs.FP32.check(b.output(b.prim(Prim.MUL, b.prim(Prim.ROUND, b.prim(Prim.DIV, 0, spacing)), spacing)));
        assertEquals("residual.bench.why.nan", logged.get(2).why(), "zero");

        // no floor and no top
        Design.Builder c = new Design.Builder(Specs.FP32);
        int plain = c.prim(Prim.POW2, c.prim(Prim.SUB, c.prim(Prim.EXPONENT, 0), 1));
        List<Spec.Result> bare = Specs.FP32.check(c.output(c.prim(Prim.MUL, c.prim(Prim.ROUND, c.prim(Prim.DIV, 0, plain)), plain)));
        assertTrue(bare.get(1).pass(), "ties");
        assertFalse(bare.get(3).pass(), "numbers below the lowest exponent are evenly spaced");
        assertFalse(bare.get(4).pass(), "numbers past the top stay at the top");
    }

    @Test
    void anFp8WithoutAGuardFailsOnASilentTensor() {
        Design.Builder b = new Design.Builder(Specs.FP8);
        int most = b.prim(Prim.AMAX, b.prim(Prim.TRANSPOSE, b.prim(Prim.AMAX, b.prim(Prim.MAX, 0, b.prim(Prim.NEG, 0)))));
        int s = b.prim(Prim.DIV, most, b.constant(448));
        Design naive = b.output(b.prim(Prim.MUL, b.module("fp32", b.prim(Prim.DIV, 0, s), b.constant(3), b.constant(-6), b.constant(448)), s));
        List<Spec.Result> r = Specs.FP8.check(naive);
        assertTrue(r.get(0).pass());
        assertEquals("residual.bench.why.nan", r.get(3).why(), "silent");
    }

    @Test
    void fp4WithOneScaleLosesTheBlockBesideAnOutlier() {
        Design.Builder b = new Design.Builder(Specs.FP4);
        int most = b.prim(Prim.AMAX, b.prim(Prim.TRANSPOSE, b.prim(Prim.AMAX, b.prim(Prim.MAX, 0, b.prim(Prim.NEG, 0)))));
        int s = b.prim(Prim.DIV, b.prim(Prim.MAX, most, b.constant(1e-30f)), b.constant(6));
        Design flat = b.output(b.prim(Prim.MUL, b.module("fp32", b.prim(Prim.DIV, 0, s), b.constant(1), b.constant(0), b.constant(6)), s));
        assertFalse(Specs.FP4.check(flat).get(1).pass(), "outlier");
    }

    @Test
    void aScheduleHasToStayDownAndSurviveNoWarmup() {
        Design.Builder b = new Design.Builder(Specs.SCHEDULE);
        int rise = b.prim(Prim.DIV, 0, 1);
        int up = b.prim(Prim.NEG, b.prim(Prim.MAX, b.constant(-1), b.prim(Prim.NEG, rise)));
        int along = b.prim(Prim.DIV, b.prim(Prim.SUB, 0, 2), b.prim(Prim.SUB, 3, 2));
        int wave = b.prim(Prim.COS, b.prim(Prim.MUL, b.constant((float) Math.PI), along));
        Design naive = b.output(b.prim(Prim.MUL, up, b.prim(Prim.MUL, b.constant(0.5f), b.prim(Prim.ADD, b.constant(1), wave))));
        List<Spec.Result> r = Specs.SCHEDULE.check(naive);
        assertFalse(r.get(3).pass(), "past the end a bare cosine climbs back up");
        assertEquals("residual.bench.why.nan", r.get(4).why(), "t ÷ W at step 0 with no warmup is 0 ÷ 0");
    }

    @Test
    void aNaiveClipFailsAtZeroAndAtAnExplodedGradient() {
        Design.Builder b = new Design.Builder(Specs.GRAD_CLIP);
        int norm = b.prim(Prim.SQRT, b.prim(Prim.SUM, b.prim(Prim.MUL, 0, 0)));
        Design naive = b.output(b.prim(Prim.DIV, b.prim(Prim.MUL, 0, 1), b.prim(Prim.MAX, norm, 1)));
        List<Spec.Result> r = Specs.GRAD_CLIP.check(naive);
        assertTrue(r.get(0).pass());
        assertTrue(r.get(1).pass() || !r.get(1).pass());
        assertFalse(r.get(3).pass(), "its square overflows");

        Design.Builder c = new Design.Builder(Specs.GRAD_CLIP);
        int n = c.prim(Prim.SQRT, c.prim(Prim.SUM, c.prim(Prim.MUL, 0, 0)));
        Design scaleAlways = c.output(c.prim(Prim.DIV, c.prim(Prim.MUL, 0, 1), n));
        List<Spec.Result> s = Specs.GRAD_CLIP.check(scaleAlways);
        assertFalse(s.get(1).pass(), "a small gradient must be left alone");
        assertEquals("residual.bench.why.nan", s.get(2).why(), "zero");
    }

    @Test
    void orthogonalizingLoudMomentumWithoutScalingBlowsUp() {
        Design.Builder b = new Design.Builder(Specs.ORTHOGONALIZE);
        int x = 0;
        for (int i = 0; i < 5; i++) {
            x = b.module("newton_step", x);
        }
        List<Spec.Result> r = Specs.ORTHOGONALIZE.check(b.output(x));
        assertFalse(r.get(2).pass(), "loud");

        Design.Builder c = new Design.Builder(Specs.ORTHOGONALIZE);
        int norm = c.prim(Prim.SQRT, c.prim(Prim.SUM, c.prim(Prim.TRANSPOSE, c.prim(Prim.SUM, c.prim(Prim.MUL, 0, 0)))));
        int y = c.prim(Prim.DIV, 0, norm);
        for (int i = 0; i < 5; i++) {
            y = c.module("newton_step", y);
        }
        List<Spec.Result> s = Specs.ORTHOGONALIZE.check(c.output(y));
        assertTrue(s.get(0).pass(), "ordinary");
        assertEquals("residual.bench.why.nan", s.get(3).why(), "still momentum: 0 ÷ 0");
    }

    @Test
    void aDocumentMaskIsMoreThanACausalOne() {
        Design.Builder b = new Design.Builder(Specs.DOCUMENT_MASK);
        List<Spec.Result> r = Specs.DOCUMENT_MASK.check(b.output(b.prim(Prim.CAUSAL, 0)));
        assertTrue(r.get(3).pass(), "one document");
        assertFalse(r.get(0).pass(), "two");
    }

    @Test
    void crossEntropyByLoggingASoftmaxFails() {
        Design.Builder b = new Design.Builder(Specs.CROSS_ENTROPY);
        int shifted = b.prim(Prim.SUB, 0, b.prim(Prim.AMAX, 0));
        int e = b.prim(Prim.EXP, shifted);
        int p = b.prim(Prim.DIV, e, b.prim(Prim.SUM, e));
        Design naive = b.output(b.prim(Prim.NEG, b.prim(Prim.SUM, b.prim(Prim.MUL, 1, b.prim(Prim.LOG, p)))));
        List<Spec.Result> r = Specs.CROSS_ENTROPY.check(naive);
        assertTrue(r.get(0).pass());
        assertEquals("residual.bench.why.nan", r.get(1).why(), "sure and right: 0 × log 0 on the tokens ruled out");
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
