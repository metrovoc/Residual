package io.github.metrovoc.residual.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Recipe;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.refine.Dump;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CoreReadingTest {
    @Test
    void recoveredTextUsesPackingBeforeCheckingWhetherItIsReadable() {
        List<String> pieces = List.of("a short letter", "a second letter");
        assertTrue(CoreBlockEntity.reading(pieces, Vocab.WORLD, Recipe.Packing.WINDOWS).isEmpty());
        TextTask packed = CoreBlockEntity.reading(pieces, Vocab.WORLD, Recipe.Packing.PACKED);
        assertFalse(packed.isEmpty());
        assertNotNull(packed.batch(new Random(3), 2, false).weights());
        Batch masked = CoreBlockEntity.reading(pieces, Vocab.WORLD, Recipe.Packing.MASKED).batch(new Random(3), 2, false);
        assertNotNull(masked.input().starts);
    }

    @Test
    void aDumpsActualTextPassesThroughTheCoresReadingFactory() {
        List<String> pieces = Dump.texts(11);
        TextTask windows = CoreBlockEntity.reading(pieces, Vocab.WORLD, Recipe.Packing.WINDOWS);
        TextTask packed = CoreBlockEntity.reading(pieces, Vocab.WORLD, Recipe.Packing.PACKED);
        TextTask masked = CoreBlockEntity.reading(pieces, Vocab.WORLD, Recipe.Packing.MASKED);
        assertFalse(windows.isEmpty());
        assertNull(windows.batch(new Random(7), 2, false).weights());
        assertNotNull(packed.batch(new Random(7), 2, false).weights());
        assertNotNull(masked.batch(new Random(7), 2, false).input().starts);
        Batch ordinary = windows.batch(new Random(9), 2, true);
        Batch checked = masked.batch(new Random(9), 2, true);
        assertArrayEquals(ordinary.input().ids, checked.input().ids);
        assertArrayEquals(ordinary.targets(), checked.targets());
        assertNull(checked.weights());
        assertNull(checked.input().starts);
    }
}
