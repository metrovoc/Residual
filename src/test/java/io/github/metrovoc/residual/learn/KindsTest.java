package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KindsTest {
    @Test
    void everyThingIsNamedTheWayAgentsAsk() {
        Random rng = new Random(1);
        for (int i = 0; i < 20000; i++) {
            Kinds.Example ex = Kinds.example(rng, i % 2 == 0);
            assertTrue(ex.name().length() <= Kinds.LONGEST, ex.toString());
            assertEquals(ex.name() + "? ", Kinds.prompt(ex.name()), "named as an agent names it: " + ex);
            Kinds.Kind known = Kinds.known(ex.name());
            assertTrue(known == null || known == ex.kind(), ex.toString());
        }
    }

    @Test
    void theTestAsksAboutThingsPracticeNeverNames() {
        Random rng = new Random(2);
        Set<String> practised = new HashSet<>();
        Set<String> tested = new HashSet<>();
        for (int i = 0; i < 40000; i++) {
            practised.add(Kinds.example(rng, false).name());
            String t = Kinds.example(rng, true).name();
            if (Kinds.known(t) != null) {
                tested.add(t);
            }
        }
        assertTrue(tested.size() > 50, "real things held out: " + tested.size());
        for (String t : tested) {
            assertFalse(practised.contains(t), t);
            // what settles it is still practised: its form with another material, or its material in another form
            String first = t.substring(0, t.indexOf(' ')), last = t.substring(t.lastIndexOf(' ') + 1);
            assertTrue(practised.stream().anyMatch(p -> !p.equals(t) && Kinds.known(p) == Kinds.known(t)
                    && (p.startsWith(first + " ") || p.endsWith(" " + last))), t);
        }
    }

    @Test
    void chestsAreLabelledInPlainWords() {
        assertEquals(Kinds.Kind.ORES, Kinds.Kind.labelled("Ores"));
        assertEquals(Kinds.Kind.ORES, Kinds.Kind.labelled("ore"));
        assertEquals(Kinds.Kind.TOOLS, Kinds.Kind.labelled("  Tools & weapons"));
        assertEquals(Kinds.Kind.FOOD, Kinds.Kind.labelled("FOOD chest"));
        assertEquals(Kinds.Kind.OTHER, Kinds.Kind.labelled("Other"));
        assertNull(Kinds.Kind.labelled("misc"));
        assertNull(Kinds.Kind.labelled(""));
        assertEquals(Kinds.Kind.STONE, Kinds.known("cobblestone"));
        assertNull(Kinds.known("tin ingot"));
    }
}
