package io.github.metrovoc.residual.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Recipe;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Vocab;
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
}
