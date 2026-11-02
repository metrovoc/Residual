package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TextPackingTest {
    @Test
    void copiesOwnTheirPackingAndCountOnlyReadableText() {
        TextTask base = new TextTask(List.of("ab", "cd", "e"), 1.4, Vocab.WORLD);
        TextTask packed = base.withPacking(Recipe.Packing.PACKED);
        TextTask masked = base.withPacking(Recipe.Packing.MASKED);
        assertTrue(base.isEmpty());
        assertEquals(0, base.characters());
        assertFalse(packed.isEmpty());
        assertEquals(4, packed.characters());
        assertEquals(0.8f, packed.readShare());
        assertEquals(List.of("ab", "cd"), packed.heldOutTexts());
        packed.packing = Recipe.Packing.WINDOWS;
        assertTrue(packed.isEmpty());
        assertFalse(masked.isEmpty());
        assertNotNull(masked.batch(new Random(4), 2, false).input().starts);
    }

    @Test
    void shortOnlySourcesTrainAndTestWithoutScoringDocumentSeams() throws Exception {
        for (Recipe.Packing packing : List.of(Recipe.Packing.PACKED, Recipe.Packing.MASKED)) {
            TextTask text = new TextTask(List.of("ab", "cd"), 1.4, Vocab.WORLD).withPacking(packing);
            for (boolean test : new boolean[]{false, true}) {
                Batch batch = text.batch(new Random(2), 2, test);
                assertNotNull(batch.weights());
                if (test || packing == Recipe.Packing.MASKED) {
                    assertNotNull(batch.input().starts);
                } else {
                    assertNull(batch.input().starts);
                }
                int scored = 0;
                for (int row = 0; row < 2; row++) {
                    for (int i = 0; i < batch.input().time; i++) {
                        int at = row * batch.input().time + i;
                        assertEquals(i % 2 == 0 ? 1f : 0f, batch.weights()[at]);
                        if (batch.input().starts != null) {
                            assertEquals(i / 2 * 2, batch.input().starts[at]);
                        }
                        if (batch.weights()[at] != 0) {
                            scored++;
                            String pair = Vocab.WORLD.piece(batch.input().ids[at]) + Vocab.WORLD.piece(batch.targets()[at]);
                            assertTrue(pair.equals("ab") || pair.equals("cd"), pair);
                        }
                    }
                }
                assertEquals(batch.input().ids.length / 2, scored);
            }
            CompiledGraph model = CompiledGraph.compile(Blueprints.context(2, 8, 16), Env.WORLD, null, 2);
            Trainer trainer = new Trainer(model, text, 3, 1e-3f, 2, 1f);
            assertTrue(Float.isFinite(trainer.train()));
            assertTrue(Double.isFinite(trainer.evaluate(2).loss()));
        }
    }

    @Test
    void longWindowEvaluationKeepsItsExactRowsAndNormalization() {
        TextTask windows = new TextTask(List.of("abcdef".repeat(40), "xy"), 1.4, Vocab.WORLD);
        Batch expected = windows.batch(new Random(8), 3, true);
        for (Recipe.Packing packing : Recipe.Packing.values()) {
            Batch actual = windows.withPacking(packing).batch(new Random(8), 3, true);
            assertArrayEquals(expected.input().ids, actual.input().ids);
            assertArrayEquals(expected.targets(), actual.targets());
            assertNull(actual.weights());
            assertNull(actual.input().starts);
            assertEquals(expected.perChar(), actual.perChar());
        }
    }
}
