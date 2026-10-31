package io.github.metrovoc.residual.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CraftPlanTest {
    private static final CraftPlan.Recipe<String> TORCH = new CraftPlan.Recipe<>("torch", 4, Map.of("coal", 1, "stick", 1), Map.of());
    private static final CraftPlan.Recipe<String> STICK = new CraftPlan.Recipe<>("stick", 4, Map.of("planks", 2), Map.of());
    private static final CraftPlan.Recipe<String> PLANKS = new CraftPlan.Recipe<>("planks", 4, Map.of("log", 1), Map.of());
    private static final Map<String, List<CraftPlan.Recipe<String>>> WOOD = Map.of(
            "torch", List.of(TORCH), "stick", List.of(STICK), "planks", List.of(PLANKS));

    @Test
    void theWholeTreeComesFromStorageAndCrafts() {
        CraftPlan<String> plan = CraftPlan.of("torch", 64, Map.of("coal", 16L, "log", 2L), WOOD);
        assertTrue(plan.complete());
        assertEquals(16, plan.crafts.get(TORCH));
        // 16 sticks: 4 crafts of 2 planks; 8 planks: 2 crafts of a log each
        assertEquals(4, plan.crafts.get(STICK));
        assertEquals(2, plan.crafts.get(PLANKS));
        assertEquals(22, plan.totalCrafts());
        CraftPlan.Step<String> coal = plan.root.parts.stream().filter(s -> s.kind.equals("coal")).findFirst().orElseThrow();
        assertEquals(16, coal.stored);
        assertEquals(0, coal.crafts);
    }

    @Test
    void whatNothingProvidesIsMissingAndTheRestIsStillPlanned() {
        CraftPlan<String> plan = CraftPlan.of("torch", 64, Map.of("coal", 10L, "planks", 2L), Map.of(
                "torch", List.of(TORCH), "stick", List.of(STICK)));
        assertFalse(plan.complete());
        assertEquals(Map.of("coal", 6L, "planks", 6L), plan.missing);
        assertEquals(16, plan.crafts.get(TORCH));
        assertEquals(4, plan.crafts.get(STICK));
    }

    @Test
    void whatIsAskedForIsMadeEvenWhenStorageHasSome() {
        CraftPlan<String> plan = CraftPlan.of("stick", 4, Map.of("stick", 100L, "planks", 2L), WOOD);
        assertEquals(1, plan.crafts.get(STICK));
        assertEquals(0, plan.root.stored);
    }

    @Test
    void aCraftsSurplusServesTheRestOfTheTree() {
        // two pressure plates of 2 planks each: one craft of planks (4) covers both
        CraftPlan.Recipe<String> plate = new CraftPlan.Recipe<>("plate", 1, Map.of("planks", 2), Map.of());
        CraftPlan.Recipe<String> pair = new CraftPlan.Recipe<>("pair", 1, Map.of("plate", 2), Map.of());
        CraftPlan<String> plan = CraftPlan.of("pair", 1, Map.of("log", 5L), Map.of(
                "pair", List.of(pair), "plate", List.of(plate), "planks", List.of(PLANKS)));
        assertTrue(plan.complete());
        assertEquals(1, plan.crafts.get(PLANKS));
        assertEquals(2, plan.crafts.get(plate));
    }

    @Test
    void whatACraftLeavesServesTheRestOfTheTree() {
        // a feast of a cake and three milk buckets, with three buckets in storage: the cake's buckets come back in time
        // to be filled again for the feast's own three
        CraftPlan.Recipe<String> milk = new CraftPlan.Recipe<>("milk_bucket", 1, Map.of("bucket", 1), Map.of());
        CraftPlan.Recipe<String> cake = new CraftPlan.Recipe<>("cake", 1, Map.of("milk_bucket", 3), Map.of("bucket", 3));
        Map<String, Integer> feastNeeds = new LinkedHashMap<>();
        feastNeeds.put("cake", 1);
        feastNeeds.put("milk_bucket", 3);
        CraftPlan.Recipe<String> feast = new CraftPlan.Recipe<>("feast", 1, feastNeeds, Map.of());
        CraftPlan<String> plan = CraftPlan.of("feast", 1, Map.of("bucket", 3L), Map.of(
                "feast", List.of(feast), "cake", List.of(cake), "milk_bucket", List.of(milk)));
        assertEquals(Map.of(), plan.missing);
        assertEquals(6, plan.crafts.get(milk));
    }

    @Test
    void aRecipeThatNeedsItsOwnResultStopsInsteadOfLooping() {
        CraftPlan.Recipe<String> block = new CraftPlan.Recipe<>("block", 1, Map.of("ingot", 9), Map.of());
        CraftPlan.Recipe<String> ingot = new CraftPlan.Recipe<>("ingot", 9, Map.of("block", 1), Map.of());
        CraftPlan<String> plan = CraftPlan.of("block", 1, Map.of(), Map.of("block", List.of(block), "ingot", List.of(ingot)));
        assertEquals(Map.of("block", 1L), plan.missing);
        assertFalse(plan.cut);
    }

    @Test
    void nothingToMakeItWithIsAllMissing() {
        CraftPlan<String> plan = CraftPlan.of("diamond", 5, Map.of("diamond", 9L), Map.of());
        assertEquals(Map.of("diamond", 5L), plan.missing);
        assertEquals(0, plan.totalCrafts());
    }
}
