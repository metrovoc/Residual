package io.github.metrovoc.residual.refine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Recipe;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplyPackingTest {
    private static Supply source() {
        List<Doc> docs = new ArrayList<>();
        docs.add(new Doc("a".repeat(128), Domain.LETTERS));
        for (int i = 0; i < 64; i++) {
            docs.add(new Doc("bbbb", Domain.TALES));
        }
        return Supply.plain(docs);
    }

    private static Random choose(float value) {
        return new Random(7) {
            @Override
            public float nextFloat() {
                return value;
            }
        };
    }

    private static void only(Batch batch, char letter) {
        for (int id : batch.input().ids) {
            assertEquals(Vocab.WORLD.id(letter), id);
        }
    }

    @Test
    void consumersRecomputeEligibleDomainsAndSharesWithoutChangingEachOther() {
        Supply supply = source();
        SupplyTask template = new SupplyTask(supply, Vocab.WORLD);
        SupplyTask masked = template.fresh(Recipe.Packing.MASKED);
        SupplyTask windows = template.fresh(Recipe.Packing.WINDOWS);
        SupplyTask packed = template.fresh(Recipe.Packing.PACKED);
        assertSame(supply, masked.supply());
        only(windows.batch(choose(0.5f), 1, false), 'a');
        only(packed.batch(choose(0.2f), 1, false), 'a');
        Batch shortRows = packed.batch(choose(0.5f), 1, false);
        only(shortRows, 'b');
        assertNotNull(shortRows.weights());
        assertNull(shortRows.input().starts);
        assertNotNull(masked.batch(choose(0.5f), 1, false).input().starts);
        assertEquals(0, template.served());
        assertEquals(1, windows.served());
        assertEquals(2, packed.served());
        assertEquals(1, masked.served());
        assertNull(windows.batch(choose(0.5f), 1, false).input().starts);
        assertNotNull(masked.fresh().batch(choose(0.5f), 1, false).input().starts);
    }

    @Test
    void switchingPackingKeepsAnAnnealedRunsStepCount() {
        Supply plain = source();
        Supply supply = new Supply(plain.docs(), plain.mix(), Domain.TALES.ordinal(), 2);
        SupplyTask current = new SupplyTask(supply, Vocab.WORLD, Recipe.Packing.PACKED);
        current.batch(choose(0), 1, false);
        current.batch(choose(0), 1, false);
        assertTrue(current.annealing());
        assertEquals(Supply.ANNEAL_RATE, current.rateScale());
        SupplyTask changed = current.fresh(Recipe.Packing.MASKED);
        assertFalse(changed.annealing());
        changed.carryOn(current);
        assertEquals(2, changed.served());
        assertTrue(changed.annealing());
        only(changed.batch(choose(0), 1, false), 'b');
        assertEquals(Supply.ANNEAL_RATE, changed.rateScale());
        assertEquals(2, current.served());
        SupplyTask independent = changed.fresh();
        assertEquals(0, independent.served());
        assertEquals(1f, independent.rateScale());
        SupplyTask windows = current.fresh(Recipe.Packing.WINDOWS);
        windows.carryOn(current);
        assertFalse(windows.annealing(), "the short-only target is unreadable as windows");
        only(windows.batch(choose(0.9f), 1, false), 'a');
    }

    @Test
    void aShortOnlySupplyIsUsableAndItsEvaluationKeepsDocumentsSeparate() throws Exception {
        SupplyTask template = new SupplyTask(Supply.plain(List.of(new Doc("ab", Domain.TOWN), new Doc("cd", Domain.TOWN))), Vocab.WORLD);
        assertTrue(template.isEmpty());
        CompiledGraph model = CompiledGraph.compile(Blueprints.bigram(), Env.WORLD, null, 1);
        for (Recipe.Packing packing : List.of(Recipe.Packing.PACKED, Recipe.Packing.MASKED)) {
            SupplyTask source = template.fresh(packing);
            assertFalse(source.isEmpty());
            assertEquals(List.of("ab", "cd"), source.heldOutTexts());
            Batch test = source.batch(new Random(4), 2, true);
            assertNotNull(test.input().starts);
            for (int i = 1; i < test.input().time; i += 2) {
                assertEquals(0f, test.weights()[i]);
            }
            assertEquals(0, source.served(), "evaluation does not anneal the run");
            assertTrue(Double.isFinite(Trainer.evaluate(model, source, 2).loss()));
        }
    }

    @Test
    void masksFromActualSupplyBatchesStopAttentionAcrossDocuments() throws Exception {
        SupplyTask task = new SupplyTask(Supply.plain(List.of(new Doc("abcdef", Domain.LETTERS), new Doc("uvwxyz", Domain.LETTERS))), Vocab.WORLD,
                Recipe.Packing.MASKED);
        Batch batch = task.batch(new Random(4), 1, false);
        int[] ids = batch.input().ids, starts = batch.input().starts;
        int seam = 1;
        while (seam < ids.length && starts[seam] == 0) {
            seam++;
        }
        assertTrue(seam < ids.length);
        assertEquals(0f, batch.weights()[seam - 1]);
        int[] changed = ids.clone();
        for (int i = 0; i < seam; i++) {
            changed[i] = (ids[i] + 7) % Vocab.WORLD.size();
        }
        CompiledGraph model = CompiledGraph.compile(Blueprints.gpt(16, 1, 2), Env.WORLD, null, 3);
        Tensor first = model.forward(null, batch.input(), null);
        Tensor second = model.forward(null, new Tokens(changed, 1, ids.length, starts), null);
        int from = seam * first.dim(-1);
        assertArrayEquals(Arrays.copyOfRange(first.data, from, first.size()), Arrays.copyOfRange(second.data, from, second.size()), 1e-6f);
        Tensor open = model.forward(null, new Tokens(ids, 1, ids.length), null);
        Tensor openChanged = model.forward(null, new Tokens(changed, 1, ids.length), null);
        boolean differs = false;
        for (int i = from; i < open.size(); i++) {
            differs |= Math.abs(open.data[i] - openChanged.data[i]) > 1e-4f;
        }
        assertTrue(differs, "the unmasked control must actually use preceding documents");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tokenCellsSharingTheCachedTemplateReceiveTheirOwnPacking() throws Exception {
        UUID id = UUID.randomUUID();
        Class<?> keyType = Arrays.stream(Supplies.class.getDeclaredClasses()).filter(c -> c.getSimpleName().equals("Key")).findFirst().orElseThrow();
        Constructor<?> makeKey = keyType.getDeclaredConstructor(UUID.class, Vocab.class);
        makeKey.setAccessible(true);
        Object key = makeKey.newInstance(id, Vocab.WORLD);
        Field field = Supplies.class.getDeclaredField("READY");
        field.setAccessible(true);
        SupplyTask template = new SupplyTask(source(), Vocab.WORLD);
        synchronized (Supplies.class) {
            Map<Object, SupplyTask> cache = (Map<Object, SupplyTask>) field.get(null);
            cache.put(key, template);
            try {
                SupplyTask masked = Supplies.task(null, id, Vocab.WORLD, Recipe.Packing.MASKED, () -> { });
                SupplyTask windows = Supplies.task(null, id, Vocab.WORLD, () -> { });
                assertNotNull(masked);
                assertNotNull(windows);
                only(windows.batch(choose(0.5f), 1, false), 'a');
                Batch shortRows = masked.batch(choose(0.5f), 1, false);
                only(shortRows, 'b');
                assertNotNull(shortRows.input().starts);
                assertSame(template.supply(), masked.supply());
                assertSame(template.supply(), windows.supply());
                assertEquals(0, template.served());
            } finally {
                cache.remove(key);
            }
        }
    }
}
