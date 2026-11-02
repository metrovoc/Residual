package io.github.metrovoc.residual.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Modular;
import io.github.metrovoc.residual.learn.Trainer;
import org.junit.jupiter.api.Test;

/** The discoveries, each on a real model training here, and on the models that must not make them. */
class DiscoveriesTest {
    @Test
    void anInductionHeadFormsOnPatterns() throws Exception {
        CompiledGraph model = CompiledGraph.compile(Blueprints.induction(), Env.WORLD, null, 1);
        Trainer tr = new Trainer(model, Benchmark.INDUCTION.practice(), 1, 3e-3f, 32, 1f);
        Discoveries.Seen seen = new Discoveries.Seen();
        Discoveries.Found found = null;
        int at = 0;
        for (int s = 1; s <= 1500 && found == null; s++) {
            tr.train();
            if (s % Discoveries.READ_EVERY == 0) {
                found = Discoveries.readRuns(model, seen);
                at = s;
            }
        }
        assertNotNull(found, "the induction blueprint on the Pattern Dataset forms a head");
        assertEquals("phase_change", found.id());
        System.out.printf("phase change at step %d: copied %.3f, prefix %.3f%n", at, found.first(), found.second());
        assertTrue(Heads.read(model).copied() >= Discoveries.COPIES);
        assertNull(Discoveries.readRuns(model, seen), "once");
    }

    @Test
    void aModelThatCameInAlreadyCopyingFormedNothing() throws Exception {
        CompiledGraph model = CompiledGraph.compile(Blueprints.recall(), Env.WORLD, null, 1);
        Trainer tr = new Trainer(model, Benchmark.INDUCTION.practice(), 1, 3e-3f, 32, 1f);
        for (int s = 0; s < 600; s++) {
            tr.train();
        }
        Discoveries.Seen seen = new Discoveries.Seen();
        assertNull(Discoveries.readRuns(model, seen), "never seen before it copied: nothing formed while anyone watched");
        assertFalse(seen.formed);
    }

    @Test
    void modelsThatDoNotCopyFormNothing() throws Exception {
        for (GraphSpec spec : new GraphSpec[]{Blueprints.bigram(), Blueprints.context()}) {
            CompiledGraph model = CompiledGraph.compile(spec, Env.WORLD, null, 1);
            Trainer tr = new Trainer(model, Benchmark.INDUCTION.practice(), 1, 3e-3f, 32, 1f);
            Discoveries.Seen seen = new Discoveries.Seen();
            for (int s = 1; s <= 500; s++) {
                tr.train();
                if (s % Discoveries.READ_EVERY == 0) {
                    assertNull(Discoveries.readRuns(model, seen));
                }
            }
        }
    }

    @Test
    void aModelGroksUnderStrongDecay() throws Exception {
        CompiledGraph model = CompiledGraph.compile(Blueprints.sums(), Env.WORLD, null, 1);
        Trainer tr = new Trainer(model, Benchmark.MODULAR.practice(), 1, 1e-2f, 64, 1f);
        tr.weightDecay = 0.1f;
        Discoveries.Seen seen = new Discoveries.Seen();
        Discoveries.Found found = null;
        int at = 0;
        for (int s = 1; s <= 25000 && found == null; s++) {
            tr.train();
            if (s % Discoveries.SUMS_EVERY == 0) {
                found = Discoveries.readSums(model, s, seen);
                at = s;
            }
        }
        assertNotNull(found, "memorised at step " + seen.knewAt + ", blind until " + seen.blindUntil + ", now " + Modular.right(model, true));
        System.out.printf("grokked at step %d: knew its practice at %d, knew none of the rest until %d, %.3f and %.3f%n", at, seen.knewAt, seen.blindUntil, found.first(), found.second());
        assertTrue(Modular.right(model, true) >= Discoveries.GROKKED);
        float[] waves = Heads.waves(model);
        float[] sorted = waves.clone();
        java.util.Arrays.sort(sorted);
        float five = 0;
        for (int k = sorted.length - 5; k < sorted.length; k++) {
            five += sorted[k];
        }
        System.out.printf("its token table: %.0f%% in its five strongest frequencies of %d%n", 100 * five, waves.length);
        assertTrue(five > 0.3f, "the rule shows in the table as a few waves: " + five);
    }

    @Test
    void learningTheRuleStraightAwayIsNotGrokking() {
        Discoveries.Seen seen = new Discoveries.Seen();
        // a transformer under decay 1: practice and the rest rise together
        float[][] run = {{0.49f, 0.07f}, {0.75f, 0.4f}, {0.99f, 0.95f}, {1f, 0.99f}};
        for (int i = 0; i < run.length; i++) {
            assertNull(Discoveries.sums(1000 * (i + 1), run[i][0], run[i][1], seen));
        }
        // and a short wait is not long enough: it knew its practice at 4,000, and the rest by 4,500
        seen = new Discoveries.Seen();
        assertNull(Discoveries.sums(4000, 0.9f, 0.2f, seen));
        assertNull(Discoveries.sums(4250, 0.95f, 0.25f, seen));
        assertNull(Discoveries.sums(4500, 1f, 0.95f, seen));
        // a long one is: it knew them at 2,000 and none of the rest until 11,000
        seen = new Discoveries.Seen();
        assertNull(Discoveries.sums(2000, 0.9f, 0.01f, seen));
        assertNull(Discoveries.sums(11000, 1f, 0.3f, seen));
        assertNull(Discoveries.sums(12000, 1f, 0.8f, seen));
        assertNotNull(Discoveries.sums(13000, 1f, 0.95f, seen));
    }
}
