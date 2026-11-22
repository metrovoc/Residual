package io.github.metrovoc.residual.interior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Vocab;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunTest {
    private static Run run(Arch arch, Benchmark bench) throws Exception {
        GraphSpec spec = arch.spec();
        CompiledGraph model = CompiledGraph.compile(spec, new Env(Vocab.WORLD, Vocab.CONTEXT), Arch.carried(spec, Map.of()), 1);
        Task practice = bench.practice();
        return new Run(arch, model, practice, bench.test(practice.vocab), 1, 0.003f, 7, bench.length, 0, java.util.List.of(), 2);
    }

    private static Snapshot await(Run run, java.util.function.Predicate<Snapshot> until) throws InterruptedException {
        long end = System.currentTimeMillis() + 60_000;
        Snapshot last = null;
        while (System.currentTimeMillis() < end) {
            Snapshot s = run.poll();
            if (s != null) {
                last = s;
                if (until.test(s)) {
                    return s;
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("timed out; last " + (last == null ? "nothing" : "step " + last.step() + " " + last.state()));
    }

    @Test
    void aPausedRunShowsItsExampleAndStepsWhenAsked() throws Exception {
        Arch arch = Arch.table().withPositions(true).withHeads(2).insert(0, Arch.Kind.ATTENTION).insert(1, Arch.Kind.FEED_FORWARD);
        Run run = run(arch, Benchmark.REVERSAL);
        run.start();
        try {
            Snapshot first = await(run, s -> true);
            assertEquals(0, first.step());
            assertEquals(Snapshot.State.PAUSED, first.state());
            assertEquals(Benchmark.REVERSAL.length, first.lanes());
            assertEquals(16, first.embed().cols());
            assertNotNull(first.position());
            assertEquals(2, first.parts().size());
            Snapshot.PartView attention = first.parts().get(0);
            assertEquals(2, attention.heads());
            assertNull(first.parts().get(1).pattern());
            // a lane reads only from itself and the lanes before it, and all it reads adds up to one
            for (int head = 0; head < 2; head++) {
                for (int to = 0; to < first.lanes(); to++) {
                    float sum = 0;
                    for (int from = 0; from < first.lanes(); from++) {
                        float w = attention.weight(head, to, from);
                        assertTrue(from <= to || w == 0, "lane " + to + " reads ahead to " + from);
                        sum += w;
                    }
                    assertEquals(1f, sum, 0.05f);
                }
            }
            for (int lane = 0; lane < first.lanes(); lane++) {
                float sum = 0;
                for (int v = 0; v < first.vocab(); v++) {
                    sum += first.chance(lane, v);
                }
                assertEquals(1f, sum, 0.2f);
            }
            assertNotNull(first.tables());
            assertEquals(Vocab.WORLD.size(), first.tables().embed().rows());
            assertTrue(first.scored()[first.lanes() - 1]);
            assertTrue(!first.scored()[0]);

            run.step(3);
            Snapshot after = await(run, s -> s.step() == 3 && s.state() == Snapshot.State.PAUSED);
            assertTrue(Float.isFinite(after.trainLoss()));
            Thread.sleep(300);
            assertEquals(3, run.steps());
        } finally {
            run.close();
        }
    }

    @Test
    void aRunPassesReversalAndCanBePaused() throws Exception {
        Arch arch = Arch.table().withPositions(true).insert(0, Arch.Kind.ATTENTION).insert(1, Arch.Kind.FEED_FORWARD);
        Run run = run(arch, Benchmark.REVERSAL);
        run.pace(0);
        run.running(true);
        run.start();
        try {
            Snapshot passed = await(run, Snapshot::passed);
            assertTrue(passed.score() >= 0.9f);
            assertTrue(passed.curve().size() > 1);
            run.running(false);
            Snapshot paused = await(run, s -> s.state() == Snapshot.State.PAUSED);
            Thread.sleep(300);
            assertEquals(paused.step(), run.steps());
        } finally {
            run.close();
        }
    }

    @Test
    void aRateTooHighIsSeenToDiverge() throws Exception {
        Arch arch = Arch.table().withPositions(true).withNorm(false).insert(0, Arch.Kind.ATTENTION).insert(1, Arch.Kind.FEED_FORWARD);
        Run run = run(arch, Benchmark.REVERSAL);
        run.rate(1f);
        run.pace(0);
        run.running(true);
        run.start();
        try {
            Snapshot s = await(run, x -> x.step() >= 300 || x.state() == Snapshot.State.DIVERGED);
            assertTrue(!s.passed());
        } finally {
            run.close();
        }
    }
}
