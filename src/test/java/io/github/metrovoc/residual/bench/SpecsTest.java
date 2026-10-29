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
