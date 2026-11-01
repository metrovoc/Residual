package io.github.metrovoc.residual.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.metrovoc.residual.bench.Spec;
import io.github.metrovoc.residual.bench.Specs;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The machines against the specs they come from, fused and not, and their gradients against finite differences. */
class MachinesTest {
    private static Tensor batched(Tensor x) {
        return new Tensor(new int[]{1, x.dim(0), x.dim(1)}, x.data.clone(), true);
    }

    private static Tensor param(Tensor t) {
        return new Tensor(t.shape.clone(), t.data.clone(), true);
    }

    private static Tensor run(Op op, Config c, Map<String, Tensor> p, boolean fused, Tape tape, Tensor... in) {
        return op.run(tape, in, p, c, new Op.Frame(0, in[0].dim(-2), null, fused, null));
    }

    private static void assertMatches(Spec.Case c, Tensor got, String what) {
        for (int i = 0; i < got.size(); i++) {
            assertEquals(c.expected().data[i], got.data[i], Spec.ABS + Spec.REL * Math.abs(c.expected().data[i]), what + " " + c.id() + " " + i);
        }
    }

    private static Map<String, Tensor> blockParams(Tensor[] in) {
        Map<String, Tensor> p = new LinkedHashMap<>();
        String[] names = {"rms1.weight", "attn.q", "attn.k", "attn.v", "attn.o", "attn.t", "rms2.weight", "mlp.w1", "mlp.w2", "mlp.w3"};
        for (int i = 0; i < names.length; i++) {
            p.put(names[i], param(in[i + 1]));
        }
        return p;
    }

    @Test
    void theModernBlockIsItsSpec() {
        Config c = Config.of(Op.MODERN_BLOCK, "heads", "1");
        for (Spec.Case k : Specs.MODERN_BLOCK.cases()) {
            for (boolean fused : new boolean[]{true, false}) {
                assertMatches(k, run(Op.MODERN_BLOCK, c, blockParams(k.inputs()), fused, null, batched(k.inputs()[0])), "fused " + fused);
            }
        }
    }

    @Test
    void theModernHeadIsItsSpec() {
        Config c = Config.of(Op.MODERN_ATTENTION, "heads", "1");
        for (Spec.Case k : Specs.MODERN_ATTENTION.cases()) {
            Map<String, Tensor> p = new LinkedHashMap<>();
            String[] names = {"q", "k", "v", "o", "t"};
            for (int i = 0; i < names.length; i++) {
                p.put(names[i], param(k.inputs()[i + 1]));
            }
            for (boolean fused : new boolean[]{true, false}) {
                assertMatches(k, run(Op.MODERN_ATTENTION, c, p, fused, null, batched(k.inputs()[0])), "fused " + fused);
            }
        }
    }

    @Test
    void theGatedBlockIsItsSpec() {
        for (Spec.Case k : Specs.SWIGLU.cases()) {
            Map<String, Tensor> p = new LinkedHashMap<>();
            p.put("w1", param(k.inputs()[1]));
            p.put("w2", param(k.inputs()[2]));
            p.put("w3", param(k.inputs()[3]));
            for (boolean fused : new boolean[]{true, false}) {
                assertMatches(k, run(Op.SWIGLU, new Config(Op.SWIGLU), p, fused, null, batched(k.inputs()[0])), "fused " + fused);
            }
        }
    }

    @Test
    void theNormalizedScoresAreTheirSpec() {
        for (Spec.Case k : Specs.QK_NORM.cases()) {
            Map<String, Tensor> p = Map.of("t", param(k.inputs()[2]));
            for (boolean fused : new boolean[]{true, false}) {
                assertMatches(k, run(Op.QK_NORM, new Config(Op.QK_NORM), p, fused, null, batched(k.inputs()[0]), batched(k.inputs()[1])), "fused " + fused);
            }
        }
    }

    /** A score can't exceed t·√d, however loud the queries and keys. */
    @Test
    void normalizedScoresAreBounded() {
        Random rng = new Random(3);
        for (float loud : new float[]{1f, 1e3f, 1e6f}) {
            Tensor q = Tensor.zeros(false, 1, 8, 16);
            Tensor k = Tensor.zeros(false, 1, 8, 16);
            for (int i = 0; i < q.size(); i++) {
                q.data[i] = (float) rng.nextGaussian() * loud;
                k.data[i] = (float) rng.nextGaussian() * loud;
            }
            Tensor s = run(Op.QK_NORM, new Config(Op.QK_NORM), Map.of("t", Tensor.of(new int[]{1}, 2f)), true, null, q, k);
            for (float v : s.data) {
                assertEquals(0, Math.max(0, Math.abs(v) - 2 * 4.001), 0, "score " + v + " at loudness " + loud);
            }
        }
    }

    private static void gradients(Op op, Config c, Map<String, Tensor> p, boolean fused, Tensor x) {
        Random rng = new Random(11);
        Tensor probe = run(op, c, p, fused, null, x);
        float[] w = new float[probe.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = (float) rng.nextGaussian();
        }
        Tape tape = new Tape();
        Tensor out = run(op, c, p, fused, tape, x);
        tape.backward(Ops.sum(tape, Ops.mul(tape, out, Tensor.of(out.shape.clone(), w))));
        List<Tensor> inputs = new ArrayList<>(p.values());
        inputs.add(x);
        for (Tensor t : inputs) {
            float[] analytic = t.grad().clone();
            for (int i = 0; i < t.size(); i += 3) {
                float old = t.data[i];
                float h = 1e-2f;
                t.data[i] = old + h;
                double up = Ops.sum(null, Ops.mul(null, run(op, c, p, fused, null, x), Tensor.of(out.shape.clone(), w))).item();
                t.data[i] = old - h;
                double down = Ops.sum(null, Ops.mul(null, run(op, c, p, fused, null, x), Tensor.of(out.shape.clone(), w))).item();
                t.data[i] = old;
                double numeric = (up - down) / (2 * h);
                assertEquals(numeric, analytic[i], 3e-2 * Math.max(1.0, Math.abs(numeric)), op.id + " fused " + fused + " element " + i);
            }
            t.zeroGrad();
        }
    }

    @Test
    void theModernBlockTrains() {
        Random rng = new Random(5);
        for (boolean fused : new boolean[]{true, false}) {
            Config c = Config.of(Op.MODERN_BLOCK, "heads", "2");
            Sym[] in = {Sym.of(Sym.B, Sym.T, 8)};
            Map<String, Tensor> p = new LinkedHashMap<>();
            for (ParamSpec spec : Op.MODERN_BLOCK.params(in, c, Env.WORLD)) {
                p.put(spec.name(), spec.create(rng));
            }
            Tensor x = Tensor.zeros(true, 1, 5, 8);
            for (int i = 0; i < x.size(); i++) {
                x.data[i] = (float) rng.nextGaussian();
            }
            gradients(Op.MODERN_BLOCK, c, p, fused, x);
        }
    }
}
