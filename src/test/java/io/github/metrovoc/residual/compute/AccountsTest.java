package io.github.metrovoc.residual.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.AdamW;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** What a run costs: the flops counted as they happen against 6ND, and what a parameter takes to train. */
class AccountsTest {
    private static final Task TASK = Benchmark.SORTING.practice();

    private static CompiledGraph model(GraphSpec spec) throws Exception {
        return CompiledGraph.compile(spec, new Env(TASK.vocab, Vocab.CONTEXT), null, 3);
    }

    /** Flops a parameter a token on a whole training step. */
    private static double perParameterToken(GraphSpec spec, int batch) throws Exception {
        CompiledGraph model = model(spec);
        Trainer trainer = new Trainer(model, TASK, 5, 1e-3f, batch, 1f);
        LongAdder flops = new LongAdder();
        Flops.with(flops, trainer::train);
        return (double) flops.sum() / model.parameterCount() / trainer.lastTokens();
    }

    @Test
    void aMultiplyIsCountedExactly() throws Exception {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "16"), GraphSpec.TOKENS);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab", "bias", "false"), e);
        CompiledGraph model = model(g);
        io.github.metrovoc.residual.tensor.Tokens tokens = new io.github.metrovoc.residual.tensor.Tokens(new int[8 * 4], 8, 4);
        LongAdder flops = new LongAdder();
        Flops.with(flops, () -> model.plain(null, tokens, null));
        int v = TASK.vocab.size();
        // two flops for each entry of the multiply, and a flop for each number the embedding and the multiply made
        long multiply = 2L * 8 * 4 * 16 * v;
        assertEquals(multiply + 8 * 4 * 16 + 8 * 4 * v, flops.sum());
    }

    /** About 6ND, as the rule says: a little over, since attention's own multiply and softmax add to what the weights take. */
    @Test
    void aTransformerTakesAboutSixFlopsAParameterAToken() throws Exception {
        double ratio = perParameterToken(Blueprints.gpt(32, 2, 4), 16);
        assertTrue(ratio > 4 && ratio < 12, "flops a parameter a token: " + ratio);
    }

    @Test
    void sixteenBytesAParameter() throws Exception {
        CompiledGraph model = model(Blueprints.gpt(32, 2, 4));
        assertEquals(16 * model.parameterCount(), Accounts.trainingBytes(model.parameterCount()));
        assertEquals(model.parameterCount(), Accounts.largestModel(16 * model.parameterCount()));
        // a pipeline's stages between them hold the whole of it
        for (int devices : new int[] {1, 2, 3, 8}) {
            assertEquals(Accounts.trainingBytes(model.parameterCount()), Arrays.stream(PipelineParallel.stageBytes(model, devices)).sum(), "on " + devices);
        }
    }

    @Test
    void aBookingIsTheSums() {
        Accounts.Capacity hall = new Accounts.Capacity(1000, 1e15, 1L << 40, 1e8, 1.25);
        Accounts.Booking run = Accounts.book(1_000_000_000L, 20e9, hall);
        assertEquals(6 * 1e9 * 20e9, run.flops(), 1e6);
        assertEquals(16_000_000_000L, run.bytes());
        assertTrue(run.fits());
        assertEquals(run.flops() / 1e8 * 1.25, run.energy(), 1e3);
        assertEquals(run.flops() / 1e15 / 86_400, run.days(), 1e-6);
        assertTrue(!Accounts.book(1L << 40, 1, hall).fits());
    }

    @Test
    void aFinerNodeHoldsMoreAndRunsFartherOnAUnitOfEnergy() {
        ProcessNode last = null;
        for (ProcessNode node : ProcessNode.ALL) {
            if (last != null) {
                assertEquals(8 * last.memoryBytes(), node.memoryBytes());
                assertEquals(4 * last.flopsPerSecond(), node.flopsPerSecond(), 1);
                assertEquals(2 * last.flopsPerFe(), node.flopsPerFe(), 1);
                assertEquals(2 * last.fePerTick(), node.fePerTick());
            }
            last = node;
        }
        assertEquals(80, ProcessNode.N90.fePerTick());
        assertEquals(64, ProcessNode.N3.units());
    }

    /** What the rules read on real models, and the memory a parameter really takes to train. */
    @Test
    @EnabledIfEnvironmentVariable(named = "RESIDUAL_MEASURE", matches = ".+")
    void measure() throws Exception {
        for (Object[] m : new Object[][] {{"gpt32", Blueprints.gpt(32, 2, 4)}, {"gpt64", Blueprints.gpt(64, 2, 4)}, {"gpt128x4", Blueprints.gpt(128, 4, 4)}}) {
            System.out.printf(Locale.ROOT, "%s: %.2f flops a parameter a token%n", m[0], perParameterToken((GraphSpec) m[1], 8));
        }
        // weights, gradients and Adam's two averages, all of them really allocated: what this thread allocates for them
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        new AdamW(model(Blueprints.gpt(32, 1, 2)).parameters());
        long before = threads.getCurrentThreadAllocatedBytes();
        CompiledGraph big = model(Blueprints.gpt(256, 4, 4));
        AdamW adam = new AdamW(big.parameters());
        for (Tensor p : big.parameters()) {
            p.grad();
        }
        long after = threads.getCurrentThreadAllocatedBytes();
        System.out.printf(Locale.ROOT, "%,d parameters: %.1f bytes a parameter allocated, optimizer step %d%n", big.parameterCount(),
                (double) (after - before) / big.parameterCount(), adam.stepCount());
    }
}
