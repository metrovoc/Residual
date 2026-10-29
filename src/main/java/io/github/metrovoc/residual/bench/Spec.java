package io.github.metrovoc.residual.bench;

import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * What a module must compute, as an old engineer's note would put it: its inputs, test cases with the answers worked
 * out exactly, and the machines a verified design lets the Fabricator make. Text lives under
 * {@code residual.spec.<id>}.
 *
 * @param ports    its inputs in order; parameters become the fabricated machine's weights
 * @param kernel   the fused computation, used when the module is a part of another design
 * @param requires specs whose modules a design is expected to use
 * @param unlocks  machines the Fabricator makes from a design that passes
 * @param salvaged the design found in the ruins: correct, written one primitive at a time
 */
public record Spec(String id, List<Port> ports, List<Case> cases, Kernel kernel, List<String> requires, List<Op> unlocks,
                   Function<Design.Builder, Design> salvaged) {
    /** Values may differ from the exact answer by this much plus this share of the answer. */
    public static final float ABS = 1e-4f, REL = 1e-3f;

    public record Port(String name, boolean param) {
    }

    public record Case(String id, Tensor[] inputs, Tensor expected) {
    }

    public interface Kernel {
        Tensor apply(@Nullable Tape tape, Tensor[] in);
    }

    /** Whether a player who verified {@code verified} may work on this spec: every spec it builds on is verified. */
    public boolean openTo(List<String> verified) {
        return verified.containsAll(requires);
    }

    public Design salvagedDesign() {
        return salvaged.apply(new Design.Builder(this));
    }

    /** Fabrication area of a module made from this spec, as the salvaged design lays it out. */
    public int area() {
        return salvagedDesign().area();
    }

    // ---------------------------------------------------------------- checking

    /** How a design did on one case. {@code why} is empty when it passed. */
    public record Result(String caseId, @Nullable Tensor got, Tensor want, boolean pass, String why, int worst) {
    }

    public List<Result> check(Design design) {
        List<Result> out = new ArrayList<>();
        for (Case c : cases) {
            out.add(check(design, c));
        }
        return out;
    }

    public static boolean passes(List<Result> results) {
        return !results.isEmpty() && results.stream().allMatch(Result::pass);
    }

    public Result check(Design design, Case c) {
        Design.Run run = design.run(null, c.inputs());
        Tensor got = run.result();
        Tensor want = c.expected();
        if (design.output() == Design.OPEN) {
            return new Result(c.id(), null, want, false, "residual.bench.why.no_output", -1);
        }
        if (got == null) {
            return new Result(c.id(), null, want, false, "residual.bench.why.broken", -1);
        }
        if (!Arrays.equals(got.shape, want.shape)) {
            return new Result(c.id(), got, want, false, "residual.bench.why.shape", -1);
        }
        int worst = -1;
        double worstErr = 0;
        for (int i = 0; i < got.data.length; i++) {
            float g = got.data[i];
            float w = want.data[i];
            if (!Float.isFinite(g)) {
                if (g != w) {
                    return new Result(c.id(), got, want, false, Float.isNaN(g) ? "residual.bench.why.nan" : "residual.bench.why.inf", i);
                }
                continue;
            }
            double err = Math.abs((double) g - w);
            if (err > ABS + REL * Math.abs(w) && err > worstErr) {
                worstErr = err;
                worst = i;
            }
        }
        if (worst >= 0) {
            return new Result(c.id(), got, want, false, "residual.bench.why.off", worst);
        }
        return new Result(c.id(), got, want, true, "", -1);
    }
}
